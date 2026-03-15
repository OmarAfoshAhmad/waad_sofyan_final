import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Alert,
  AlertTitle,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  LinearProgress,
  IconButton,
  Collapse
} from '@mui/material';
import { CloudUpload, Download, Close, ExpandMore, CheckCircle, Error, Warning } from '@mui/icons-material';
import { downloadBlob } from 'services/api/excel-import.service';

/**
 * Reusable Excel Import Dialog Component
 *
 * @param {Object} props
 * @param {boolean} props.open - Dialog open state
 * @param {Function} props.onClose - Close handler
 * @param {string} props.title - Dialog title (e.g., "استيراد الأعضاء")
 * @param {Function} props.onDownloadTemplate - Template download function
 * @param {Function} props.onImport - Import function (receives file)
 * @param {string} props.templateFilename - Template filename (e.g., "Members_Template.xlsx")
 */
const ExcelImportDialog = ({ open, onClose, title, onDownloadTemplate, onImport, templateFilename }) => {
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [showErrors, setShowErrors] = useState(false);

  const handleDownloadTemplate = async () => {
    try {
      setLoading(true);
      setError(null);
      const blob = await onDownloadTemplate();
      downloadBlob(blob, templateFilename);
    } catch (err) {
      setError(err.message || 'فشل تنزيل القالب');
    } finally {
      setLoading(false);
    }
  };

  const handleFileChange = (event) => {
    const selectedFile = event.target.files[0];
    if (selectedFile) {
      // Validate file type
      if (!selectedFile.name.endsWith('.xlsx') && !selectedFile.name.endsWith('.xls')) {
        setError('يجب أن يكون الملف من نوع Excel (.xlsx أو .xls)');
        return;
      }
      setFile(selectedFile);
      setError(null);
      setResult(null);
    }
  };

  const handleImport = async () => {
    if (!file) {
      setError('الرجاء اختيار ملف');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const importResult = await onImport(file);
      setResult(importResult);

      // Auto-expand errors if any
      if (importResult.errors && importResult.errors.length > 0) {
        setShowErrors(true);
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'فشل الاستيراد');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setFile(null);
    setResult(null);
    setError(null);
    setShowErrors(false);
  };

  const handleCloseDialog = () => {
    handleReset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleCloseDialog} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{title}</Typography>
          <IconButton onClick={handleCloseDialog} size="small">
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {/* Warning Alert */}
        <Alert severity="warning" sx={{ mb: '1.5rem' }}>
          <AlertTitle>⚠️ تحذير مهم</AlertTitle>
          <Typography variant="body2">
            يتم قبول الملفات المُنزَّلة من هذا النظام فقط.
            <br />
            Only files downloaded from this system are accepted.
          </Typography>
        </Alert>

        {/* Step 1: Download Template */}
        <Box sx={{ mb: '1.5rem' }}>
          <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
            الخطوة 1: تنزيل القالب
          </Typography>
          <Button variant="outlined" startIcon={<Download />} onClick={handleDownloadTemplate} disabled={loading} fullWidth>
            تنزيل قالب Excel
          </Button>
        </Box>

        {/* Step 2: Upload File */}
        <Box sx={{ mb: '1.5rem' }}>
          <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
            الخطوة 2: رفع الملف المُعبّأ
          </Typography>
          <Box
            sx={{
              border: '2px dashed',
              borderColor: file ? 'success.main' : 'divider',
              borderRadius: '0.25rem',
              p: '1.5rem',
              textAlign: 'center',
              cursor: 'pointer',
              '&:hover': {
                borderColor: 'primary.main',
                bgcolor: 'action.hover'
              }
            }}
            onClick={() => document.getElementById('excel-file-input').click()}
          >
            <input id="excel-file-input" type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleFileChange} />
            <CloudUpload sx={{ fontSize: '3.0rem', color: 'text.secondary', mb: 1 }} />
            <Typography variant="body1">{file ? file.name : 'انقر لاختيار الملف'}</Typography>
            <Typography variant="caption" color="text.secondary">
              Excel files only (.xlsx, .xls)
            </Typography>
          </Box>
        </Box>

        {/* Loading */}
        {loading && (
          <Box sx={{ mb: '1.5rem' }}>
            <LinearProgress />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              جاري المعالجة...
            </Typography>
          </Box>
        )}

        {/* Error Message */}
        {error && (
          <Alert severity="error" sx={{ mb: '1.5rem' }}>
            {error}
          </Alert>
        )}

        {/* Results */}
        {result && (
          <Box sx={{ mb: '1.5rem' }}>
            {/* Summary */}
            <Alert severity={result.success ? 'success' : 'error'} icon={result.success ? <CheckCircle /> : <Error />} sx={{ mb: '1.0rem' }}>
              <AlertTitle>{result.message || (result.success ? 'تم الاستيراد بنجاح' : 'فشل الاستيراد')}</AlertTitle>
            </Alert>

            {/* Statistics */}
            <Paper variant="outlined" sx={{ p: '1.0rem', mb: '1.0rem' }}>
              <Box display="flex" gap={2} flexWrap="wrap">
                <Chip label={`إجمالي الصفوف: ${result.summary?.total || 0}`} color="default" size="small" />
                <Chip label={`تم الإنشاء: ${result.summary?.inserted || 0}`} color="success" size="small" />
                <Chip label={`تم التحديث: ${result.summary?.updated || 0}`} color="info" size="small" />
                {(result.summary?.failed || 0) > 0 && <Chip label={`فشل: ${result.summary?.failed || 0}`} color="warning" size="small" />}
              </Box>
            </Paper>

            {/* Errors List */}
            {result.summary?.errors && result.summary.errors.length > 0 && (
              <Box>
                <Button
                  onClick={() => setShowErrors(!showErrors)}
                  endIcon={<ExpandMore sx={{ transform: showErrors ? 'rotate(180deg)' : 'none', transition: '0.3s' }} />}
                  size="small"
                  sx={{ mb: 1 }}
                >
                  عرض الأخطاء ({result.summary.errors.length})
                </Button>

                <Collapse in={showErrors}>
                  <TableContainer component={Paper} variant="outlined">
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>الصف</TableCell>
                          <TableCell>النوع</TableCell>
                          <TableCell>الوصف</TableCell>
                          <TableCell>القيمة</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {result.summary.errors.map((err, idx) => (
                          <TableRow key={idx}>
                            <TableCell>{err.row || '-'}</TableCell>
                            <TableCell>
                              <Chip label={err.column || 'خطأ'} color="error" size="small" />
                            </TableCell>
                            <TableCell>{err.error || '-'}</TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary">
                                {err.value || '-'}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Collapse>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        {result ? (
          <>
            <Button onClick={handleReset} variant="outlined">
              استيراد ملف آخر
            </Button>
            <Button onClick={handleCloseDialog} variant="contained">
              إغلاق
            </Button>
          </>
        ) : (
          <>
            <Button onClick={handleCloseDialog}>إلغاء</Button>
            <Button onClick={handleImport} variant="contained" disabled={!file || loading} startIcon={<CloudUpload />}>
              رفع واستيراد
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default ExcelImportDialog;
