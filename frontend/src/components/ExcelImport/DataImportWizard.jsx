import { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Step,
  StepLabel,
  Stepper,
  Typography,
  Paper,
  Alert,
  CircularProgress,
  Grid,
  Divider,
  Autocomplete,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip
} from '@mui/material';
import { CloudUpload, Code, Business, CheckCircle, Warning, Error as ErrorIcon } from '@mui/icons-material';
import { useDropzone } from 'react-dropzone';
import { useSnackbar } from 'notistack';
import axios from 'utils/axios';
import { useImportProgress } from 'contexts/GlobalImportProgressContext';
import employersService from 'services/api/employers.service';

// Steps
const steps = ['رفع الملف', 'التحليل والمطابقة', 'التنفيذ'];

const DataImportWizard = ({
  open,
  onClose,
  baseApiUrl = '/api/v1/unified-members/import',
  entityName = 'المستفيدين',
  hideContextSelectors = false
}) => {
  const { enqueueSnackbar } = useSnackbar();
  const [activeStep, setActiveStep] = useState(0);
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Analysis State
  const [previewData, setPreviewData] = useState(null);
  const [allEmployers, setAllEmployers] = useState([]);
  const [selectedEmployer, setSelectedEmployer] = useState(null); // { id, nameAr }
  const [selectedPolicy, setSelectedPolicy] = useState(null); // { id, policyNumber }

  const { startImport } = useImportProgress();

  // Reset on open
  useEffect(() => {
    if (open) {
      setActiveStep(0);
      setFile(null);
      setPreviewData(null);
      setSelectedEmployer(null);
      setSelectedPolicy(null);
      setError(null);
      fetchEmployers();
    }
  }, [open]);

  const fetchEmployers = async () => {
    try {
      const selectors = await employersService.getEmployerSelectors();
      // Map selector labels to nameAr for component compatibility
      const mapped = (selectors || []).map((s) => ({
        id: s.id,
        nameAr: s.label || s.nameAr,
        code: s.code
      }));
      setAllEmployers(mapped);
    } catch (err) {
      console.error('Failed to fetch employers list', err);
    }
  };

  // File Drop
  const onDrop = (acceptedFiles) => {
    if (acceptedFiles?.length > 0) {
      setFile(acceptedFiles[0]);
      setError(null);
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
      'application/vnd.ms-excel': ['.xls']
    },
    multiple: false
  });

  // Actions
  const handleAnalyze = async () => {
    if (!file) return;
    setLoading(true);
    setError(null);

    const formData = new FormData();
    formData.append('file', file);
    if (selectedEmployer) formData.append('employerId', selectedEmployer.id);

    try {
      // Using parameterized baseApiUrl
      const response = await axios.post(`${baseApiUrl}/preview`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      // NEW: Extract data from ApiResponse wrapper correctly
      const data = response.data?.data || response.data?.result || response.data;
      setPreviewData(data);
      console.log(`📊 Import ${entityName} Preview Data (Unwrapped):`, data);
      setActiveStep(1);
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.message || err.message || 'فشل تحليل الملف');
    } finally {
      setLoading(false);
    }
  };

  const handleExecute = async () => {
    if (!file || !previewData) return;

    // Smart Context Validation
    // Smart Context Validation
    const hasEmployerColumn =
      (previewData?.columnMappings && Object.values(previewData.columnMappings).includes('employer')) ||
      previewData?.detectedColumns?.some((c) => {
        if (!c) return false;
        // NEW: Handle newlines and multiple spaces more aggressively
        const normalized = c
          .toLowerCase()
          .replace(/[\r\n]+/g, ' ')
          .replace(/\u00A0/g, ' ')
          .replace(/\u200B/g, ' ')
          .replace(/\*/g, '')
          .trim();
        return normalized.includes('employer') || normalized.includes('جهة العمل');
      });
    if (!selectedEmployer && !hasEmployerColumn) {
      setError("يرجى اختيار جهة العمل (أو التأكد من وجود عمود 'جهة العمل' في الملف)");
      return;
    }

    setLoading(true);
    const formData = new FormData();
    formData.append('file', file);
    if (selectedEmployer) formData.append('employerId', selectedEmployer.id);
    if (selectedPolicy) formData.append('benefitPolicyId', selectedPolicy.id);
    if (previewData.batchId) formData.append('batchId', previewData.batchId);

    // Default Policy
    formData.append('importPolicy', 'UPDATE');

    try {
      const response = await axios.post(`${baseApiUrl}/execute`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      const result = response.data?.data || response.data?.result || response.data;

      if (result.batchId) {
        // Start Background Monitoring for long-running imports
        startImport(result.batchId, file.name);
      } else {
        // For simple imports (like employers), just show success and close
        enqueueSnackbar(result.message || 'تم الاستيراد بنجاح', { variant: 'success' });
      }
      onClose(); // Close Wizard
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.message || err.message || 'فشل بدء الاستيراد');
      setLoading(false);
    }
  };

  const downloadTemplate = async () => {
    try {
      const response = await axios.get(`${baseApiUrl}/template`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${entityName}_Template.xlsx`);
      document.body.appendChild(link);
      link.click();
    } catch (err) {
      console.error('Template download failed', err);
    }
  };

  // Render Steps
  const renderStepContent = (step) => {
    switch (step) {
      case 0:
        return (
          <Box sx={{ mt: '1.0rem', textAlign: 'center' }}>
            <Box
              {...getRootProps()}
              sx={{
                border: '2px dashed',
                borderColor: isDragActive ? 'primary.main' : 'grey.400',
                borderRadius: '0.125rem',
                p: '2.0rem',
                cursor: 'pointer',
                bgcolor: isDragActive ? 'action.hover' : 'background.paper',
                transition: 'all 0.2s',
                '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' }
              }}
            >
              <input {...getInputProps()} />
              <CloudUpload sx={{ fontSize: '3.0rem', color: 'text.secondary', mb: 1 }} />
              <Typography variant="h6" gutterBottom>
                {isDragActive ? 'افلت الملف هنا' : 'اسحب وافلت ملف Excel هنا'}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                أو اضغط لاختيار ملف (.xlsx, .xls)
              </Typography>
              {file && (
                <Box sx={{ mt: '1.0rem', p: 1, bgcolor: 'primary.lighter', borderRadius: 1, display: 'inline-block' }}>
                  <Typography variant="subtitle2" color="primary.main">
                    {file.name}
                  </Typography>
                </Box>
              )}
            </Box>
            <Box sx={{ mt: '1.0rem', display: 'flex', justifyContent: 'center' }}>
              <Button variant="text" size="small" startIcon={<Code />} onClick={downloadTemplate}>
                تحميل النموذج القياسي
              </Button>
            </Box>
          </Box>
        );
      case 1:
        const hasEmployerCol =
          (previewData?.columnMappings && Object.values(previewData.columnMappings).includes('employer')) ||
          previewData?.detectedColumns?.some((c) => {
            if (!c) return false;
            const normalized = c
              .toLowerCase()
              .replace(/[\r\n]+/g, ' ')
              .replace(/\u00A0/g, ' ')
              .replace(/\u200B/g, ' ')
              .replace(/\*/g, '')
              .trim();
            return normalized.includes('employer') || normalized.includes('جهة العمل');
          });

        return (
          <Box sx={{ mt: '1.0rem' }}>
            {/* Smart Analysis Summary */}
            <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                  <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                    تحليل السياق (Smart Context)
                  </Typography>
                  {hideContextSelectors ? (
                    <Alert severity="success" icon={<CheckCircle fontSize="inherit" />}>
                      جاهز لاستيراد <b>{entityName}</b> بناءً على بيانات الملف.
                    </Alert>
                  ) : hasEmployerCol ? (
                    <Alert severity="info" icon={<CheckCircle fontSize="inherit" />}>
                      تم اكتشاف عمود <b>جهة العمل</b>. سيتم تحديد جهة العمل لكل صف تلقائياً من الملف (Multi-tenant).
                    </Alert>
                  ) : (
                    <Alert severity="warning" icon={<Warning fontSize="inherit" />}>
                      لم يتم العثور على عمود <b>جهة العمل</b>. يرجى تحديد جهة عمل موحدة لجميع السجلات أدناه.
                    </Alert>
                  )}
                </Paper>
              </Grid>
              {!hideContextSelectors && (
                <Grid size={{ xs: 12, md: 6 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                    <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                      إعدادات الاستيراد
                    </Typography>
                    <Autocomplete
                      options={allEmployers}
                      getOptionLabel={(option) => option.nameAr || option.code || ''}
                      value={selectedEmployer}
                      onChange={(_, newValue) => setSelectedEmployer(newValue)}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          label="جهة العمل الموحدة (اختياري)"
                          size="small"
                          fullWidth
                          helperText="اختر جهة فقط إذا كان الملف لا يحتوي على عمود 'جهة العمل'"
                        />
                      )}
                      sx={{ mb: '1.0rem' }}
                    />
                    {selectedEmployer && (
                      <Autocomplete
                        options={(previewData?.availableBenefitPolicies || []).filter((p) => p.employerId === selectedEmployer.id)}
                        getOptionLabel={(option) => option.nameAr || option.policyNumber || ''}
                        value={selectedPolicy}
                        onChange={(_, newValue) => setSelectedPolicy(newValue)}
                        renderInput={(params) => <TextField {...params} label="وثيقة التأمين (اختياري)" size="small" fullWidth />}
                        disabled={!selectedEmployer}
                      />
                    )}
                  </Paper>
                </Grid>
              )}
            </Grid>

            {/* Data Preview */}
            <Typography variant="subtitle2" gutterBottom>
              معاينة البيانات ({previewData?.totalRows} صفوف)
            </Typography>
            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '18.75rem' }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>#</TableCell>
                    <TableCell>{entityName === 'جهات العمل' ? 'الكود' : 'الاسم'}</TableCell>
                    <TableCell>{entityName === 'جهات العمل' ? 'الاسم' : 'رقم الهوية'}</TableCell>
                    {!hideContextSelectors && <TableCell>جهة العمل</TableCell>}
                    <TableCell>الحالة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {previewData?.previewRows?.map((row) => (
                    <TableRow key={row.rowNumber} hover>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{entityName === 'جهات العمل' ? row.code || '-' : row.fullName}</TableCell>
                      <TableCell>{entityName === 'جهات العمل' ? row.name : row.nationalNumber || '-'}</TableCell>
                      {!hideContextSelectors && <TableCell>{row.employerName || row.attributes?.employer || '-'}</TableCell>}
                      <TableCell>
                        <Chip
                          label={
                            row.status === 'NEW' ? 'جديد' : row.status === 'WARNING' ? 'تنبيه' : row.status === 'ERROR' ? 'خطأ' : row.status
                          }
                          size="small"
                          color={row.status === 'ERROR' ? 'error' : row.status === 'NEW' ? 'success' : 'warning'}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            {/* Errors Summary */}
            {previewData?.errorCount > 0 && (
              <Box sx={{ mt: '1.0rem' }}>
                <Typography color="error" variant="caption">
                  يوجد {previewData.errorCount} صفوف بها أخطاء حرجة لن يتم استيرادها.
                </Typography>
              </Box>
            )}
          </Box>
        );
      default:
        return null;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>استيراد {entityName} (معالج البيانات الذكي)</DialogTitle>
      <DialogContent dividers>
        <Stepper activeStep={activeStep} alternativeLabel>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {error && (
          <Alert severity="error" sx={{ mt: '1.0rem' }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: '2.5rem' }}>
            <CircularProgress />
          </Box>
        ) : (
          renderStepContent(activeStep)
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="inherit">
          إلغاء
        </Button>
        {activeStep === 0 && (
          <Button onClick={handleAnalyze} variant="contained" disabled={!file}>
            تحليل الملف
          </Button>
        )}
        {activeStep === 1 && (
          <Button
            onClick={handleExecute}
            variant="contained"
            color="primary"
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} color="inherit" /> : null}
          >
            {loading ? 'جاري التحضير...' : 'بدء الاستيراد'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default DataImportWizard;
