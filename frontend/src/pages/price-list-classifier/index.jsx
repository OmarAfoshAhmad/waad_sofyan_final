import { useMemo, useState } from 'react';
import * as XLSX from 'xlsx';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import PsychologyAltIcon from '@mui/icons-material/PsychologyAlt';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import medicalDictionaryService from 'services/api/medical-dictionary.service';

const normalizeText = (value) => (value == null ? '' : String(value).trim());
const numericPattern = /^[\d\s.,]+$/;
const letterPattern = /[A-Za-z\u0600-\u06FF]/;

const parseNumber = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const text = normalizeText(value).replace(/,/g, '');
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
};

const isLikelyServiceName = (value) => {
  const text = normalizeText(value);
  if (text.length < 3) return false;
  if (!letterPattern.test(text)) return false;
  if (numericPattern.test(text)) return false;
  const lower = text.toLowerCase();
  return !['السعر', 'price', 'service', 'اسم الخدمة', 'البيان', 'الكود'].some((word) => lower === word || lower.includes(`${word}:`));
};

const extractRowsFromWorkbook = (workbook) => {
  const rows = [];

  workbook.SheetNames.forEach((sheetName) => {
    const sheetRows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { header: 1, defval: '' });
    sheetRows.forEach((row, rowIndex) => {
      const cells = (row || []).map(normalizeText);
      if (!cells.some(Boolean)) return;

      const serviceCandidates = cells
        .map((value, index) => ({ value, index }))
        .filter((cell) => isLikelyServiceName(cell.value));
      if (!serviceCandidates.length) return;

      const priceCandidates = cells
        .map((value, index) => ({ value, index, price: parseNumber(value) }))
        .filter((cell) => cell.price != null && cell.price >= 0);

      const service = serviceCandidates.reduce((best, current) => {
        if (!best) return current;
        return current.value.length > best.value.length ? current : best;
      }, null);

      const nearestPrice = priceCandidates.reduce((best, current) => {
        const distance = Math.abs(current.index - service.index);
        if (!best || distance < best.distance) return { ...current, distance };
        return best;
      }, null);

      rows.push({
        rowNumber: rowIndex + 1,
        sourceSheet: sheetName,
        serviceName: service.value,
        serviceCode: '',
        price: nearestPrice?.price ?? null
      });
    });
  });

  const seen = new Set();
  return rows.filter((row) => {
    const key = `${row.sourceSheet}-${row.rowNumber}-${row.serviceName}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const statusColor = {
  HIGH_CONFIDENCE: 'success',
  NEEDS_REVIEW: 'warning',
  UNKNOWN: 'error'
};

const exportRows = (items) => {
  const data = items.map((item) => ({
    sheet: item.sourceSheet,
    row_number: item.rowNumber,
    original_service_name: item.serviceName,
    original_price: item.price ?? '',
    classification_status: item.statusLabel,
    confidence: item.bestMatch?.confidence ?? '',
    canonical_name: item.bestMatch?.canonicalName ?? '',
    medical_category_code: item.bestMatch?.medicalCategoryCode ?? '',
    medical_category_name: item.bestMatch?.medicalCategoryName ?? '',
    matched_text: item.bestMatch?.matchedText ?? '',
    duplicate_name: item.duplicateName ? 'YES' : 'NO'
  }));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'classified_price_list');
  XLSX.writeFile(workbook, 'تصنيف_قائمة_أسعار_بالقاموس.xlsx');
};

export default function PriceListClassifierPage() {
  const [fileName, setFileName] = useState('');
  const [rawRows, setRawRows] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');

  const items = result?.items || [];
  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter((item) =>
      [item.serviceName, item.bestMatch?.canonicalName, item.bestMatch?.medicalCategoryCode, item.bestMatch?.medicalCategoryName, item.statusLabel]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q))
    );
  }, [items, search]);

  const handleFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setError('');
    setResult(null);
    setFileName(file.name);

    try {
      const buffer = await file.arrayBuffer();
      const workbook = XLSX.read(buffer, { type: 'array' });
      const rows = extractRowsFromWorkbook(workbook).slice(0, 1000);
      setRawRows(rows);
      if (!rows.length) {
        setError('لم أجد خدمات قابلة للتصنيف داخل الملف. تحقق من بنية الأعمدة أو جرّب قالباً أوضح.');
      }
    } catch (err) {
      setError('تعذر قراءة ملف Excel. تأكد من أن الملف xlsx أو xls صالح.');
    }
  };

  const classifyRows = async () => {
    if (!rawRows.length) return;
    setLoading(true);
    setError('');
    try {
      const response = await medicalDictionaryService.classifyPriceListWithDictionary({ rows: rawRows });
      setResult(response);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل تصنيف قائمة الأسعار بالقاموس');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={3}>
        <Box>
          <Typography variant="h2" sx={{ fontWeight: 900 }}>
            تنظيم قوائم الأسعار بالقاموس
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            أداة داخلية لتصنيف الخدمات وترتيبها اعتماداً على القاموس الطبي. لا تعتمد أي قرار مالي ولا تغيّر السقوف أو نسب التغطية.
          </Typography>
        </Box>

        <Alert severity="info" icon={<PsychologyAltIcon />}>
          القاموس يقترح التصنيف والاسم الموحد فقط. محرك التغطية يبقى المسؤول الوحيد عن الحسابات المالية، النسب، السقوف، الرفض، والتحمل.
        </Alert>

        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  1. رفع قائمة الأسعار
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  ارفع ملف Excel خام. سيتم استخراج أسماء الخدمات والأسعار محلياً ثم إرسال النصوص فقط للتصنيف.
                </Typography>
                <Button component="label" variant="contained" startIcon={<CloudUploadIcon />} fullWidth>
                  اختيار ملف Excel
                  <input hidden type="file" accept=".xlsx,.xls" onChange={handleFile} />
                </Button>
                {fileName && <Chip sx={{ mt: 2 }} label={fileName} variant="outlined" />}
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  2. تصنيف بالقاموس
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  تم استخراج {rawRows.length} خدمة. الحد الحالي 1000 خدمة في الطلب الواحد لحماية الأداء.
                </Typography>
                <Button
                  variant="contained"
                  color="secondary"
                  startIcon={loading ? <CircularProgress size={18} /> : <ManageSearchIcon />}
                  disabled={!rawRows.length || loading}
                  onClick={classifyRows}
                  fullWidth
                >
                  تصنيف الخدمات
                </Button>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  3. تصدير للمراجعة
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  صدّر النتائج كملف مراجعة يحتوي الاسم الأصلي، الاسم الموحد، التصنيف، والثقة.
                </Typography>
                <Button
                  variant="outlined"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.length}
                  onClick={() => exportRows(items)}
                  fullWidth
                >
                  تصدير Excel
                </Button>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {loading && <LinearProgress />}
        {error && <Alert severity="error">{error}</Alert>}

        {result && (
          <Card>
            <CardContent>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }} justifyContent="space-between">
                <Box>
                  <Typography variant="h4" sx={{ fontWeight: 900 }}>
                    نتيجة التصنيف
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    هذه النتائج للمراجعة والتنظيم فقط، وليست اعتماداً مالياً.
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip label={`الإجمالي ${result.summary?.total || 0}`} />
                  <Chip color="success" label={`ثقة عالية ${result.summary?.highConfidence || 0}`} />
                  <Chip color="warning" label={`تحتاج مراجعة ${result.summary?.needsReview || 0}`} />
                  <Chip color="error" label={`غير معروف ${result.summary?.unknown || 0}`} />
                  <Chip color="info" label={`مكرر ${result.summary?.duplicateNames || 0}`} />
                </Stack>
              </Stack>

              <Divider sx={{ my: 2 }} />

              <TextField
                fullWidth
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="بحث داخل النتائج: اسم الخدمة، التصنيف، الكود، الحالة..."
                sx={{ mb: 2 }}
              />

              <TableContainer sx={{ maxHeight: 620 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>#</TableCell>
                      <TableCell>الخدمة الأصلية</TableCell>
                      <TableCell>السعر</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>الثقة</TableCell>
                      <TableCell>الاسم الموحد</TableCell>
                      <TableCell>التصنيف</TableCell>
                      <TableCell>ملاحظات</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredItems.map((item, index) => (
                      <TableRow key={`${item.sourceSheet}-${item.rowNumber}-${index}`} hover>
                        <TableCell>{item.rowNumber}</TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography sx={{ fontWeight: 700 }}>{item.serviceName}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {item.sourceSheet}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>{item.price ?? '-'}</TableCell>
                        <TableCell>
                          <Chip size="small" color={statusColor[item.status] || 'default'} label={item.statusLabel} />
                        </TableCell>
                        <TableCell>{item.bestMatch?.confidence ?? '-'}</TableCell>
                        <TableCell>{item.bestMatch?.canonicalName ?? '-'}</TableCell>
                        <TableCell>
                          {item.bestMatch ? (
                            <Stack spacing={0.25}>
                              <Typography>{item.bestMatch.medicalCategoryName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {item.bestMatch.medicalCategoryCode}
                              </Typography>
                            </Stack>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                        <TableCell>{item.duplicateName ? <Chip size="small" color="info" label="اسم مكرر" /> : '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}
      </Stack>
    </Box>
  );
}
