import { useEffect, useMemo, useState } from 'react';
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
  MenuItem,
  Select,
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
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck';
import LibraryAddCheckIcon from '@mui/icons-material/LibraryAddCheck';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';

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

const CLASSIFICATION_BATCH_SIZE = 500;
const CLASSIFICATION_SESSION_KEY = 'waad.priceListClassifier.session.v1';

const rowKey = (item) => `${item.sourceSheet || '-'}-${item.rowNumber || '-'}-${item.serviceName || '-'}`;

const getEffectiveCategory = (item) => item.manualCategory || item.bestMatch;

const getEffectiveStatusLabel = (item) => (item.manualCategory ? 'مراجع يدوياً' : item.statusLabel);

const buildSummary = (items) => ({
  total: items.length,
  highConfidence: items.filter((item) => item.status === 'HIGH_CONFIDENCE').length,
  needsReview: items.filter((item) => item.status === 'NEEDS_REVIEW').length,
  unknown: items.filter((item) => item.status === 'UNKNOWN').length,
  duplicateNames: items.filter((item) => item.duplicateName).length
});

const saveClassificationSession = (session) => {
  try {
    localStorage.setItem(CLASSIFICATION_SESSION_KEY, JSON.stringify({ ...session, savedAt: new Date().toISOString() }));
  } catch (err) {
    // Local persistence is a convenience; classification must continue even if storage quota is full.
  }
};

const loadClassificationSession = () => {
  try {
    const raw = localStorage.getItem(CLASSIFICATION_SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
};

const clearClassificationSession = () => {
  try {
    localStorage.removeItem(CLASSIFICATION_SESSION_KEY);
  } catch (err) {
    // ignore
  }
};

const exportRows = (items) => {
  const data = items.map((item) => ({
    sheet: item.sourceSheet,
    row_number: item.rowNumber,
    original_service_name: item.serviceName,
    original_price: item.price ?? '',
    classification_status: getEffectiveStatusLabel(item),
    confidence: item.bestMatch?.confidence ?? '',
    canonical_name: item.bestMatch?.canonicalName ?? '',
    medical_category_code: getEffectiveCategory(item)?.medicalCategoryCode ?? '',
    medical_category_name: getEffectiveCategory(item)?.medicalCategoryName ?? '',
    matched_text: item.bestMatch?.matchedText ?? '',
    manual_override: item.manualCategory ? 'YES' : 'NO',
    duplicate_name: item.duplicateName ? 'YES' : 'NO'
  }));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'classified_price_list');
  XLSX.writeFile(workbook, 'تصنيف_قائمة_أسعار_بالقاموس.xlsx');
};

const appendCategoriesLookupSheet = (workbook, categories = []) => {
  const lookup = categories.map((category) => ({
    medical_category_id: category.id,
    medical_category_code: category.code || '',
    medical_category_name: category.nameAr || category.name || '',
    medical_category_name_en: category.nameEn || ''
  }));
  const worksheet = XLSX.utils.json_to_sheet(lookup);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'التصنيفات المتاحة');
};

const exportProviderContractReadyRows = (items, categories = []) => {
  const acceptedItems = items.filter((item) => getEffectiveCategory(item)?.medicalCategoryId);
  const data = acceptedItems.map((item) => ({
    service_name: item.bestMatch?.canonicalName || item.serviceName,
    service_code: item.serviceCode || '',
    contract_price: item.price ?? '',
    medical_category_code: getEffectiveCategory(item)?.medicalCategoryCode || '',
    medical_category_name: getEffectiveCategory(item)?.medicalCategoryName || '',
    notes: [
      item.serviceName && item.bestMatch?.canonicalName && item.serviceName !== item.bestMatch.canonicalName ? `الأصل: ${item.serviceName}` : '',
      `الثقة: ${item.bestMatch?.confidence ?? '-'}`,
      `الحالة: ${getEffectiveStatusLabel(item)}`,
      `المصدر: ${item.sourceSheet || '-'} صف ${item.rowNumber || '-'}`
    ]
      .filter(Boolean)
      .join(' | ')
  }));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Pricing_Template');
  appendCategoriesLookupSheet(workbook, categories);
  XLSX.writeFile(workbook, 'قائمة_أسعار_جاهزة_مبدئياً_لعقد_مقدم_خدمة.xlsx');
};

const downloadTemplate = (categories = []) => {
  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet([
    {
      service_name: 'مثال: تحليل CBC',
      service_code: 'SRV-001',
      contract_price: 25,
      medical_category_code: 'CAT-LAB',
      medical_category_name: 'التحاليل الطبية والمختبرات',
      notes: 'اختياري'
    },
    {
      service_name: 'مثال: رنين مغناطيسي',
      service_code: 'SRV-002',
      contract_price: 900,
      medical_category_code: 'CAT-IMG-ADV',
      medical_category_name: 'التصوير بالرنين المغناطيسي والمقطعي والطبقي',
      notes: 'اختياري'
    }
  ]);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Pricing_Template');
  appendCategoriesLookupSheet(workbook, categories);
  XLSX.writeFile(workbook, 'قالب_تنظيم_قائمة_الأسعار.xlsx');
};

export default function PriceListClassifierPage() {
  const [fileName, setFileName] = useState('');
  const [rawRows, setRawRows] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [classificationProgress, setClassificationProgress] = useState(null);
  const [promoting, setPromoting] = useState(false);
  const [approvingSynonyms, setApprovingSynonyms] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [categories, setCategories] = useState([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [sessionInfo, setSessionInfo] = useState(null);

  const items = result?.items || [];
  const manualReviewedCount = items.filter((item) => item.manualCategory).length;
  const synonymReadyCount = items.filter((item) => item.bestMatch?.entryId && item.serviceName && item.serviceName !== item.bestMatch?.canonicalName).length;
  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((item) => {
      if (statusFilter !== 'ALL' && item.status !== statusFilter) return false;
      if (!q) return true;
      return [item.serviceName, item.bestMatch?.canonicalName, item.bestMatch?.medicalCategoryCode, item.bestMatch?.medicalCategoryName, item.statusLabel]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q));
    });
  }, [items, search, statusFilter]);

  useEffect(() => {
    let mounted = true;
    setCategoriesLoading(true);
    getAllMedicalCategories()
      .then((data) => {
        if (!mounted) return;
        setCategories(Array.isArray(data) ? data.filter((category) => category.active !== false && category.deleted !== true) : []);
      })
      .catch(() => {
        if (mounted) setError('تعذر تحميل التصنيفات الطبية للتعديل اليدوي');
      })
      .finally(() => {
        if (mounted) setCategoriesLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const session = loadClassificationSession();
    if (!session?.rawRows?.length) return;

    setSessionInfo(session);
    setFileName(session.fileName || '');
    setRawRows(session.rawRows || []);
    setResult({
      summary: buildSummary(session.items || []),
      items: session.items || []
    });
    setClassificationProgress({
      done: session.done || 0,
      total: session.total || session.rawRows.length
    });
    if (session.status === 'COMPLETED') {
      setSuccess(`تم استرجاع آخر نتيجة محفوظة: ${session.done || 0} خدمة مصنفة.`);
    } else if ((session.done || 0) > 0) {
      setSuccess(`تم استرجاع جلسة تصنيف غير مكتملة: ${session.done} من ${session.total || session.rawRows.length}. يمكنك المتابعة من نفس النقطة.`);
    }
  }, []);

  const applyManualCategory = (item, categoryId) => {
    const category = categories.find((entry) => String(entry.id) === String(categoryId));
    setResult((previous) => {
      if (!previous) return previous;
      const nextItems = previous.items.map((row) => {
        if (rowKey(row) !== rowKey(item)) return row;
        if (!category) {
          const { manualCategory, ...rest } = row;
          return rest;
        }
        return {
          ...row,
          manualCategory: {
            medicalCategoryId: category.id,
            medicalCategoryCode: category.code,
            medicalCategoryName: category.name
          }
        };
      });
      return {
        ...previous,
        items: nextItems
      };
    });
  };

  const handleFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    clearClassificationSession();
    setError('');
    setSuccess('');
    setSessionInfo(null);
    setClassificationProgress(null);
    setResult(null);
    setFileName(file.name);

    try {
      const buffer = await file.arrayBuffer();
      const workbook = XLSX.read(buffer, { type: 'array' });
      const rows = extractRowsFromWorkbook(workbook);
      setRawRows(rows);
      saveClassificationSession({
        status: 'READY',
        fileName: file.name,
        rawRows: rows,
        items: [],
        done: 0,
        total: rows.length,
        batchSize: CLASSIFICATION_BATCH_SIZE
      });
      setSessionInfo(loadClassificationSession());
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
    setSuccess('');
    try {
      const persisted = loadClassificationSession();
      const shouldResume = persisted?.rawRows?.length === rawRows.length && (persisted.done || 0) < rawRows.length;
      const allItems = shouldResume ? [...(persisted.items || [])] : [];
      let startIndex = shouldResume ? persisted.done || 0 : 0;

      setClassificationProgress({ done: startIndex, total: rawRows.length });
      saveClassificationSession({
        status: 'RUNNING',
        fileName,
        rawRows,
        items: allItems,
        done: startIndex,
        total: rawRows.length,
        batchSize: CLASSIFICATION_BATCH_SIZE
      });

      for (let index = startIndex; index < rawRows.length; index += CLASSIFICATION_BATCH_SIZE) {
        const chunk = rawRows.slice(index, index + CLASSIFICATION_BATCH_SIZE);
        const response = await medicalDictionaryService.classifyPriceListWithDictionary({ rows: chunk });
        allItems.push(...(response.items || []));
        startIndex = Math.min(index + chunk.length, rawRows.length);

        const nextResult = {
          summary: buildSummary(allItems),
          items: allItems
        };
        setResult(nextResult);
        setClassificationProgress({ done: startIndex, total: rawRows.length });

        saveClassificationSession({
          status: startIndex >= rawRows.length ? 'COMPLETED' : 'RUNNING',
          fileName,
          rawRows,
          items: allItems,
          done: startIndex,
          total: rawRows.length,
          batchSize: CLASSIFICATION_BATCH_SIZE
        });
        setSessionInfo(loadClassificationSession());

        await new Promise((resolve) => setTimeout(resolve, 0));
      }

      setSuccess(`اكتمل التصنيف: ${allItems.length} خدمة. تم حفظ النتائج ويمكن الرجوع لها لاحقاً.`);
    } catch (err) {
      const persisted = loadClassificationSession();
      if (persisted) {
        setSessionInfo(persisted);
        setClassificationProgress({ done: persisted.done || 0, total: persisted.total || rawRows.length });
      }
      setError(err?.response?.data?.message || 'فشل تصنيف قائمة الأسعار بالقاموس');
    } finally {
      setLoading(false);
    }
  };

  const clearCurrentClassificationSession = () => {
    clearClassificationSession();
    setSessionInfo(null);
    setClassificationProgress(null);
    setResult(null);
    setRawRows([]);
    setFileName('');
    setSuccess('تم مسح جلسة التصنيف المحفوظة.');
  };

  const promoteReviewRowsToDictionarySuggestions = async () => {
    const reviewRows = items.filter((item) => item.status !== 'HIGH_CONFIDENCE');
    if (!reviewRows.length) {
      setSuccess('لا توجد خدمات تحتاج ترحيل للاقتراحات.');
      return;
    }

    setPromoting(true);
    setError('');
    setSuccess('');
    try {
      let created = 0;
      for (const item of reviewRows) {
        const effectiveCategory = getEffectiveCategory(item);
        await medicalDictionaryService.createDictionarySuggestion({
          originalText: item.serviceName,
          suggestedEntryId: item.bestMatch?.entryId || null,
          suggestedCategoryId: effectiveCategory?.medicalCategoryId || null,
          source: 'PRICE_LIST_IMPORT',
          confidence: item.manualCategory ? 90 : item.bestMatch?.confidence || 0,
          sourceReference: `${fileName || 'price-list'} | ${item.sourceSheet || '-'} | row ${item.rowNumber || '-'}`
        });
        created += 1;
      }
      setSuccess(`تم ترحيل ${created} خدمة إلى اقتراحات القاموس للمراجعة، بدون اعتماد تلقائي.`);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل ترحيل الخدمات إلى اقتراحات القاموس');
    } finally {
      setPromoting(false);
    }
  };

  const approveMatchedRowsAsSynonyms = async () => {
    const synonymRows = items.filter((item) => item.bestMatch?.entryId && item.serviceName && item.serviceName !== item.bestMatch?.canonicalName);
    if (!synonymRows.length) {
      setSuccess('لا توجد صفوف مرتبطة باسم موحد يمكن اعتمادها كمرادفات.');
      return;
    }

    setApprovingSynonyms(true);
    setError('');
    setSuccess('');
    let approved = 0;
    let skipped = 0;
    try {
      for (const item of synonymRows) {
        try {
          await medicalDictionaryService.addDictionarySynonym(item.bestMatch.entryId, {
            synonym: item.serviceName,
            synonymType: 'PROVIDER_SPECIFIC',
            language: /[A-Za-z]/.test(item.serviceName) ? 'en' : 'ar'
          });
          approved += 1;
        } catch (err) {
          skipped += 1;
        }
      }
      setSuccess(`تم اعتماد ${approved} مرادف للقاموس. تم تخطي ${skipped} صف غالباً لأنها مرادفات موجودة مسبقاً.`);
    } catch (err) {
      setError('فشل اعتماد المرادفات من نتائج قائمة الأسعار');
    } finally {
      setApprovingSynonyms(false);
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

        {sessionInfo?.rawRows?.length > 0 && (
          <Alert
            severity={sessionInfo.status === 'COMPLETED' ? 'success' : 'warning'}
            action={
              <Button color="inherit" size="small" onClick={clearCurrentClassificationSession}>
                مسح الجلسة
              </Button>
            }
          >
            جلسة محفوظة: {sessionInfo.done || 0} من {sessionInfo.total || sessionInfo.rawRows.length} خدمة
            {sessionInfo.savedAt ? ` — آخر حفظ ${new Date(sessionInfo.savedAt).toLocaleString('ar-LY')}` : ''}
          </Alert>
        )}

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
                <Button sx={{ mt: 1 }} variant="outlined" startIcon={<FileDownloadIcon />} onClick={() => downloadTemplate(categories)} fullWidth>
                  تحميل قالب قياسي
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
                  تم استخراج {rawRows.length} خدمة. سيتم التصنيف على دفعات من {CLASSIFICATION_BATCH_SIZE} خدمة لحماية الأداء.
                </Typography>
                <Button
                  variant="contained"
                  color="secondary"
                  startIcon={loading ? <CircularProgress size={18} /> : <ManageSearchIcon />}
                  disabled={!rawRows.length || loading}
                  onClick={classifyRows}
                  fullWidth
                >
                  {(classificationProgress?.done || 0) > 0 && (classificationProgress?.done || 0) < rawRows.length ? 'متابعة التصنيف' : 'تصنيف الخدمات'}
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
                <Button
                  sx={{ mt: 1 }}
                  variant="contained"
                  color="success"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.some((item) => item.bestMatch?.medicalCategoryId)}
                  onClick={() => exportProviderContractReadyRows(items, categories)}
                  fullWidth
                >
                  تصدير للعقود
                </Button>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {loading && (
          <LinearProgress
            variant={classificationProgress?.total ? 'determinate' : 'indeterminate'}
            value={
              classificationProgress?.total ? Math.min(100, Math.round(((classificationProgress.done || 0) / classificationProgress.total) * 100)) : undefined
            }
          />
        )}
        {classificationProgress && (
          <Typography variant="body2" color="text.secondary">
            التقدم الحقيقي: {classificationProgress.done} من {classificationProgress.total}
            {classificationProgress.total ? ` (${Math.round(((classificationProgress.done || 0) / classificationProgress.total) * 100)}%)` : ''}
          </Typography>
        )}
        {error && <Alert severity="error">{error}</Alert>}
        {success && <Alert severity="success">{success}</Alert>}

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
                  <Chip color="secondary" label={`مراجع يدوياً ${manualReviewedCount}`} />
                  <Chip color="primary" label={`جاهز كمرادف ${synonymReadyCount}`} />
                </Stack>
              </Stack>

              <Divider sx={{ my: 2 }} />

              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
                <TextField
                  fullWidth
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="بحث داخل النتائج: اسم الخدمة، التصنيف، الكود، الحالة..."
                />
                <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} sx={{ minWidth: 190 }}>
                  <MenuItem value="ALL">كل النتائج</MenuItem>
                  <MenuItem value="HIGH_CONFIDENCE">ثقة عالية</MenuItem>
                  <MenuItem value="NEEDS_REVIEW">تحتاج مراجعة</MenuItem>
                  <MenuItem value="UNKNOWN">غير معروف</MenuItem>
                </Select>
                <Button variant="outlined" startIcon={<FileDownloadIcon />} onClick={() => downloadTemplate(categories)}>
                  قالب Excel
                </Button>
                <Button
                  variant="contained"
                  color="success"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.some((item) => item.bestMatch?.medicalCategoryId)}
                  onClick={() => exportProviderContractReadyRows(items, categories)}
                >
                  تصدير للعقود
                </Button>
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={approvingSynonyms ? <CircularProgress size={18} /> : <LibraryAddCheckIcon />}
                  disabled={!synonymReadyCount || approvingSynonyms}
                  onClick={approveMatchedRowsAsSynonyms}
                >
                  اعتماد كمرادفات
                </Button>
                <Button
                  variant="contained"
                  color="warning"
                  startIcon={promoting ? <CircularProgress size={18} /> : <PlaylistAddCheckIcon />}
                  disabled={!items.some((item) => item.status !== 'HIGH_CONFIDENCE') || promoting}
                  onClick={promoteReviewRowsToDictionarySuggestions}
                >
                  ترحيل للمراجعة
                </Button>
              </Stack>

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
                      <TableCell>تعديل التصنيف</TableCell>
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
                          <Chip
                            size="small"
                            color={item.manualCategory ? 'secondary' : statusColor[item.status] || 'default'}
                            label={getEffectiveStatusLabel(item)}
                          />
                        </TableCell>
                        <TableCell>{item.bestMatch?.confidence ?? '-'}</TableCell>
                        <TableCell>{item.bestMatch?.canonicalName ?? '-'}</TableCell>
                        <TableCell>
                          {getEffectiveCategory(item) ? (
                            <Stack spacing={0.25}>
                              <Typography>{getEffectiveCategory(item).medicalCategoryName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {getEffectiveCategory(item).medicalCategoryCode}
                              </Typography>
                            </Stack>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                        <TableCell sx={{ minWidth: 240 }}>
                          <Select
                            size="small"
                            fullWidth
                            displayEmpty
                            value={item.manualCategory?.medicalCategoryId || ''}
                            disabled={categoriesLoading}
                            onChange={(event) => applyManualCategory(item, event.target.value)}
                          >
                            <MenuItem value="">بدون تعديل يدوي</MenuItem>
                            {categories.map((category) => (
                              <MenuItem key={category.id} value={category.id}>
                                {category.name} ({category.code})
                              </MenuItem>
                            ))}
                          </Select>
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
