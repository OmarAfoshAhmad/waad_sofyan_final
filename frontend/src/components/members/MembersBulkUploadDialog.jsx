import { useState, useRef } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Alert,
  Stack,
  LinearProgress,
  IconButton,
  Grid,
  Paper
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  Close as CloseIcon,
  Download as DownloadIcon,
  InsertDriveFile as FileIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import { downloadTemplate, importMembers } from 'services/api/unified-members.service';

// Static Arabic labels
const LABELS = {
  title: 'استيراد الأعضاء (Excel)',
  close: 'إغلاق',
  downloadTemplate: 'تحميل القالب',
  info: 'قم بتحميل القالب المعتمد، تعبئة بيانات الأعضاء، ثم إعادة رفعه هنا.',
  invalidFileType: 'الرجاء اختيار ملف Excel (.xlsx)',
  selectFile: 'الرجاء اختيار ملف أولاً',
  clickToUpload: 'اضغط هنا لاختيار ملف Excel المعبأ',
  dragDrop: 'أو قم بسحب وإسقاط الملف هنا',
  uploading: 'جار الرفع والمعالجة...',
  cancel: 'إلغاء',
  upload: 'رفع واستيراد',
  success: 'تم استيراد الأعضاء بنجاح',
  successSummary: 'تم إضافة {count} عضو',
  error: 'فشل في استيراد الملف'
};

const MembersBulkUploadDialog = ({ open, onClose, onSuccess }) => {
  const { enqueueSnackbar } = useSnackbar();
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [result, setResult] = useState(null);
  const [progress, setProgress] = useState(0);
  const progressTimerRef = useRef(null);

  const startProgressSimulation = () => {
    setProgress(0);
    progressTimerRef.current = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 85) return prev;
        const increment = prev < 40 ? 6 : prev < 65 ? 3 : 1;
        return Math.min(prev + increment, 85);
      });
    }, 400);
  };

  const stopProgressSimulation = (finalValue = 100) => {
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
    setProgress(finalValue);
  };

  const handleFileChange = (event) => {
    const file = event.target.files?.[0];
    if (file) {
      if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
        enqueueSnackbar(LABELS.invalidFileType, { variant: 'error' });
        return;
      }
      setSelectedFile(file);
      setResult(null); // Clear result when a new file is selected
    }
  };

  const handleDownloadTemplate = async () => {
    setDownloading(true);
    try {
      const blob = await downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'Members_Import_Template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      enqueueSnackbar('تم تحميل القالب بنجاح', { variant: 'success' });
    } catch (error) {
      console.error('Template download failed:', error);
      enqueueSnackbar('فشل تحميل القالب', { variant: 'error' });
    } finally {
      setDownloading(false);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      enqueueSnackbar(LABELS.selectFile, { variant: 'warning' });
      return;
    }

    setUploading(true);
    setResult(null);
    startProgressSimulation();
    try {
      const response = await importMembers(selectedFile);
      const data = response?.data || response;
      stopProgressSimulation(100);
      setResult(data);

      if (data?.success) {
        enqueueSnackbar(`${LABELS.success}: ${data.summary?.created} عضو`, { variant: 'success' });
      } else {
        enqueueSnackbar('اكتمل الاستيراد مع وجود أخطاء', { variant: 'warning' });
      }

      if (onSuccess) onSuccess(data);
    } catch (error) {
      stopProgressSimulation(0);
      console.error('Upload failed:', error.response?.data || error.message);
      const errorMessage = error.response?.data?.message || error.message || LABELS.error;
      enqueueSnackbar(errorMessage, { variant: 'error' });
    } finally {
      setUploading(false);
    }
  };

  const handleClose = () => {
    if (!uploading) {
      setSelectedFile(null);
      setResult(null);
      setProgress(0);
      onClose();
    }
  };

  const handleRemoveFile = (e) => {
    e.stopPropagation();
    setSelectedFile(null);
    setResult(null);
    setProgress(0);
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth disableEnforceFocus>
      <DialogTitle>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="h5">{LABELS.title}</Typography>
          {!uploading && (
            <IconButton onClick={handleClose} size="small">
              <CloseIcon />
            </IconButton>
          )}
        </Stack>
      </DialogTitle>

      <DialogContent dividers>
        <Stack spacing={3}>
          {!result && ( // Only show info/upload section if no result yet
            <>
              <Alert severity="info" icon={<DownloadIcon />}>
                {LABELS.info}
                <Box mt={1}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={handleDownloadTemplate}
                    disabled={downloading || uploading}
                    startIcon={<DownloadIcon />}
                  >
                    {downloading ? 'جار التحميل...' : LABELS.downloadTemplate}
                  </Button>
                </Box>
              </Alert>

              <Box
                component="label"
                sx={{
                  border: '2px dashed',
                  borderColor: selectedFile ? 'success.main' : 'divider',
                  borderRadius: '0.25rem',
                  p: '2.0rem',
                  textAlign: 'center',
                  backgroundColor: selectedFile ? 'success.lighter' : 'background.paper',
                  cursor: uploading ? 'default' : 'pointer',
                  transition: 'all 0.3s',
                  '&:hover': {
                    borderColor: uploading ? undefined : 'primary.main',
                    backgroundColor: uploading ? undefined : 'primary.lighter'
                  },
                  position: 'relative'
                }}
              >
                <input type="file" hidden accept=".xlsx,.xls" onChange={handleFileChange} disabled={uploading} />

                <Stack spacing={2} alignItems="center">
                  {selectedFile ? (
                    <>
                      <FileIcon color="success" sx={{ fontSize: '3.0rem' }} />
                      <Typography variant="h6" color="success.dark">
                        {selectedFile.name}
                      </Typography>
                      <Button color="error" size="small" onClick={handleRemoveFile} disabled={uploading}>
                        إزالة الملف
                      </Button>
                    </>
                  ) : (
                    <>
                      <CloudUploadIcon color="action" sx={{ fontSize: '3.0rem' }} />
                      <Typography variant="body1" color="textSecondary">
                        {LABELS.clickToUpload}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {LABELS.dragDrop}
                      </Typography>
                    </>
                  )}
                </Stack>
              </Box>
            </>
          )}

          {result && ( // Show import summary and errors if result is available
            <Box>
              <Typography variant="h6" gutterBottom>ملخص الاستيراد</Typography>
              <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'primary.lighter' }}>
                    <Typography variant="h4" color="primary.main">{result.summary?.totalRows || 0}</Typography>
                    <Typography variant="caption">إجمالي الصفوف</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'success.lighter' }}>
                    <Typography variant="h4" color="success.main">{result.summary?.created || 0}</Typography>
                    <Typography variant="caption">تم استيرادها</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'warning.lighter' }}>
                    <Typography variant="h4" color="warning.main">{result.summary?.skipped || 0}</Typography>
                    <Typography variant="caption">تكرار/تخطي</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'error.lighter' }}>
                    <Typography variant="h4" color="error.main">{(result.summary?.rejected || 0) + (result.summary?.failed || 0)}</Typography>
                    <Typography variant="caption">فشل</Typography>
                  </Paper>
                </Grid>
              </Grid>

              {result.errors && result.errors.length > 0 && (
                <Box>
                  <Typography variant="subtitle1" color="error" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    تفاصيل الأخطاء ({result.errors.length})
                  </Typography>
                  <Box sx={{ maxHeight: '18.75rem', overflow: 'auto', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                      <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f5f5f5' }}>
                        <tr>
                          <th style={{ padding: '0.375rem', textAlign: 'right', borderBottom: '2px solid #ddd' }}>الصف</th>
                          <th style={{ padding: '0.375rem', textAlign: 'right', borderBottom: '2px solid #ddd' }}>المعرف/الاسم</th>
                          <th style={{ padding: '0.375rem', textAlign: 'right', borderBottom: '2px solid #ddd' }}>السبب</th>
                          <th style={{ padding: '0.375rem', textAlign: 'right', borderBottom: '2px solid #ddd' }}>القيمة</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.errors.map((err, idx) => (
                          <tr key={idx} style={{ borderBottom: '1px solid #eee' }}>
                            <td style={{ padding: '0.375rem', textAlign: 'center' }}>{err.rowNumber}</td>
                            <td style={{ padding: '0.375rem' }}>{err.rowIdentifier || '-'}</td>
                            <td style={{ padding: '0.375rem', color: '#d32f2f' }}>{err.messageAr}</td>
                            <td style={{ padding: '0.375rem', fontFamily: 'monospace' }}>{err.value || '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </Box>
                </Box>
              )}
            </Box>
          )}

          {uploading && (
            <Box sx={{ width: '100%', py: '1.0rem' }}>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography variant="body1">{LABELS.uploading}</Typography>
                <Typography variant="body2" color="primary" fontWeight="bold">{progress}%</Typography>
              </Stack>
              <LinearProgress
                variant="determinate"
                value={progress}
                sx={{ height: '0.625rem', borderRadius: '0.3125rem', mb: 1 }}
              />
              <Typography variant="caption" color="textSecondary">قد تستغرق معالجة الملفات الكبيرة عدة دقائق...</Typography>
            </Box>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: '1.5rem', py: '1.0rem' }}>
        <Button onClick={handleClose} disabled={uploading} color="inherit">
          {result ? LABELS.close : LABELS.cancel}
        </Button>
        {!result && ( // Only show upload button if no result is displayed
          <Button
            onClick={handleUpload}
            disabled={!selectedFile || uploading}
            variant="contained"
            color="primary"
            startIcon={<CloudUploadIcon />}
          >
            {uploading ? 'جاري المعالجة...' : LABELS.upload}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default MembersBulkUploadDialog;


