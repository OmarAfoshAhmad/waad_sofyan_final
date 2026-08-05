/**
 * EmployerImportDialog
 * ═══════════════════════════════════════════════════════════════════════════
 * Two-stage bulk import of employers (جهات العمل):
 *   1) Preview: upload the file — columns are detected automatically regardless
 *      of order or exact wording, every row is matched against existing
 *      employers, and a full "what will happen" status report is shown
 *      (سيُنشأ / سيُحدَّث مع تفاصيل الحقول المتغيرة / لا تغيير) before anything
 *      is written.
 *   2) Confirm: persist only the valid rows — new employers are created, existing
 *      ones are merged (blank cells never erase existing data), and each ends up
 *      with exactly one ACTIVE insurance policy.
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
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import EditNoteIcon from '@mui/icons-material/EditNote';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';

import {
  downloadEmployerImportTemplate,
  previewEmployerImport,
  confirmEmployerImport,
  downloadEmployerImportErrors
} from 'services/api/employers.service';
import { useSnackbar } from 'notistack';

const ACTION_CONFIG = {
  CREATE: { label: 'إنشاء جديد', color: 'success', icon: <AddCircleOutlineIcon fontSize="small" /> },
  UPDATE: { label: 'تحديث', color: 'info', icon: <EditNoteIcon fontSize="small" /> },
  NO_CHANGE: { label: 'بدون تغيير', color: 'default', icon: <RemoveCircleOutlineIcon fontSize="small" /> }
};

const ActionChip = ({ action }) => {
  const cfg = ACTION_CONFIG[action];
  if (!cfg) return <Chip size="small" label="-" />;
  return <Chip size="small" color={cfg.color} label={cfg.label} icon={cfg.icon} />;
};

const EmployerImportDialog = ({ open, onClose, onImportComplete }) => {
  const { enqueueSnackbar } = useSnackbar();
  const fileInputRef = useRef(null);

  const [selectedFile, setSelectedFile] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [preview, setPreview] = useState(null); // EmployerImportPreviewResultDto
  const [confirmResult, setConfirmResult] = useState(null); // EmployerImportConfirmResultDto
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

  const validateAndSetFile = (file) => {
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

  const handleFileChange = (e) => validateAndSetFile(e.target.files?.[0]);

  const handlePreview = async () => {
    if (!selectedFile) return;
    setPreviewLoading(true);
    setError(null);
    try {
      const result = await previewEmployerImport(selectedFile);
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
      const result = await confirmEmployerImport(preview.sessionId);
      setConfirmResult(result);
      enqueueSnackbar(`تمت معالجة ${result.successCount} من ${result.totalRows} جهة عمل بنجاح`, {
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

  const createCount = preview?.rows?.filter((r) => r.action === 'CREATE').length ?? 0;
  const updateCount = preview?.rows?.filter((r) => r.action === 'UPDATE').length ?? 0;
  const noChangeCount = preview?.rows?.filter((r) => r.action === 'NO_CHANGE').length ?? 0;

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
            استيراد جهات العمل
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.85 }}>
            اطّلع على ما سيحدث قبل الحفظ، ثم أكِّد استيراد الصفوف الصحيحة فقط
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
              <Button size="small" startIcon={<FileDownloadIcon />} onClick={() => downloadEmployerImportTemplate()}>
                تنزيل القالب
              </Button>
            }
          >
            حمّل القالب أولاً (رمز الجهة، اسم الجهة، رقم الهاتف، البريد الإلكتروني، العنوان، نسبة التغطية — الأعمدة الاختيارية يمكن تركها
            فارغة)، ثم عبّئه وارفعه أدناه. ترتيب الأعمدة وأسماؤها ليست مهمة — سيتم التعرف عليها تلقائياً. كل جهة عمل جديدة بلا وثيقة تأمين
            تحصل على وثيقة <strong>كمسودة</strong> بنسبة التغطية المحدَّدة (أو 100% افتراضياً) دون قواعد تغطية — يجب إضافتها وتفعيل الوثيقة
            يدوياً لاحقاً.
          </Alert>
        )}

        {/* Step 1: upload */}
        {!preview && !confirmResult && (
          <>
            <Box
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                validateAndSetFile(e.dataTransfer.files?.[0]);
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

        {/* Step 2: pre-import status report */}
        {preview && !confirmResult && (
          <>
            <Stack direction="row" spacing={1.5} flexWrap="wrap" mb={1.5} useFlexGap>
              <Chip label={`الإجمالي: ${preview.totalRows}`} variant="outlined" />
              <Chip color="success" label={`صالح: ${preview.validCount}`} icon={<CheckCircleIcon />} />
              <Chip color="error" label={`به أخطاء: ${preview.invalidCount}`} icon={<ErrorIcon />} />
            </Stack>

            <Stack direction="row" spacing={1.5} flexWrap="wrap" mb={2} useFlexGap>
              <Chip color="success" variant="outlined" label={`سيُنشأ: ${createCount}`} icon={<AddCircleOutlineIcon />} />
              <Chip color="info" variant="outlined" label={`سيُحدَّث: ${updateCount}`} icon={<EditNoteIcon />} />
              <Chip color="default" variant="outlined" label={`بدون تغيير: ${noChangeCount}`} icon={<RemoveCircleOutlineIcon />} />
            </Stack>

            {preview.invalidCount > 0 && (
              <Alert
                severity="warning"
                sx={{ mb: 2 }}
                action={
                  <Button size="small" startIcon={<FileDownloadIcon />} onClick={() => downloadEmployerImportErrors(preview.sessionId)}>
                    تنزيل تقرير الأخطاء
                  </Button>
                }
              >
                الصفوف التي بها أخطاء لن تُحفظ عند التأكيد. صحِّحها في الملف الأصلي وأعد رفعه إذا رغبت.
              </Alert>
            )}

            <Divider sx={{ mb: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                تقرير الوضع — ماذا سيحدث لكل صف
              </Typography>
            </Divider>

            <Box sx={{ maxHeight: 340, overflowY: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>الصف</TableCell>
                    <TableCell>جهة العمل</TableCell>
                    <TableCell>الرمز</TableCell>
                    <TableCell align="center">الإجراء</TableCell>
                    <TableCell>الحقول المتغيرة / الأخطاء</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.rows.map((row) => (
                    <TableRow key={row.rowNumber} hover>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.name || row.nameRaw || '-'}</TableCell>
                      <TableCell>{row.code || '-'}</TableCell>
                      <TableCell align="center">
                        {row.errors?.length ? <Chip size="small" color="error" label="خطأ" /> : <ActionChip action={row.action} />}
                      </TableCell>
                      <TableCell>
                        {(row.errors || []).length > 0 ? (
                          row.errors.map((err, i) => (
                            <Typography key={i} variant="caption" display="block" color="error.main">
                              • {err}
                            </Typography>
                          ))
                        ) : row.action === 'UPDATE' && row.changedFields?.length > 0 ? (
                          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                            {row.changedFields.map((f) => (
                              <Chip key={f} size="small" variant="outlined" label={f} />
                            ))}
                          </Stack>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            {row.action === 'NO_CHANGE' ? 'لا يوجد تغيير — لن يُلمس السجل' : '—'}
                          </Typography>
                        )}
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

            <Box sx={{ maxHeight: 340, overflowY: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>الصف</TableCell>
                    <TableCell>جهة العمل</TableCell>
                    <TableCell>الرمز</TableCell>
                    <TableCell align="center">الإجراء</TableCell>
                    <TableCell>وثيقة التأمين (مسودة)</TableCell>
                    <TableCell align="center">النتيجة</TableCell>
                    <TableCell>الرسالة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {confirmResult.results.map((row) => (
                    <TableRow key={row.rowNumber} hover>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.employerName || '-'}</TableCell>
                      <TableCell>{row.employerCode || '-'}</TableCell>
                      <TableCell align="center">{row.action ? <ActionChip action={row.action} /> : '-'}</TableCell>
                      <TableCell>{row.policyCode || '-'}</TableCell>
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

export default EmployerImportDialog;
