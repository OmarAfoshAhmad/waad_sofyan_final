import { useState, useEffect, useRef } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
  Paper,
  Alert,
  LinearProgress,
  Stack,
  Divider,
  IconButton
} from '@mui/material';
import {
  FileDownload as FileDownloadIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Close as CloseIcon
} from '@mui/icons-material';

/**
 * Reusable Data Export Wizard
 *
 * Provides a standard UI for exporting data to Excel with progress feedback.
 *
 * @param {boolean} open - Dialog open state
 * @param {function} onClose - Dialog close handler
 * @param {function} onExport - Function that performs the export and returns the blob
 * @param {string} title - Dialog title
 * @param {string} fileName - Default filename for the export
 * @param {Object} params - Current filter parameters
 */
const DataExportWizard = ({ open, onClose, onExport, title = 'تصدير البيانات', fileName = 'export.xlsx', params = {} }) => {
  const [status, setStatus] = useState('IDLE'); // IDLE, PROCESSING, SUCCESS, ERROR
  const [error, setError] = useState(null);
  const [progress, setProgress] = useState(0);
  const progressTimerRef = useRef(null);

  const startProgressSimulation = () => {
    setProgress(0);
    progressTimerRef.current = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 90) return prev; // Hold at 90% until real completion
        const increment = prev < 50 ? 8 : prev < 75 ? 4 : 1;
        return Math.min(prev + increment, 90);
      });
    }, 300);
  };

  const stopProgressSimulation = (finalValue = 100) => {
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
    setProgress(finalValue);
  };

  // Reset on open
  useEffect(() => {
    if (open) {
      setStatus('IDLE');
      setError(null);
      setProgress(0);
    }
    return () => stopProgressSimulation(0);
  }, [open]);

  const handleStartExport = async () => {
    setStatus('PROCESSING');
    setError(null);
    startProgressSimulation();

    try {
      const blob = await onExport(params);

      stopProgressSimulation(100);

      // Trigger download
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      setStatus('SUCCESS');
      // Auto close after 2 seconds on success
      setTimeout(() => {
        onClose();
      }, 2000);
    } catch (err) {
      stopProgressSimulation(0);
      console.error('Export failed:', err);
      setError(err.message || 'فشل تصدير البيانات. يرجى المحاولة مرة أخرى.');
      setStatus('ERROR');
    }
  };

  const renderContent = () => {
    switch (status) {
      case 'IDLE':
        return (
          <Box sx={{ py: '1.5rem', textAlign: 'center' }}>
            <FileDownloadIcon sx={{ fontSize: '4.0rem', color: 'primary.main', mb: '1.0rem', opacity: 0.8 }} />
            <Typography variant="h6" gutterBottom>
              جاهز للتصدير
            </Typography>
            <Typography variant="body2" color="textSecondary">
              سيتم تصدير البيانات بناءً على الفلاتر النشطة حالياً في الجدول.
            </Typography>
            <Paper variant="outlined" sx={{ mt: '1.5rem', p: '1.0rem', bgcolor: 'grey.50', textAlign: 'right' }}>
              <Typography variant="caption" color="textSecondary" display="block">
                الفلاتر المطبقة:
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
                {params.searchTerm && (
                  <Typography variant="caption" sx={{ bgcolor: 'white', px: 1, borderRadius: 1, border: '1px solid #ddd' }}>
                    البحث: {params.searchTerm}
                  </Typography>
                )}
                {params.organizationId && (
                  <Typography variant="caption" sx={{ bgcolor: 'white', px: 1, borderRadius: 1, border: '1px solid #ddd' }}>
                    جهة محددة
                  </Typography>
                )}
                {params.status && (
                  <Typography variant="caption" sx={{ bgcolor: 'white', px: 1, borderRadius: 1, border: '1px solid #ddd' }}>
                    الحالة: {params.status}
                  </Typography>
                )}
                {params.type && (
                  <Typography variant="caption" sx={{ bgcolor: 'white', px: 1, borderRadius: 1, border: '1px solid #ddd' }}>
                    النوع: {params.type}
                  </Typography>
                )}
                {params.deleted && (
                  <Typography variant="caption" sx={{ bgcolor: 'warning.lighter', px: 1, borderRadius: 1, border: '1px solid #f9d8d8' }}>
                    المحذوفات
                  </Typography>
                )}
              </Stack>
            </Paper>
          </Box>
        );
      case 'PROCESSING':
        return (
          <Box sx={{ py: '2.0rem', textAlign: 'center' }}>
            <FileDownloadIcon sx={{ fontSize: '3.5rem', color: 'primary.main', mb: '1.0rem', opacity: 0.8 }} />
            <Typography variant="h6" gutterBottom>
              جاري معالجة البيانات...
            </Typography>
            <Typography variant="body2" color="textSecondary" sx={{ mb: '1.5rem' }}>
              يرجى الانتظار بينما نقوم بتجميع ملف الإكسل الخاص بك.
            </Typography>
            <Box sx={{ px: '1.0rem' }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                <Typography variant="caption" color="textSecondary">التقدم</Typography>
                <Typography variant="caption" color="primary" fontWeight="bold">{progress}%</Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={progress}
                sx={{ height: '0.625rem', borderRadius: '0.3125rem' }}
              />
            </Box>
          </Box>
        );
      case 'SUCCESS':
        return (
          <Box sx={{ py: '3.0rem', textAlign: 'center' }}>
            <CheckCircleIcon sx={{ fontSize: '5.0rem', color: 'success.main', mb: '1.0rem', animation: 'bounce 1s' }} />
            <Typography variant="h6" color="success.main" gutterBottom>
              تم التصدير بنجاح!
            </Typography>
            <Typography variant="body2" color="textSecondary">
              بدأ تحميل الملف تلقائياً.
            </Typography>
          </Box>
        );
      case 'ERROR':
        return (
          <Box sx={{ py: '2.0rem', textAlign: 'center' }}>
            <ErrorIcon sx={{ fontSize: '4.0rem', color: 'error.main', mb: '1.0rem' }} />
            <Typography variant="h6" color="error.main" gutterBottom>
              حدث خطأ أثناء التصدير
            </Typography>
            <Alert severity="error" sx={{ mt: '1.0rem', textAlign: 'right' }}>
              {error}
            </Alert>
          </Box>
        );
      default:
        return null;
    }
  };

  return (
    <Dialog
      open={open}
      onClose={status === 'PROCESSING' ? null : onClose}
      maxWidth="xs"
      fullWidth
      sx={{
        '& .MuiDialog-paper': {
          borderRadius: '0.1875rem',
          boxShadow: '0 8px 32px rgba(0,0,0,0.1)'
        }
      }}
    >
      <style>
        {`
                @keyframes bounce {
                    0%, 100% { transform: translateY(0); }
                    50% { transform: translateY(-10px); }
                }
                `}
      </style>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: '1.25rem' }}>
        <Typography variant="h6">{title}</Typography>
        {status !== 'PROCESSING' && (
          <IconButton size="small" onClick={onClose}>
            <CloseIcon />
          </IconButton>
        )}
      </DialogTitle>
      <Divider />
      <DialogContent>{renderContent()}</DialogContent>
      {status !== 'PROCESSING' && status !== 'SUCCESS' && (
        <DialogActions sx={{ px: '1.5rem', pb: '1.0rem' }}>
          <Button onClick={onClose} variant="text" color="inherit">
            إلغاء
          </Button>
          <Button
            onClick={handleStartExport}
            variant="contained"
            color="primary"
            startIcon={<FileDownloadIcon />}
            disabled={status === 'PROCESSING'}
          >
            بدء التصدير
          </Button>
        </DialogActions>
      )}
    </Dialog>
  );
};

export default DataExportWizard;
