/**
 * BulkPriceListImportDialog
 * ═══════════════════════════════════════════════════════════════════════════
 * Dialog for uploading the classified multi-provider price-list Excel file.
 * Calls POST /api/v1/provider-contracts/bulk-import and shows per-provider results.
 */

import { useRef, useState, useCallback } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import CloseIcon from '@mui/icons-material/Close';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import AutorenewIcon from '@mui/icons-material/Autorenew';

import { bulkImportPriceList } from 'services/api/provider-contracts.service';
import { useSnackbar } from 'notistack';

// ─────────────────────────────────────────────────────────────────────────────
// STATUS CHIP
// ─────────────────────────────────────────────────────────────────────────────
const StatusChip = ({ status }) => {
  const map = {
    SUCCESS: { label: 'ناجح', color: 'success', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
    PARTIAL: { label: 'جزئي', color: 'warning', icon: <WarningIcon sx={{ fontSize: 14 }} /> },
    FAILED:  { label: 'فشل',  color: 'error',   icon: <ErrorIcon sx={{ fontSize: 14 }} /> }
  };
  const cfg = map[status] || { label: status, color: 'default', icon: null };
  return (
    <Chip
      size="small"
      label={cfg.label}
      color={cfg.color}
      icon={cfg.icon}
      sx={{ fontWeight: 600, fontSize: '0.72rem' }}
    />
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// PROVIDER ROW (collapsible errors)
// ─────────────────────────────────────────────────────────────────────────────
const ProviderRow = ({ row }) => {
  const [open, setOpen] = useState(false);
  const hasErrors = row.errors && row.errors.length > 0;

  return (
    <>
      <TableRow hover>
        <TableCell sx={{ fontWeight: 600, fontSize: '0.82rem' }}>
          {row.providerName}
          {row.providerCreated && (
            <Chip size="small" label="جديد" color="info" sx={{ ml: 0.5, fontSize: '0.68rem' }} />
          )}
          {row.contractCreated && (
            <Chip size="small" label="عقد جديد" color="secondary" sx={{ ml: 0.5, fontSize: '0.68rem' }} />
          )}
          {row.contractActivated && (
            <Chip size="small" label="تم التفعيل" color="success" sx={{ ml: 0.5, fontSize: '0.68rem' }} />
          )}
        </TableCell>
        <TableCell align="center"><StatusChip status={row.status} /></TableCell>
        <TableCell align="center">
          <Typography variant="body2" color="success.main" fontWeight={700}>{row.created}</Typography>
        </TableCell>
        <TableCell align="center">
          <Typography variant="body2" color="info.main" fontWeight={700}>{row.updated}</Typography>
        </TableCell>
        <TableCell align="center">
          <Typography variant="body2" color="error.main" fontWeight={700}>{row.failed}</Typography>
        </TableCell>
        <TableCell align="center">
          {hasErrors ? (
            <Tooltip title={open ? 'إخفاء الأخطاء' : 'عرض الأخطاء'}>
              <IconButton size="small" onClick={() => setOpen((v) => !v)}>
                {open ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          ) : (
            <Typography variant="caption" color="text.secondary">—</Typography>
          )}
        </TableCell>
      </TableRow>

      {hasErrors && (
        <TableRow>
          <TableCell colSpan={6} sx={{ p: 0, borderBottom: 0 }}>
            <Collapse in={open} unmountOnExit>
              <Box sx={{ bgcolor: 'error.lighter', px: 3, py: 1.5 }}>
                {row.errors.map((err, i) => (
                  <Typography key={i} variant="caption" display="block" color="error.dark" sx={{ mb: 0.3 }}>
                    • {err}
                  </Typography>
                ))}
              </Box>
            </Collapse>
          </TableCell>
        </TableRow>
      )}
    </>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// MAIN DIALOG
// ─────────────────────────────────────────────────────────────────────────────
const BulkPriceListImportDialog = ({ open, onClose, onImportComplete }) => {
  const { enqueueSnackbar } = useSnackbar();
  const fileInputRef = useRef(null);

  const [selectedFile, setSelectedFile] = useState(null);
  const [loading, setLoading]     = useState(false);
  const [uploadPct, setUploadPct] = useState(0);
  const [result, setResult]       = useState(null);
  const [error, setError]         = useState(null);

  const handleClose = useCallback(() => {
    if (loading) return;
    setSelectedFile(null);
    setResult(null);
    setError(null);
    setUploadPct(0);
    onClose?.();
  }, [loading, onClose]);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.match(/\.(xlsx|xls)$/i)) {
      setError('يرجى اختيار ملف Excel بصيغة .xlsx أو .xls');
      return;
    }
    setSelectedFile(file);
    setResult(null);
    setError(null);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (!file) return;
    if (!file.name.match(/\.(xlsx|xls)$/i)) {
      setError('يرجى اختيار ملف Excel بصيغة .xlsx أو .xls');
      return;
    }
    setSelectedFile(file);
    setResult(null);
    setError(null);
  };

  const handleImport = async () => {
    if (!selectedFile) return;
    setLoading(true);
    setError(null);
    setResult(null);
    setUploadPct(0);

    try {
      const data = await bulkImportPriceList(selectedFile, (evt) => {
        if (evt.total) setUploadPct(Math.round((evt.loaded / evt.total) * 100));
      });

      setResult(data);
      const created = data.totalCreated ?? 0;
      const updated = data.totalUpdated ?? 0;
      enqueueSnackbar(
        `تم الاستيراد: ${created} خدمة جديدة، ${updated} محدَّثة`,
        { variant: 'success', autoHideDuration: 6000 }
      );
      onImportComplete?.();
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.messageAr ||
        err?.message ||
        'فشل الاستيراد';
      setError(msg);
      enqueueSnackbar(msg, { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  // ── Totals summary ──────────────────────────────────────────────────────────
  const summary = result
    ? [
        { label: 'المرافق', value: result.totalProviders, color: 'text.primary' },
        { label: 'جديد',    value: result.providersCreated, color: 'info.main' },
        { label: 'موجود',   value: result.providersMatched, color: 'text.secondary' },
        { label: '+ خدمات', value: result.totalCreated, color: 'success.main' },
        { label: '↻ محدَّث', value: result.totalUpdated, color: 'primary.main' },
        { label: '✗ فشل',   value: result.totalFailed, color: result.totalFailed > 0 ? 'error.main' : 'text.disabled' }
      ]
    : [];

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      PaperProps={{ sx: { borderRadius: 3 } }}
    >
      {/* ── Header ── */}
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          background: 'linear-gradient(135deg, #1565c0 0%, #0d47a1 100%)',
          color: 'white',
          borderRadius: '12px 12px 0 0'
        }}
      >
        <FileUploadIcon />
        <Box flex={1}>
          <Typography variant="h6" fontWeight={700}>استيراد قوائم الأسعار الجماعي</Typography>
          <Typography variant="caption" sx={{ opacity: 0.85 }}>
            ملف نتيجة_تصنيف_قوائم_الاسعار_بالقاموس_الموحد.xlsx
          </Typography>
        </Box>
        <IconButton size="small" sx={{ color: 'white' }} onClick={handleClose} disabled={loading}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pt: 3 }}>
        {/* ── Upload zone ── */}
        {!result && (
          <>
            <Box
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              sx={{
                border: '2px dashed',
                borderColor: selectedFile ? 'success.main' : 'primary.light',
                borderRadius: 2,
                p: 4,
                textAlign: 'center',
                cursor: 'pointer',
                bgcolor: selectedFile ? 'success.lighter' : 'action.hover',
                transition: 'all .2s',
                '&:hover': { borderColor: 'primary.main', bgcolor: 'primary.lighter' }
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                style={{ display: 'none' }}
                onChange={handleFileChange}
              />
              <CloudUploadIcon sx={{ fontSize: 52, color: selectedFile ? 'success.main' : 'primary.light', mb: 1 }} />
              {selectedFile ? (
                <>
                  <Typography variant="body1" fontWeight={700} color="success.main">
                    {selectedFile.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {(selectedFile.size / 1024 / 1024).toFixed(1)} MB — انقر لتغيير الملف
                  </Typography>
                </>
              ) : (
                <>
                  <Typography variant="body1" fontWeight={600} color="primary.main">
                    اسحب ملف Excel هنا أو انقر للاختيار
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    يقبل ملفات .xlsx و .xls فقط
                  </Typography>
                </>
              )}
            </Box>

            {error && (
              <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            {loading && (
              <Box sx={{ mt: 2 }}>
                <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                  <AutorenewIcon fontSize="small" color="primary" sx={{ animation: 'spin 1.2s linear infinite', '@keyframes spin': { from: { transform: 'rotate(0deg)' }, to: { transform: 'rotate(360deg)' } } }} />
                  <Typography variant="body2" color="primary">
                    {uploadPct < 100
                      ? `جارٍ رفع الملف... ${uploadPct}%`
                      : 'جارٍ معالجة البيانات، قد يستغرق دقيقتين...'}
                  </Typography>
                </Stack>
                <LinearProgress variant={uploadPct < 100 ? 'determinate' : 'indeterminate'} value={uploadPct} sx={{ borderRadius: 4 }} />
              </Box>
            )}

            {/* Info note */}
            <Alert severity="info" sx={{ mt: 2 }}>
              <Typography variant="body2">
                <strong>ملاحظة:</strong> سيتم قراءة عمود <strong>"المرفق"</strong> تلقائياً لتوزيع الخدمات على مقدمي الخدمة.
                المرافق غير الموجودة سيتم إنشاؤها تلقائياً. الخدمات المكررة يُحدَّث سعرها فقط.
              </Typography>
            </Alert>
          </>
        )}

        {/* ── Results ── */}
        {result && (
          <>
            {/* Summary cards */}
            <Stack direction="row" spacing={1.5} flexWrap="wrap" mb={2.5} useFlexGap>
              {summary.map((s) => (
                <Box
                  key={s.label}
                  sx={{
                    px: 2, py: 1,
                    borderRadius: 2,
                    bgcolor: 'background.paper',
                    border: '1px solid',
                    borderColor: 'divider',
                    minWidth: 90,
                    textAlign: 'center'
                  }}
                >
                  <Typography variant="h5" fontWeight={800} color={s.color}>{s.value}</Typography>
                  <Typography variant="caption" color="text.secondary">{s.label}</Typography>
                </Box>
              ))}
            </Stack>

            <Alert severity={result.totalFailed === 0 ? 'success' : 'warning'} sx={{ mb: 2 }}>
              {result.summaryAr}
            </Alert>

            <Divider sx={{ mb: 1.5 }}>
              <Typography variant="caption" color="text.secondary">تفاصيل المرافق</Typography>
            </Divider>

            <Box sx={{ maxHeight: 340, overflowY: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>المرفق</TableCell>
                    <TableCell align="center">الحالة</TableCell>
                    <TableCell align="center">مضاف</TableCell>
                    <TableCell align="center">محدَّث</TableCell>
                    <TableCell align="center">فشل</TableCell>
                    <TableCell align="center">أخطاء</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(result.providerResults || []).map((row, i) => (
                    <ProviderRow key={i} row={row} />
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        {result ? (
          <>
            <Button variant="outlined" onClick={handleClose}>إغلاق</Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<FileUploadIcon />}
              onClick={() => {
                setResult(null);
                setSelectedFile(null);
                setError(null);
              }}
            >
              استيراد ملف آخر
            </Button>
          </>
        ) : (
          <>
            <Button variant="outlined" onClick={handleClose} disabled={loading}>إلغاء</Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <FileUploadIcon />}
              onClick={handleImport}
              disabled={!selectedFile || loading}
            >
              {loading ? 'جارٍ الاستيراد...' : 'بدء الاستيراد'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default BulkPriceListImportDialog;
