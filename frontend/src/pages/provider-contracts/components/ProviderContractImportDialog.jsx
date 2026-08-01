/**
 * ProviderContractImportDialog
 * ═══════════════════════════════════════════════════════════════════════════
 * Two-stage bulk import of NEW provider contracts:
 *   1) Preview: upload the file, server validates every row (structural +
 *      business rules) without persisting anything.
 *   2) Confirm: persist only the rows that were valid at preview time.
 * The user can download the template up front, and download an error report
 * for any invalid rows to fix and re-upload.
 */

import { useRef, useState, useCallback } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import CloseIcon from '@mui/icons-material/Close';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import FileDownloadIcon from '@mui/icons-material/FileDownload';

import {
  downloadContractImportTemplate,
  previewContractImport,
  confirmContractImport,
  downloadContractImportErrors
} from 'services/api/provider-contracts.service';
import { useSnackbar } from 'notistack';

const ProviderContractImportDialog = ({ open, onClose, onImportComplete }) => {
  const { enqueueSnackbar } = useSnackbar();
  const fileInputRef = useRef(null);

  const [selectedFile, setSelectedFile] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [preview, setPreview] = useState(null); // ContractImportPreviewResultDto
  const [confirmResult, setConfirmResult] = useState(null); // ContractImportConfirmResultDto
  const [error, setError] = useState(null);

  const resetState = () => {
    setSelectedFile(null);
    setPreview(null);
    setConfirmResult(null);
    setError(null);
  };

  const handleClose = useCallback(() => {
    if (previewLoading || confirmLoading) return;
    resetState();
    onClose?.();
  }, [previewLoading, confirmLoading, onClose]);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.match(/\.(xlsx|xls)$/i)) {
      setError('يرجى اختيار ملف Excel بصيغة .xlsx أو .xls');
      return;
    }
    setSelectedFile(file);
    setPreview(null);
    setConfirmResult(null);
    setError(null);
  };

  const handlePreview = async () => {
    if (!selectedFile) return;
    setPreviewLoading(true);
    setError(null);
    try {
      const result = await previewContractImport(selectedFile);
      setPreview(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل تحليل الملف');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!preview?.sessionId) return;
    setConfirmLoading(true);
    setError(null);
    try {
      const result = await confirmContractImport(preview.sessionId);
      setConfirmResult(result);
      enqueueSnackbar(`تم إنشاء ${result.successCount} من ${result.totalRows} عقداً بنجاح`, {
        variant: result.failedCount > 0 ? 'warning' : 'success'
      });
      onImportComplete?.();
    } catch (err) {
      const msg = err?.response?.data?.message || 'فشل تأكيد الاستيراد';
      setError(msg);
      enqueueSnackbar(msg, { variant: 'error' });
    } finally {
      setConfirmLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          background: 'linear-gradient(135deg, #00796B 0%, #004D40 100%)',
          color: 'white',
          borderRadius: '12px 12px 0 0'
        }}
      >
        <FileUploadIcon />
        <Box flex={1}>
          <Typography variant="h6" fontWeight={700}>
            استيراد عقود مقدمي الخدمة
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.85 }}>
            تحقق من الملف قبل الحفظ، ثم أكِّد استيراد الصفوف الصحيحة فقط
          </Typography>
        </Box>
        <IconButton size="small" sx={{ color: 'white' }} onClick={handleClose} disabled={previewLoading || confirmLoading}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pt: 3 }}>
        {/* Step 0: template download — always visible until confirmed */}
        {!confirmResult && (
          <Alert
            severity="info"
            sx={{ mb: 2 }}
            action={
              <Button size="small" startIcon={<FileDownloadIcon />} onClick={() => downloadContractImportTemplate()}>
                تنزيل القالب
              </Button>
            }
          >
            حمّل القالب أولاً (يحتوي 3 أوراق: البيانات، التعليمات، القيم المرجعية) وعبّئه ثم ارفعه أدناه.
          </Alert>
        )}

        {/* Step 1: upload */}
        {!preview && !confirmResult && (
          <>
            <Box
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                const file = e.dataTransfer.files?.[0];
                if (!file) return;
                if (!file.name.match(/\.(xlsx|xls)$/i)) {
                  setError('يرجى اختيار ملف Excel بصيغة .xlsx أو .xls');
                  return;
                }
                setSelectedFile(file);
              }}
              onClick={() => fileInputRef.current?.click()}
              sx={{
                border: '2px dashed',
                borderColor: selectedFile ? 'success.main' : 'primary.light',
                borderRadius: 2,
                p: 4,
                textAlign: 'center',
                cursor: 'pointer',
                bgcolor: selectedFile ? 'success.lighter' : 'action.hover'
              }}
            >
              <input ref={fileInputRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleFileChange} />
              <CloudUploadIcon sx={{ fontSize: 52, color: selectedFile ? 'success.main' : 'primary.light', mb: 1 }} />
              {selectedFile ? (
                <Typography variant="body1" fontWeight={700} color="success.main">
                  {selectedFile.name}
                </Typography>
              ) : (
                <Typography variant="body1" fontWeight={600} color="primary.main">
                  اسحب ملف Excel هنا أو انقر للاختيار
                </Typography>
              )}
            </Box>

            {error && (
              <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
          </>
        )}

        {/* Step 2: preview results */}
        {preview && !confirmResult && (
          <>
            <Stack direction="row" spacing={1.5} flexWrap="wrap" mb={2} useFlexGap>
              <Chip label={`الإجمالي: ${preview.totalRows}`} variant="outlined" />
              <Chip color="success" label={`صالح: ${preview.validCount}`} icon={<CheckCircleIcon />} />
              <Chip color="error" label={`به أخطاء: ${preview.invalidCount}`} icon={<ErrorIcon />} />
            </Stack>

            {preview.invalidCount > 0 && (
              <Alert
                severity="warning"
                sx={{ mb: 2 }}
                action={
                  <Button size="small" startIcon={<FileDownloadIcon />} onClick={() => downloadContractImportErrors(preview.sessionId)}>
                    تنزيل تقرير الأخطاء
                  </Button>
                }
              >
                الصفوف التي بها أخطاء لن تُحفظ عند التأكيد. صحِّحها في الملف الأصلي وأعد رفعه إذا رغبت.
              </Alert>
            )}

            <Divider sx={{ mb: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                تفاصيل الصفوف
              </Typography>
            </Divider>

            <Box sx={{ maxHeight: 320, overflowY: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>الصف</TableCell>
                    <TableCell>مقدم الخدمة</TableCell>
                    <TableCell>رمز العقد</TableCell>
                    <TableCell align="center">الحالة</TableCell>
                    <TableCell>الأخطاء</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.rows.map((row) => (
                    <TableRow key={row.rowNumber} hover>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.providerName || row.providerNameRaw || '-'}</TableCell>
                      <TableCell>{row.contractCode || '-'}</TableCell>
                      <TableCell align="center">
                        {row.errors?.length ? (
                          <Chip size="small" color="error" label="خطأ" />
                        ) : (
                          <Chip size="small" color="success" label="صالح" />
                        )}
                      </TableCell>
                      <TableCell>
                        {(row.errors || []).map((err, i) => (
                          <Typography key={i} variant="caption" display="block" color="error.main">
                            • {err}
                          </Typography>
                        ))}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>

            {error && (
              <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
          </>
        )}

        {/* Step 3: confirm results */}
        {confirmResult && (
          <>
            <Stack direction="row" spacing={1.5} flexWrap="wrap" mb={2} useFlexGap>
              <Chip label={`الإجمالي: ${confirmResult.totalRows}`} variant="outlined" />
              <Chip color="success" label={`نجح: ${confirmResult.successCount}`} icon={<CheckCircleIcon />} />
              <Chip color="error" label={`فشل: ${confirmResult.failedCount}`} icon={<ErrorIcon />} />
              {confirmResult.skippedInvalidCount > 0 && (
                <Chip color="warning" label={`متخطى (أخطاء): ${confirmResult.skippedInvalidCount}`} />
              )}
            </Stack>

            <Box sx={{ maxHeight: 320, overflowY: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>الصف</TableCell>
                    <TableCell>مقدم الخدمة</TableCell>
                    <TableCell>رمز العقد</TableCell>
                    <TableCell align="center">النتيجة</TableCell>
                    <TableCell>الرسالة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {confirmResult.results.map((row) => (
                    <TableRow key={row.rowNumber} hover>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.providerName || '-'}</TableCell>
                      <TableCell>{row.contractCode || '-'}</TableCell>
                      <TableCell align="center">
                        {row.success ? <Chip size="small" color="success" label="تم" /> : <Chip size="small" color="error" label="فشل" />}
                      </TableCell>
                      <TableCell>{row.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        {confirmResult ? (
          <Button variant="contained" onClick={handleClose}>
            إغلاق
          </Button>
        ) : preview ? (
          <>
            <Button variant="outlined" onClick={resetState} disabled={confirmLoading}>
              رفع ملف آخر
            </Button>
            <Button variant="outlined" onClick={handleClose} disabled={confirmLoading}>
              إلغاء
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={confirmLoading ? <CircularProgress size={16} color="inherit" /> : <FileUploadIcon />}
              onClick={handleConfirm}
              disabled={confirmLoading || preview.validCount === 0}
            >
              {confirmLoading ? 'جارٍ التأكيد...' : `تأكيد استيراد (${preview.validCount})`}
            </Button>
          </>
        ) : (
          <>
            <Button variant="outlined" onClick={handleClose} disabled={previewLoading}>
              إلغاء
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={previewLoading ? <CircularProgress size={16} color="inherit" /> : <FileUploadIcon />}
              onClick={handlePreview}
              disabled={!selectedFile || previewLoading}
            >
              {previewLoading ? 'جارٍ التحليل...' : 'تحليل الملف'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default ProviderContractImportDialog;
