import React, { useState, useContext } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Alert,
  CircularProgress,
  Stack,
  IconButton
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  Download as DownloadIcon,
  Close as CloseIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon
} from '@mui/icons-material';
import usersService from 'services/rbac/users.service';
import { openSnackbar } from 'api/snackbar';

const ProviderUsersImportModal = ({ open, onClose, onSuccess }) => {
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [result, setResult] = useState(null);

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setResult(null); // Clear previous results
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      setDownloading(true);
      const response = await usersService.downloadProviderUsersTemplate();
      
      const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'Provider_Users_Template.xlsx';
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();
      
      openSnackbar({
        open: true,
        message: 'تم تحميل القالب بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });
    } catch (error) {
      console.error('Error downloading template:', error);
      openSnackbar({
        open: true,
        message: 'حدث خطأ أثناء تحميل القالب',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setDownloading(false);
    }
  };

  const handleImport = async () => {
    if (!file) {
      openSnackbar({
        open: true,
        message: 'الرجاء اختيار ملف أولاً',
        variant: 'alert',
        alert: { color: 'warning' }
      });
      return;
    }

    try {
      setLoading(true);
      const response = await usersService.importProviderUsers(file);
      setResult(response);
      
      if (response.success) {
        openSnackbar({
          open: true,
          message: 'تم استيراد المستخدمين بنجاح',
          variant: 'alert',
          alert: { color: 'success' }
        });
        if (onSuccess) onSuccess();
      } else {
        openSnackbar({
          open: true,
          message: 'تم الاستيراد مع وجود بعض الأخطاء',
          variant: 'alert',
          alert: { color: 'warning' }
        });
        if (response.summary?.created > 0 && onSuccess) onSuccess();
      }
    } catch (error) {
      console.error('Error importing users:', error);
      openSnackbar({
        open: true,
        message: error.response?.data?.message || 'حدث خطأ أثناء استيراد الملف',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setFile(null);
    setResult(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={!loading ? handleClose : undefined} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        استيراد مستخدمي المرافق الصحية
        {!loading && (
          <IconButton onClick={handleClose} size="small">
            <CloseIcon />
          </IconButton>
        )}
      </DialogTitle>
      
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <Alert severity="info" icon={false}>
            <Typography variant="subtitle2" gutterBottom fontWeight="bold">تعليمات الاستيراد:</Typography>
            <Typography variant="body2" component="ul" sx={{ pl: 2, m: 0 }}>
              <li>قم بتحميل القالب أولاً لتعبئة البيانات بالصيغة الصحيحة.</li>
              <li>عمود "المرفق الصحي" يحتوي على قائمة منسدلة لتسهيل الاختيار.</li>
              <li>إذا تركت "كلمة المرور" فارغة، سيتم تعيينها تلقائياً إلى <code>Aa@1234567</code></li>
            </Typography>
          </Alert>

          <Box sx={{ display: 'flex', justifyContent: 'center' }}>
            <Button
              variant="outlined"
              color="primary"
              startIcon={downloading ? <CircularProgress size={20} /> : <DownloadIcon />}
              onClick={handleDownloadTemplate}
              disabled={downloading || loading}
              fullWidth
            >
              تحميل القالب الفارغ
            </Button>
          </Box>

          <Box
            sx={{
              border: '2px dashed',
              borderColor: file ? 'success.main' : 'grey.300',
              borderRadius: 1,
              p: 3,
              textAlign: 'center',
              bgcolor: file ? 'success.lighter' : 'grey.50'
            }}
          >
            <input
              accept=".xlsx, .xls"
              style={{ display: 'none' }}
              id="raised-button-file"
              type="file"
              onChange={handleFileChange}
              disabled={loading}
            />
            <label htmlFor="raised-button-file">
              <Button
                variant="contained"
                component="span"
                startIcon={<CloudUploadIcon />}
                disabled={loading}
                sx={{ mb: 2 }}
              >
                اختر ملف الإكسيل
              </Button>
            </label>
            {file && (
              <Typography variant="body2" color="success.main" fontWeight="bold">
                تم اختيار الملف: {file.name}
              </Typography>
            )}
            {!file && (
              <Typography variant="body2" color="textSecondary">
                الامتدادات المدعومة: .xlsx, .xls
              </Typography>
            )}
          </Box>

          {/* Results Display */}
          {result && (
            <Box>
              <Typography variant="subtitle1" gutterBottom fontWeight="bold">
                نتيجة الاستيراد:
              </Typography>
              <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
                <Box sx={{ bgcolor: 'success.lighter', p: 1.5, borderRadius: 1, flex: 1, textAlign: 'center' }}>
                  <Typography variant="h6" color="success.main">{result.summary?.created || 0}</Typography>
                  <Typography variant="body2">تمت الإضافة</Typography>
                </Box>
                <Box sx={{ bgcolor: 'error.lighter', p: 1.5, borderRadius: 1, flex: 1, textAlign: 'center' }}>
                  <Typography variant="h6" color="error.main">{result.summary?.failed || 0}</Typography>
                  <Typography variant="body2">فشل</Typography>
                </Box>
                <Box sx={{ bgcolor: 'grey.100', p: 1.5, borderRadius: 1, flex: 1, textAlign: 'center' }}>
                  <Typography variant="h6">{result.summary?.totalRows || 0}</Typography>
                  <Typography variant="body2">الإجمالي</Typography>
                </Box>
              </Stack>

              {result.errors && result.errors.length > 0 && (
                <Box sx={{ maxHeight: 200, overflow: 'auto', bgcolor: 'error.lighter', p: 2, borderRadius: 1 }}>
                  <Typography variant="subtitle2" color="error.main" gutterBottom>
                    تفاصيل الأخطاء:
                  </Typography>
                  {result.errors.map((error, index) => (
                    <Typography key={index} variant="body2" color="error.dark" sx={{ mb: 0.5 }}>
                      • الصف {error.rowNumber}: {error.messageAr}
                    </Typography>
                  ))}
                </Box>
              )}
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={handleClose} color="error" disabled={loading}>
          إلغاء
        </Button>
        <Button
          onClick={handleImport}
          variant="contained"
          disabled={!file || loading}
          startIcon={loading ? <CircularProgress size={20} color="inherit" /> : null}
        >
          {loading ? 'جاري الاستيراد...' : 'بدء الاستيراد'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ProviderUsersImportModal;
