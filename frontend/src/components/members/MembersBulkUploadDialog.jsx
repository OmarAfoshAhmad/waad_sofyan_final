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
  Stack,
  LinearProgress,
  IconButton,
  Grid,
  Paper,
  FormControlLabel,
  Checkbox
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  Close as CloseIcon,
  Download as DownloadIcon,
  FileDownload as FileDownloadIcon,
  InsertDriveFile as FileIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import { downloadTemplate, previewImport, executeImport } from 'services/api/unified-members.service';
import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';

/** Client-side CSV export -- the data is already in hand, no backend round trip needed. */
function downloadCsv(filename, headers, rows) {
  const escape = (value) => {
    const text = value === null || value === undefined ? '' : String(value);
    return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  const lines = [headers.map(escape).join(','), ...rows.map((row) => row.map(escape).join(','))];
  // BOM so Excel opens Arabic text as UTF-8 instead of guessing a legacy codepage.
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.parentNode.removeChild(link);
  window.URL.revokeObjectURL(url);
}

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
  const [preview, setPreview] = useState(null);
  const [clearOldMembers, setClearOldMembers] = useState(false);
  const [selectedEmployerId, setSelectedEmployerId] = useState('');

  const handleFileChange = (event) => {
    const file = event.target.files?.[0];
    if (file) {
      if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
        enqueueSnackbar(LABELS.invalidFileType, { variant: 'error' });
        return;
      }
      setSelectedFile(file);
      setPreview(null);
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
    try {
      if (!preview) {
        const response = await previewImport(selectedFile, {
          employerId: selectedEmployerId
        });
        const data = response?.data || response;
        setPreview(data);
        if (!data?.canProceed) {
          enqueueSnackbar('لا توجد صفوف صالحة للتنفيذ. راجع أخطاء المعاينة.', { variant: 'warning' });
        } else {
          enqueueSnackbar(`اكتملت المعاينة: ${data.validRows || 0} صف صالح`, { variant: 'success' });
        }
        return;
      }

      const response = await executeImport(selectedFile, {
        employerId: selectedEmployerId,
        batchId: preview.batchId,
        headerRowNumber: preview.headerRowNumber,
        clearOldMembers
      });
      const data = response?.data || response;
      setResult(data);

      if (data?.success) {
        enqueueSnackbar(`${LABELS.success}: ${data.summary?.created} عضو`, { variant: 'success' });
      } else {
        enqueueSnackbar('اكتمل الاستيراد مع وجود أخطاء', { variant: 'warning' });
      }

      if (onSuccess) onSuccess(data);
    } catch (error) {
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
      setPreview(null);
      setResult(null);
      setClearOldMembers(false);
      setSelectedEmployerId('');
      onClose();
    }
  };

  const handleRemoveFile = (e) => {
    e.stopPropagation();
    setSelectedFile(null);
    setPreview(null);
    setResult(null);
    setClearOldMembers(false);
    setSelectedEmployerId('');
  };

  const exportErrorsCsv = () => {
    if (!result?.errors?.length) return;
    downloadCsv(
      `تقرير_الاستيراد_${selectedFile?.name || 'errors'}.csv`,
      ['الصف', 'المعرف/الاسم', 'السبب', 'القيمة'],
      result.errors.map((err) => [err.rowNumber, err.rowIdentifier || '-', err.messageAr, err.value || '-'])
    );
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

              <EmployerFilterSelector
                selectedEmployerId={selectedEmployerId}
                onEmployerChange={(employer) => {
                  setSelectedEmployerId(employer?.id || '');
                  setPreview(null);
                }}
                showAllOption={false}
                label="جهة العمل (الشريك)"
                placeholder="اختر جهة العمل التي ترغب بالاستيراد إليها..."
                disabled={uploading}
                sx={{ width: '100%' }}
              />

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
              <FormControlLabel
                control={
                  <Checkbox
                    checked={clearOldMembers}
                    onChange={(e) => {
                      setClearOldMembers(e.target.checked);
                      setPreview(null);
                    }}
                    color="primary"
                    disabled={uploading}
                  />
                }
                label={
                  <Typography variant="body2" sx={{ fontWeight: 'medium' }}>
                    مسح المستفيدين القدامى قبل الاستيراد (سيتم الإبقاء على المستفيدين الذين لديهم حركات مالية)
                  </Typography>
                }
                sx={{ alignSelf: 'flex-start', mt: 1 }}
              />
            </>
          )}

          {preview && !result && (
            <Alert severity={preview.canProceed ? 'success' : 'warning'}>
              <Typography variant="subtitle1" fontWeight="bold">نتيجة المعاينة قبل التنفيذ</Typography>
              <Typography variant="body2">
                الإجمالي: {preview.totalRows || 0} — صالح: {preview.validRows || 0} — غير صالح: {preview.invalidRows || 0}
              </Typography>
              {preview.warnings?.map((warning) => (
                <Typography key={warning} variant="caption" display="block">{warning}</Typography>
              ))}
            </Alert>
          )}

          {result && ( // Show import summary and errors if result is available
            <Box>
              <Typography variant="h6" gutterBottom>
                ملخص الاستيراد
              </Typography>
              <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'primary.lighter' }}>
                    <Typography variant="h4" color="primary.main">
                      {result.summary?.totalRows || 0}
                    </Typography>
                    <Typography variant="caption">إجمالي الصفوف</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'success.lighter' }}>
                    <Typography variant="h4" color="success.main">
                      {result.summary?.created || 0}
                    </Typography>
                    <Typography variant="caption">تم استيرادها</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'warning.lighter' }}>
                    <Typography variant="h4" color="warning.main">
                      {result.summary?.skipped || 0}
                    </Typography>
                    <Typography variant="caption">تكرار/تخطي</Typography>
                  </Paper>
                </Grid>
                <Grid size={3}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'error.lighter' }}>
                    <Typography variant="h4" color="error.main">
                      {(result.summary?.rejected || 0) + (result.summary?.failed || 0)}
                    </Typography>
                    <Typography variant="caption">فشل</Typography>
                  </Paper>
                </Grid>
              </Grid>

              {result.errors && result.errors.length > 0 && (
                <Box>
                  <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                    <Typography variant="subtitle1" color="error" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      تفاصيل الأخطاء ({result.errors.length})
                    </Typography>
                    <Button size="small" startIcon={<FileDownloadIcon />} onClick={exportErrorsCsv}>
                      تصدير التقرير
                    </Button>
                  </Stack>
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
              <Typography variant="body1" sx={{ mb: 1 }}>
                {LABELS.uploading}
              </Typography>
              {/* Indeterminate on purpose: there is no real progress figure from
                  the server to show, and a fake percentage that freezes near the
                  end is worse than admitting the wait time is unknown. */}
              <LinearProgress sx={{ height: '0.625rem', borderRadius: '0.3125rem', mb: 1 }} />
              <Typography variant="caption" color="textSecondary">
                قد تستغرق معالجة الملفات الكبيرة عدة دقائق، خصوصاً للملفات التي تحتوي آلاف الصفوف...
              </Typography>
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
            disabled={!selectedFile || !selectedEmployerId || uploading || (preview && !preview.canProceed)}
            variant="contained"
            color="primary"
            startIcon={<CloudUploadIcon />}
          >
            {uploading ? 'جاري المعالجة...' : preview ? 'تأكيد وتنفيذ الاستيراد' : 'معاينة الملف'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default MembersBulkUploadDialog;
