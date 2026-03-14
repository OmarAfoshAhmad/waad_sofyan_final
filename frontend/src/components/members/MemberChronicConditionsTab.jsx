/**
 * MemberChronicConditionsTab
 * تبويب الأمراض المزمنة للعضو
 *
 * يعرض ويدير الأمراض المزمنة المسجلة للعضو مع دعم:
 * - إضافة/تعديل/حذف الأمراض المزمنة
 * - تحديث حالة التغطية
 * - التحقق من المستندات
 * - عرض الإحصائيات
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid,
  Alert,
  CircularProgress,
  Tooltip,
  Collapse,
  Stack,
  LinearProgress,
  Divider
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  VerifiedUser as VerifyIcon,
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  LocalHospital as MedicalIcon,
  Warning as WarningIcon,
  CheckCircle as CheckIcon,
  AccessTime as WaitingIcon,
  Block as BlockIcon
} from '@mui/icons-material';
import { DatePicker, LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';
import chronicConditionsService from 'services/chronic-conditions.service';
import { openSnackbar } from 'api/snackbar';

// Coverage status colors and icons
const COVERAGE_STATUS_CONFIG = {
  COVERED: { color: 'success', icon: <CheckIcon />, label: 'مغطى' },
  EXCLUDED: { color: 'error', icon: <BlockIcon />, label: 'مستثنى' },
  WAITING_PERIOD: { color: 'warning', icon: <WaitingIcon />, label: 'فترة انتظار' },
  PARTIAL: { color: 'info', icon: <CheckIcon />, label: 'تغطية جزئية' },
  PENDING_REVIEW: { color: 'default', icon: <WaitingIcon />, label: 'قيد المراجعة' },
  COVERED_AFTER_WAITING: { color: 'success', icon: <CheckIcon />, label: 'مغطى بعد الانتظار' },
  REQUIRES_PRE_APPROVAL: { color: 'warning', icon: <VerifyIcon />, label: 'يتطلب موافقة مسبقة' },
  LIMITED: { color: 'info', icon: <WarningIcon />, label: 'تغطية محدودة' }
};

// Severity level colors
const SEVERITY_COLORS = {
  1: 'success',
  2: 'info',
  3: 'warning',
  4: 'error',
  5: 'error'
};

const SEVERITY_LABELS = {
  1: 'خفيف',
  2: 'متوسط',
  3: 'معتدل',
  4: 'شديد',
  5: 'حرج'
};

const MemberChronicConditionsTab = ({ memberId, readOnly = false }) => {
  // State
  const [conditions, setConditions] = useState([]);
  const [conditionTypes, setConditionTypes] = useState([]);
  const [coverageStatuses, setCoverageStatuses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCondition, setEditingCondition] = useState(null);
  const [expandedRow, setExpandedRow] = useState(null);
  const [deleteDialog, setDeleteDialog] = useState({ open: false, condition: null });
  const [statusDialog, setStatusDialog] = useState({ open: false, condition: null, newStatus: '', reason: '' });

  // Snackbar helpers
  const showSuccess = (message) => openSnackbar({ open: true, message, variant: 'alert', alert: { color: 'success' } });
  const showError = (message) => openSnackbar({ open: true, message, variant: 'alert', alert: { color: 'error' } });

  // Form state
  const [formData, setFormData] = useState({
    conditionType: '',
    customConditionName: '',
    icd10Code: '',
    diagnosisDate: null,
    disclosureDate: null,
    severityLevel: 3,
    coverageStatus: 'PENDING_REVIEW',
    waitingPeriodDays: 0,
    coveragePercentage: null,
    annualLimit: null,
    coverageReason: '',
    diagnosingPhysician: '',
    diagnosingFacility: '',
    currentMedications: '',
    treatmentPlan: '',
    notes: ''
  });

  // Load data
  const loadData = useCallback(async () => {
    if (!memberId) return;

    setLoading(true);
    try {
      const [conditionsRes, typesRes, statusesRes] = await Promise.all([
        chronicConditionsService.getConditionsByMemberId(memberId),
        chronicConditionsService.getConditionTypes(),
        chronicConditionsService.getCoverageStatuses()
      ]);

      setConditions(conditionsRes.data || []);
      setConditionTypes(typesRes.data || []);
      setCoverageStatuses(statusesRes.data || []);
    } catch (error) {
      console.error('Failed to load chronic conditions:', error);
      showError('فشل تحميل بيانات الأمراض المزمنة');
    } finally {
      setLoading(false);
    }
  }, [memberId, showError]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Reset form
  const resetForm = () => {
    setFormData({
      conditionType: '',
      customConditionName: '',
      icd10Code: '',
      diagnosisDate: null,
      disclosureDate: null,
      severityLevel: 3,
      coverageStatus: 'PENDING_REVIEW',
      waitingPeriodDays: 0,
      coveragePercentage: null,
      annualLimit: null,
      coverageReason: '',
      diagnosingPhysician: '',
      diagnosingFacility: '',
      currentMedications: '',
      treatmentPlan: '',
      notes: ''
    });
    setEditingCondition(null);
  };

  // Handle dialog open for add
  const handleAddClick = () => {
    resetForm();
    setDialogOpen(true);
  };

  // Handle dialog open for edit
  const handleEditClick = (condition) => {
    setEditingCondition(condition);
    setFormData({
      conditionType: condition.conditionType || '',
      customConditionName: condition.customConditionName || '',
      icd10Code: condition.icd10Code || '',
      diagnosisDate: condition.diagnosisDate ? dayjs(condition.diagnosisDate) : null,
      disclosureDate: condition.disclosureDate ? dayjs(condition.disclosureDate) : null,
      severityLevel: condition.severityLevel || 3,
      coverageStatus: condition.coverageStatus || 'PENDING_REVIEW',
      waitingPeriodDays: condition.waitingPeriodDays || 0,
      coveragePercentage: condition.coveragePercentage || null,
      annualLimit: condition.annualLimit || null,
      coverageReason: condition.coverageReason || '',
      diagnosingPhysician: condition.diagnosingPhysician || '',
      diagnosingFacility: condition.diagnosingFacility || '',
      currentMedications: condition.currentMedications || '',
      treatmentPlan: condition.treatmentPlan || '',
      notes: condition.notes || ''
    });
    setDialogOpen(true);
  };

  // Handle condition type change
  const handleConditionTypeChange = (value) => {
    setFormData((prev) => ({ ...prev, conditionType: value }));

    // Auto-fill ICD-10 and waiting period from selected type
    const selectedType = conditionTypes.find((t) => t.value === value);
    if (selectedType) {
      setFormData((prev) => ({
        ...prev,
        conditionType: value,
        icd10Code: selectedType.icd10Code || prev.icd10Code,
        waitingPeriodDays: selectedType.defaultWaitingPeriodDays || prev.waitingPeriodDays
      }));
    }
  };

  // Handle form submit
  const handleSubmit = async () => {
    try {
      const payload = {
        ...formData,
        memberId,
        diagnosisDate: formData.diagnosisDate?.format('YYYY-MM-DD'),
        disclosureDate: formData.disclosureDate?.format('YYYY-MM-DD')
      };

      if (editingCondition) {
        await chronicConditionsService.updateCondition(editingCondition.id, payload);
        showSuccess('تم تحديث المرض المزمن بنجاح');
      } else {
        await chronicConditionsService.createCondition(payload);
        showSuccess('تم إضافة المرض المزمن بنجاح');
      }

      setDialogOpen(false);
      resetForm();
      loadData();
    } catch (error) {
      console.error('Failed to save condition:', error);
      showError(error.response?.data?.message || 'فشل حفظ المرض المزمن');
    }
  };

  // Handle delete
  const handleDelete = async () => {
    if (!deleteDialog.condition) return;

    try {
      await chronicConditionsService.deleteCondition(deleteDialog.condition.id);
      showSuccess('تم حذف المرض المزمن بنجاح');
      setDeleteDialog({ open: false, condition: null });
      loadData();
    } catch (error) {
      console.error('Failed to delete condition:', error);
      showError('فشل حذف المرض المزمن');
    }
  };

  // Handle coverage status update
  const handleUpdateCoverageStatus = async () => {
    if (!statusDialog.condition || !statusDialog.newStatus) return;

    try {
      await chronicConditionsService.updateCoverageStatus(statusDialog.condition.id, statusDialog.newStatus, statusDialog.reason);
      showSuccess('تم تحديث حالة التغطية بنجاح');
      setStatusDialog({ open: false, condition: null, newStatus: '', reason: '' });
      loadData();
    } catch (error) {
      console.error('Failed to update coverage status:', error);
      showError(error.response?.data?.message || 'فشل تحديث حالة التغطية');
    }
  };

  // Handle verify documentation
  const handleVerifyDocumentation = async (condition) => {
    try {
      await chronicConditionsService.verifyDocumentation(condition.id);
      showSuccess('تم التحقق من المستندات بنجاح');
      loadData();
    } catch (error) {
      console.error('Failed to verify documentation:', error);
      showError(error.response?.data?.message || 'فشل التحقق من المستندات');
    }
  };

  // Render loading
  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        {/* Header */}
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <MedicalIcon color="primary" />
            الأمراض المزمنة ({conditions.length})
          </Typography>
          {!readOnly && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={handleAddClick} size="small">
              إضافة مرض مزمن
            </Button>
          )}
        </Box>

        {/* Conditions Table */}
        {conditions.length === 0 ? (
          <Alert severity="info" icon={<MedicalIcon />}>
            لا توجد أمراض مزمنة مسجلة لهذا العضو
          </Alert>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.100' }}>
                  <TableCell width={30} />
                  <TableCell>المرض</TableCell>
                  <TableCell>تاريخ التشخيص</TableCell>
                  <TableCell>الشدة</TableCell>
                  <TableCell>حالة التغطية</TableCell>
                  <TableCell>الحد السنوي</TableCell>
                  <TableCell>المستخدم</TableCell>
                  <TableCell align="center">إجراءات</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {conditions.map((condition) => (
                  <React.Fragment key={condition.id}>
                    <TableRow hover>
                      <TableCell>
                        <IconButton size="small" onClick={() => setExpandedRow(expandedRow === condition.id ? null : condition.id)}>
                          {expandedRow === condition.id ? <CollapseIcon /> : <ExpandIcon />}
                        </IconButton>
                      </TableCell>
                      <TableCell>
                        <Box>
                          <Typography variant="body2" fontWeight="bold">
                            {condition.conditionTypeNameAr}
                          </Typography>
                          {condition.customConditionName && (
                            <Typography variant="caption" color="text.secondary">
                              {condition.customConditionName}
                            </Typography>
                          )}
                          {condition.icd10Code && (
                            <Typography variant="caption" display="block" color="primary">
                              ICD-10: {condition.icd10Code}
                            </Typography>
                          )}
                        </Box>
                      </TableCell>
                      <TableCell>{condition.diagnosisDate || '-'}</TableCell>
                      <TableCell>
                        <Chip
                          label={SEVERITY_LABELS[condition.severityLevel] || condition.severityLevel}
                          color={SEVERITY_COLORS[condition.severityLevel] || 'default'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          icon={COVERAGE_STATUS_CONFIG[condition.coverageStatus]?.icon}
                          label={COVERAGE_STATUS_CONFIG[condition.coverageStatus]?.label || condition.coverageStatus}
                          color={COVERAGE_STATUS_CONFIG[condition.coverageStatus]?.color || 'default'}
                          size="small"
                          onClick={
                            !readOnly
                              ? () =>
                                  setStatusDialog({
                                    open: true,
                                    condition,
                                    newStatus: condition.coverageStatus,
                                    reason: ''
                                  })
                              : undefined
                          }
                          sx={{ cursor: readOnly ? 'default' : 'pointer' }}
                        />
                      </TableCell>
                      <TableCell>{condition.annualLimit ? `${Number(condition.annualLimit).toLocaleString()} ر.س` : '-'}</TableCell>
                      <TableCell>
                        {condition.annualLimit ? (
                          <Box>
                            <Typography variant="caption">{Number(condition.usedAmount || 0).toLocaleString()} ر.س</Typography>
                            <LinearProgress
                              variant="determinate"
                              value={Math.min(((condition.usedAmount || 0) / condition.annualLimit) * 100, 100)}
                              color={(condition.usedAmount || 0) / condition.annualLimit > 0.8 ? 'error' : 'primary'}
                              sx={{ mt: 0.5, height: '0.375rem', borderRadius: 1 }}
                            />
                          </Box>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="row" spacing={0.5} justifyContent="center">
                          {!readOnly && (
                            <>
                              <Tooltip title="تعديل">
                                <IconButton size="small" onClick={() => handleEditClick(condition)}>
                                  <EditIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              {condition.documentationPath && !condition.documentationVerified && (
                                <Tooltip title="التحقق من المستندات">
                                  <IconButton size="small" color="warning" onClick={() => handleVerifyDocumentation(condition)}>
                                    <VerifyIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              )}
                              <Tooltip title="حذف">
                                <IconButton size="small" color="error" onClick={() => setDeleteDialog({ open: true, condition })}>
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>

                    {/* Expanded Details */}
                    <TableRow>
                      <TableCell colSpan={8} sx={{ py: 0 }}>
                        <Collapse in={expandedRow === condition.id} timeout="auto" unmountOnExit>
                          <Box sx={{ p: '1.0rem', bgcolor: 'grey.50' }}>
                            <Grid container spacing={2}>
                              <Grid size={{ xs: 12, md: 6 }}>
                                <Card variant="outlined">
                                  <CardContent>
                                    <Typography variant="subtitle2" color="primary" gutterBottom>
                                      معلومات التغطية
                                    </Typography>
                                    <Grid container spacing={1}>
                                      <Grid size={6}>
                                        <Typography variant="caption" color="text.secondary">
                                          نسبة التغطية:
                                        </Typography>
                                        <Typography variant="body2">
                                          {condition.coveragePercentage ? `${condition.coveragePercentage}%` : 'غير محدد'}
                                        </Typography>
                                      </Grid>
                                      <Grid size={6}>
                                        <Typography variant="caption" color="text.secondary">
                                          فترة الانتظار:
                                        </Typography>
                                        <Typography variant="body2">
                                          {condition.waitingPeriodDays ? `${condition.waitingPeriodDays} يوم` : 'لا يوجد'}
                                        </Typography>
                                      </Grid>
                                      {condition.waitingPeriodEndDate && (
                                        <Grid size={12}>
                                          <Typography variant="caption" color="text.secondary">
                                            تنتهي في:
                                          </Typography>
                                          <Typography variant="body2" color={condition.waitingPeriodOver ? 'success.main' : 'warning.main'}>
                                            {condition.waitingPeriodEndDate}
                                            {condition.waitingPeriodOver && ' ✓'}
                                          </Typography>
                                        </Grid>
                                      )}
                                      {condition.coverageReason && (
                                        <Grid size={12}>
                                          <Typography variant="caption" color="text.secondary">
                                            سبب حالة التغطية:
                                          </Typography>
                                          <Typography variant="body2">{condition.coverageReason}</Typography>
                                        </Grid>
                                      )}
                                    </Grid>
                                  </CardContent>
                                </Card>
                              </Grid>

                              <Grid size={{ xs: 12, md: 6 }}>
                                <Card variant="outlined">
                                  <CardContent>
                                    <Typography variant="subtitle2" color="primary" gutterBottom>
                                      معلومات التشخيص
                                    </Typography>
                                    <Grid container spacing={1}>
                                      <Grid size={6}>
                                        <Typography variant="caption" color="text.secondary">
                                          الطبيب:
                                        </Typography>
                                        <Typography variant="body2">{condition.diagnosingPhysician || '-'}</Typography>
                                      </Grid>
                                      <Grid size={6}>
                                        <Typography variant="caption" color="text.secondary">
                                          المنشأة:
                                        </Typography>
                                        <Typography variant="body2">{condition.diagnosingFacility || '-'}</Typography>
                                      </Grid>
                                      <Grid size={12}>
                                        <Typography variant="caption" color="text.secondary">
                                          تاريخ الإفصاح:
                                        </Typography>
                                        <Typography variant="body2">{condition.disclosureDate || '-'}</Typography>
                                      </Grid>
                                    </Grid>
                                  </CardContent>
                                </Card>
                              </Grid>

                              {(condition.currentMedications || condition.treatmentPlan) && (
                                <Grid size={12}>
                                  <Card variant="outlined">
                                    <CardContent>
                                      <Typography variant="subtitle2" color="primary" gutterBottom>
                                        معلومات العلاج
                                      </Typography>
                                      <Grid container spacing={2}>
                                        {condition.currentMedications && (
                                          <Grid size={{ xs: 12, md: 6 }}>
                                            <Typography variant="caption" color="text.secondary">
                                              الأدوية الحالية:
                                            </Typography>
                                            <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
                                              {condition.currentMedications}
                                            </Typography>
                                          </Grid>
                                        )}
                                        {condition.treatmentPlan && (
                                          <Grid size={{ xs: 12, md: 6 }}>
                                            <Typography variant="caption" color="text.secondary">
                                              خطة العلاج:
                                            </Typography>
                                            <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>
                                              {condition.treatmentPlan}
                                            </Typography>
                                          </Grid>
                                        )}
                                      </Grid>
                                    </CardContent>
                                  </Card>
                                </Grid>
                              )}

                              {condition.notes && (
                                <Grid size={12}>
                                  <Typography variant="caption" color="text.secondary">
                                    ملاحظات:
                                  </Typography>
                                  <Typography variant="body2">{condition.notes}</Typography>
                                </Grid>
                              )}

                              <Grid size={12}>
                                <Divider />
                                <Box display="flex" gap={2} mt={1}>
                                  <Typography variant="caption" color="text.secondary">
                                    تم الإنشاء: {condition.createdAt} بواسطة {condition.createdBy || 'النظام'}
                                  </Typography>
                                  {condition.verifiedAt && (
                                    <Chip
                                      icon={<VerifyIcon />}
                                      label={`تم التحقق ${condition.verifiedAt} بواسطة ${condition.verifiedBy}`}
                                      size="small"
                                      color="success"
                                      variant="outlined"
                                    />
                                  )}
                                </Box>
                              </Grid>
                            </Grid>
                          </Box>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </React.Fragment>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {/* Add/Edit Dialog */}
        <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
          <DialogTitle>{editingCondition ? 'تعديل المرض المزمن' : 'إضافة مرض مزمن جديد'}</DialogTitle>
          <DialogContent dividers>
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid size={{ xs: 12, md: 6 }}>
                <FormControl fullWidth required>
                  <InputLabel>نوع المرض المزمن</InputLabel>
                  <Select
                    value={formData.conditionType}
                    label="نوع المرض المزمن"
                    onChange={(e) => handleConditionTypeChange(e.target.value)}
                    disabled={!!editingCondition}
                  >
                    {conditionTypes.map((type) => (
                      <MenuItem key={type.value} value={type.value}>
                        {type.nameAr} ({type.code})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>

              {formData.conditionType === 'OTHER' && (
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    required
                    label="اسم المرض المزمن"
                    value={formData.customConditionName}
                    onChange={(e) => setFormData((prev) => ({ ...prev, customConditionName: e.target.value }))}
                  />
                </Grid>
              )}

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="كود ICD-10"
                  value={formData.icd10Code}
                  onChange={(e) => setFormData((prev) => ({ ...prev, icd10Code: e.target.value }))}
                  placeholder="مثال: E11.9"
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <DatePicker
                  label="تاريخ التشخيص"
                  value={formData.diagnosisDate}
                  onChange={(value) => setFormData((prev) => ({ ...prev, diagnosisDate: value }))}
                  slotProps={{ textField: { fullWidth: true } }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <DatePicker
                  label="تاريخ الإفصاح"
                  value={formData.disclosureDate}
                  onChange={(value) => setFormData((prev) => ({ ...prev, disclosureDate: value }))}
                  slotProps={{ textField: { fullWidth: true } }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <FormControl fullWidth>
                  <InputLabel>مستوى الشدة</InputLabel>
                  <Select
                    value={formData.severityLevel}
                    label="مستوى الشدة"
                    onChange={(e) => setFormData((prev) => ({ ...prev, severityLevel: e.target.value }))}
                  >
                    <MenuItem value={1}>1 - خفيف</MenuItem>
                    <MenuItem value={2}>2 - متوسط</MenuItem>
                    <MenuItem value={3}>3 - معتدل</MenuItem>
                    <MenuItem value={4}>4 - شديد</MenuItem>
                    <MenuItem value={5}>5 - حرج</MenuItem>
                  </Select>
                </FormControl>
              </Grid>

              <Grid size={12}>
                <Divider sx={{ my: 1 }}>معلومات التغطية</Divider>
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <FormControl fullWidth>
                  <InputLabel>حالة التغطية</InputLabel>
                  <Select
                    value={formData.coverageStatus}
                    label="حالة التغطية"
                    onChange={(e) => setFormData((prev) => ({ ...prev, coverageStatus: e.target.value }))}
                  >
                    {coverageStatuses.map((status) => (
                      <MenuItem key={status.value} value={status.value}>
                        {status.labelAr}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  type="number"
                  label="فترة الانتظار (أيام)"
                  value={formData.waitingPeriodDays}
                  onChange={(e) => setFormData((prev) => ({ ...prev, waitingPeriodDays: Number(e.target.value) }))}
                  InputProps={{ inputProps: { min: 0 } }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  type="number"
                  label="نسبة التغطية %"
                  value={formData.coveragePercentage || ''}
                  onChange={(e) => setFormData((prev) => ({ ...prev, coveragePercentage: e.target.value ? Number(e.target.value) : null }))}
                  InputProps={{ inputProps: { min: 0, max: 100 } }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  type="number"
                  label="الحد السنوي (ر.س)"
                  value={formData.annualLimit || ''}
                  onChange={(e) => setFormData((prev) => ({ ...prev, annualLimit: e.target.value ? Number(e.target.value) : null }))}
                  InputProps={{ inputProps: { min: 0 } }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="سبب حالة التغطية"
                  value={formData.coverageReason}
                  onChange={(e) => setFormData((prev) => ({ ...prev, coverageReason: e.target.value }))}
                  placeholder="مثال: حالة سابقة للتأمين"
                />
              </Grid>

              <Grid size={12}>
                <Divider sx={{ my: 1 }}>معلومات التشخيص</Divider>
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="الطبيب المشخص"
                  value={formData.diagnosingPhysician}
                  onChange={(e) => setFormData((prev) => ({ ...prev, diagnosingPhysician: e.target.value }))}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="منشأة التشخيص"
                  value={formData.diagnosingFacility}
                  onChange={(e) => setFormData((prev) => ({ ...prev, diagnosingFacility: e.target.value }))}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  multiline
                  rows={2}
                  label="الأدوية الحالية"
                  value={formData.currentMedications}
                  onChange={(e) => setFormData((prev) => ({ ...prev, currentMedications: e.target.value }))}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  multiline
                  rows={2}
                  label="خطة العلاج"
                  value={formData.treatmentPlan}
                  onChange={(e) => setFormData((prev) => ({ ...prev, treatmentPlan: e.target.value }))}
                />
              </Grid>

              <Grid size={12}>
                <TextField
                  fullWidth
                  multiline
                  rows={2}
                  label="ملاحظات"
                  value={formData.notes}
                  onChange={(e) => setFormData((prev) => ({ ...prev, notes: e.target.value }))}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)}>إلغاء</Button>
            <Button variant="contained" onClick={handleSubmit} disabled={!formData.conditionType}>
              {editingCondition ? 'تحديث' : 'إضافة'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Delete Confirmation Dialog */}
        <Dialog open={deleteDialog.open} onClose={() => setDeleteDialog({ open: false, condition: null })}>
          <DialogTitle>تأكيد الحذف</DialogTitle>
          <DialogContent>
            <Typography>
              هل أنت متأكد من حذف هذا المرض المزمن؟
              <br />
              <strong>{deleteDialog.condition?.conditionTypeNameAr}</strong>
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteDialog({ open: false, condition: null })}>إلغاء</Button>
            <Button color="error" variant="contained" onClick={handleDelete}>
              حذف
            </Button>
          </DialogActions>
        </Dialog>

        {/* Coverage Status Update Dialog */}
        <Dialog
          open={statusDialog.open}
          onClose={() => setStatusDialog({ open: false, condition: null, newStatus: '', reason: '' })}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>تحديث حالة التغطية</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid size={12}>
                <FormControl fullWidth>
                  <InputLabel>حالة التغطية الجديدة</InputLabel>
                  <Select
                    value={statusDialog.newStatus}
                    label="حالة التغطية الجديدة"
                    onChange={(e) => setStatusDialog((prev) => ({ ...prev, newStatus: e.target.value }))}
                  >
                    {coverageStatuses.map((status) => (
                      <MenuItem key={status.value} value={status.value}>
                        {status.labelAr}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={12}>
                <TextField
                  fullWidth
                  multiline
                  rows={2}
                  label="سبب التغيير"
                  value={statusDialog.reason}
                  onChange={(e) => setStatusDialog((prev) => ({ ...prev, reason: e.target.value }))}
                  placeholder="أدخل سبب تغيير حالة التغطية"
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setStatusDialog({ open: false, condition: null, newStatus: '', reason: '' })}>إلغاء</Button>
            <Button variant="contained" onClick={handleUpdateCoverageStatus} disabled={!statusDialog.newStatus}>
              تحديث
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </LocalizationProvider>
  );
};

export default MemberChronicConditionsTab;
