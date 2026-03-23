import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { Close, Minimize, ExpandLess, CheckCircle, Error as ErrorIcon, ListAlt } from '@mui/icons-material';
import {
  Box,
  Typography,
  Paper,
  LinearProgress,
  IconButton,
  Collapse,
  Alert,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  Chip
} from '@mui/material';
import axios from 'utils/axios';

const ImportProgressContext = createContext(null);

export const useImportProgress = () => useContext(ImportProgressContext);

export const GlobalImportProgressProvider = ({ children }) => {
  const [activeImport, setActiveImport] = useState(null); // { batchId, fileName, status, progress }
  const [isMinimized, setIsMinimized] = useState(false);
  const [importHistory, setImportHistory] = useState([]);
  const [errorDetails, setErrorDetails] = useState(null); // { batchId, errors: [] }
  const [isErrorModalOpen, setIsErrorModalOpen] = useState(false);

  const POLLING_INTERVAL = 2000;

  const startImport = useCallback((batchId, fileName) => {
    setActiveImport({
      batchId,
      fileName,
      status: 'PROCESSING',
      progress: 0,
      counts: { created: 0, updated: 0, skipped: 0, error: 0 },
      total: 0
    });
    setIsMinimized(false);
  }, []);

  const checkStatus = useCallback(async () => {
    if (!activeImport || activeImport.status === 'COMPLETED' || activeImport.status === 'FAILED') return;

    try {
      // Using the endpoint we verified: MemberExcelTemplateController
      // Using relative path since baseURL handles /api
      const response = await axios.get(`unified-members/import/status/${activeImport.batchId}`);
      const log = response.data?.data; // ApiResponse.data contains the MemberImportLog

      if (log) {
        const total = log.totalRows || 0;
        const processed = (log.createdCount || 0) + (log.updatedCount || 0) + (log.skippedCount || 0) + (log.errorCount || 0);
        let progress = total > 0 ? (processed / total) * 100 : 0;

        // STATUS HIERARCHY (To prevent backward jumps)
        const statusOrder = { PENDING: 0, VALIDATING: 1, VALIDATION: 1, PROCESSING: 2, COMPLETED: 3, FAILED: 3, PARTIAL: 3 };
        const currentStatus = activeImport.status?.toUpperCase() || 'PENDING';
        const receivedStatus = log.status?.toUpperCase();

        const currentRank = statusOrder[currentStatus] || 0;
        const receivedRank = statusOrder[receivedStatus] || 0;

        // PREVENT PROGRESS REGRESSION
        if (progress < activeImport.progress && receivedRank <= currentRank) {
          progress = activeImport.progress;
        }

        // PREVENT STATUS REGRESSION (e.g. going from PROCESSING back to VALIDATING)
        let finalStatus = receivedStatus;
        if (receivedRank < currentRank) {
          finalStatus = currentStatus; // Stay in higher status
        }

        const updatedState = {
          ...activeImport,
          status: finalStatus,
          progress: progress,
          processedRows: processed, // NEW: for detailed display
          counts: {
            created: log.createdCount || 0,
            updated: log.updatedCount || 0,
            skipped: log.skippedCount || 0,
            error: log.errorCount || 0
          },
          total: total,
          errorMessage: log.errorMessage || (finalStatus === 'FAILED' ? 'فشل الاستيراد: الرجاء التأكد من صحة الملف وصلاحياتك.' : null)
        };

        setActiveImport(updatedState);

        // UX: Auto-minimize after completion to move to background
        if (finalStatus === 'COMPLETED' && !isMinimized) {
          setTimeout(() => {
            setIsMinimized(true);
          }, 3000); // Wait 3 seconds before minimizing
        } else if (finalStatus === 'FAILED' && !isMinimized) {
          // Keep failed open for review, or minimize after longer delay?
          // Let's keep failed open.
        }
      }
    } catch (error) {
      console.error('Failed to poll import status', error);
    }
  }, [activeImport]);

  useEffect(() => {
    let intervalId;
    const terminalStatuses = ['COMPLETED', 'FAILED', 'PARTIAL'];
    if (activeImport && !terminalStatuses.includes(activeImport.status)) {
      intervalId = setInterval(checkStatus, POLLING_INTERVAL);
    }
    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [activeImport, checkStatus]);

  const dismissImport = () => {
    if (activeImport?.status === 'COMPLETED' || activeImport?.status === 'FAILED') {
      setImportHistory((prev) => [activeImport, ...prev]);
      setActiveImport(null);
    } else {
      setIsMinimized(true);
    }
  };

  const viewErrors = async (batchId) => {
    try {
      const response = await axios.get(`unified-members/import/errors/${batchId}`);
      const errors = response.data?.data || response.data?.result || [];
      setErrorDetails({ batchId, errors });
      setIsErrorModalOpen(true);
    } catch (err) {
      console.error('Failed to fetch import errors', err);
    }
  };

  return (
    <ImportProgressContext.Provider value={{ startImport, activeImport, isMinimized, setIsMinimized }}>
      {children}

      {/* Floating Widget */}
      {activeImport && (
        <Box
          sx={{
            position: 'fixed',
            bottom: '12.0rem',
            right: '1.5rem',
            zIndex: 9999,
            width: '20.0rem',
            transition: 'all 0.3s ease'
          }}
        >
          <Paper
            elevation={6}
            sx={{
              p: '1.0rem',
              borderLeft: '6px solid',
              borderColor:
                activeImport.status === 'COMPLETED' ? 'success.main' : activeImport.status === 'FAILED' ? 'error.main' : 'primary.main',
              overflow: 'hidden'
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: isMinimized ? 0 : 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 1 }}>
                {activeImport.status === 'PROCESSING' && <CircularLoader size={16} />}
                {activeImport.status === 'COMPLETED' && <CheckCircle color="success" fontSize="small" />}
                {activeImport.status === 'FAILED' && <ErrorIcon color="error" fontSize="small" />}
                {activeImport.fileName}
              </Typography>
              <Box>
                <IconButton size="small" onClick={() => setIsMinimized(!isMinimized)}>
                  {isMinimized ? <ExpandLess /> : <Minimize />}
                </IconButton>
                {(activeImport.status === 'COMPLETED' || activeImport.status === 'FAILED') && (
                  <IconButton size="small" onClick={dismissImport}>
                    <Close />
                  </IconButton>
                )}
              </Box>
            </Box>

            <Collapse in={!isMinimized}>
              <Box sx={{ mt: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" color="textSecondary">
                    {(() => {
                      const s = activeImport.status?.toString()?.toUpperCase();
                      if (s === 'PROCESSING') return 'جاري الاستيراد...';
                      if (s === 'VALIDATING' || s === 'VALIDATION') return 'جاري فحص الملف...';
                      if (s === 'COMPLETED') return 'تم الانتهاء بنجاح';
                      if (s === 'PARTIAL') return 'اكتمل مع وجود أخطاء';
                      if (s === 'FAILED') return 'فشل الاستيراد';
                      if (s === 'PENDING') return 'في الانتظار...';
                      return 'جاري التحضير...';
                    })()}
                  </Typography>
                  <Typography variant="caption" fontWeight="bold">
                    {activeImport.processedRows || 0} / {activeImport.total || 0} ({Math.round(activeImport.progress)}%)
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={activeImport.progress}
                  color={
                    activeImport.status === 'FAILED'
                      ? 'error'
                      : activeImport.status === 'COMPLETED'
                        ? 'success'
                        : activeImport.status === 'PARTIAL'
                          ? 'warning'
                          : 'primary'
                  }
                  sx={{ height: '0.375rem', borderRadius: 1 }}
                />

                <Box sx={{ display: 'flex', gap: 1, mt: '0.75rem', flexWrap: 'wrap' }}>
                  <ChipLabel label="جديد" count={activeImport.counts.created} color="success.main" />
                  <ChipLabel label="تحديث" count={activeImport.counts.updated} color="info.main" />
                  <ChipLabel label="أخطاء" count={activeImport.counts.error} color="error.main" />
                </Box>

                {(activeImport.status === 'FAILED' || activeImport.errorMessage) && (
                  <Alert severity="error" sx={{ mt: 1, py: 0.5, fontSize: '0.8rem' }}>
                    {activeImport.errorMessage || 'فشل الاستيراد: الرجاء التأكد من صحة الملف وصلاحياتك.'}
                  </Alert>
                )}

                {activeImport.counts.error > 0 && (
                  <Button
                    fullWidth
                    variant="outlined"
                    size="small"
                    startIcon={<ListAlt />}
                    onClick={() => viewErrors(activeImport.batchId)}
                    sx={{ mt: 1, fontSize: '0.75rem' }}
                  >
                    عرض تفاصيل الأخطاء
                  </Button>
                )}
              </Box>
            </Collapse>
          </Paper>
        </Box>
      )}

      {/* Error Details Modal */}
      <Dialog open={isErrorModalOpen} onClose={() => setIsErrorModalOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          تفاصيل أخطاء الاستيراد
          <IconButton onClick={() => setIsErrorModalOpen(false)} size="small">
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          {errorDetails?.errors?.length > 0 ? (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>رقم الصف</TableCell>
                  <TableCell>نوع الخطأ</TableCell>
                  <TableCell>الوصف</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {errorDetails.errors.map((error, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{error.rowNumber}</TableCell>
                    <TableCell>
                      <Chip label={error.errorType} size="small" color="error" variant="outlined" />
                    </TableCell>
                    <TableCell>{error.messageAr || error.message}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Box sx={{ p: '1.5rem', textAlign: 'center' }}>
              <CheckCircle color="success" sx={{ fontSize: '3.0rem', mb: 1 }} />
              <Typography>لا توجد أخطاء مسجلة أو تم تصفير السجل</Typography>
            </Box>
          )}
        </DialogContent>
      </Dialog>
    </ImportProgressContext.Provider>
  );
};

// Internal Components
const CircularLoader = ({ size }) => (
  <Box
    sx={{
      animation: 'spin 1s linear infinite',
      display: 'flex',
      '@keyframes spin': { '0%': { transform: 'rotate(0deg)' }, '100%': { transform: 'rotate(360deg)' } }
    }}
  >
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
      <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
    </svg>
  </Box>
);

const ChipLabel = ({ label, count, color }) => (
  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, bgcolor: 'action.hover', px: 1, py: 0.5, borderRadius: 1 }}>
    <Box sx={{ width: '0.375rem', height: '0.375rem', borderRadius: '50%', bgcolor: color }} />
    <Typography variant="caption" color="textSecondary">
      {label}:
    </Typography>
    <Typography variant="caption" fontWeight="bold" sx={{ color: color }}>
      {count}
    </Typography>
  </Box>
);

export default ImportProgressContext;


