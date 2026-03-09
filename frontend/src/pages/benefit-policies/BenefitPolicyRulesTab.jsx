import { useState, useCallback, useMemo, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Category as CategoryIcon,
  MedicalServices as ServiceIcon,
  Search as SearchIcon,
  Clear as ClearIcon,
  Save as SaveIcon
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import MedicalServiceSelector from 'components/tba/MedicalServiceSelector';

import {
  getPolicyRules,
  createPolicyRule,
  updatePolicyRule,
  togglePolicyRuleActive,
  deletePolicyRule
} from 'services/api/benefit-policy-rules.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';

// ═══════════════════════════════════════════════════════════════════════════
// RULE FORM COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const INITIAL_FORM_STATE = {
  targetType: '', // 'CATEGORY' or 'SERVICE'
  medicalCategoryId: '',
  medicalServiceId: '',
  medicalServiceObject: null, // full object for display
  coveragePercent: '',
  amountLimit: '',
  timesLimit: '',
  waitingPeriodDays: '0',
  requiresPreApproval: false,
  notes: ''
};

/**
 * Rule Form Modal
 */
const RuleFormModal = ({ open, onClose, onSubmit, initialData, isEdit, loading, categories, loadingCategories }) => {
  const [formData, setFormData] = useState(INITIAL_FORM_STATE);
  const [errors, setErrors] = useState({});

  // Initialize form data when modal opens
  useEffect(() => {
    if (open) {
      if (isEdit && initialData) {
        setFormData({
          targetType: initialData.ruleType || '',
          medicalCategoryId: initialData.medicalCategoryId || '',
          medicalServiceId: initialData.medicalServiceId || '',
          medicalServiceObject: initialData.medicalServiceId
            ? { id: initialData.medicalServiceId, code: initialData.medicalServiceCode || '', name: initialData.medicalServiceName || '' }
            : null,
          coveragePercent: initialData.coveragePercent ?? '',
          amountLimit: initialData.amountLimit ?? '',
          timesLimit: initialData.timesLimit ?? '',
          waitingPeriodDays: initialData.waitingPeriodDays ?? '0',
          requiresPreApproval: initialData.requiresPreApproval || false,
          notes: initialData.notes || ''
        });
      } else {
        setFormData(INITIAL_FORM_STATE);
      }
      setErrors({});
    }
  }, [open, isEdit, initialData]);

  const handleChange = useCallback(
    (field) => (event) => {
      const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;

      setFormData((prev) => {
        const newData = { ...prev, [field]: value };

        // XOR logic: Clear the other field when targetType changes
        if (field === 'targetType') {
          if (value === 'CATEGORY') {
            newData.medicalServiceId = '';
            newData.medicalServiceObject = null;
          } else if (value === 'SERVICE') {
            newData.medicalCategoryId = '';
          }
        }

        // Clear service when category changes
        if (field === 'medicalCategoryId') {
          newData.medicalServiceId = '';
          newData.medicalServiceObject = null;
        }

        return newData;
      });

      // Clear error for this field
      setErrors((prev) => ({ ...prev, [field]: null }));
    },
    []
  );

  const validate = useCallback(() => {
    const newErrors = {};

    // Target type required
    if (!formData.targetType) {
      newErrors.targetType = 'يجب اختيار نوع العنصر';
    }

    // Category or Service based on type
    if (formData.targetType === 'CATEGORY' && !formData.medicalCategoryId) {
      newErrors.medicalCategoryId = 'يجب اختيار التصنيف الطبي';
    }
    if (formData.targetType === 'SERVICE' && !formData.medicalCategoryId) {
      newErrors.medicalCategoryId = 'يجب اختيار التصنيف أولاً';
    }
    if (formData.targetType === 'SERVICE' && !formData.medicalServiceId) {
      newErrors.medicalServiceId = 'يجب اختيار الخدمة الطبية';
    }

    // Coverage percent validation
    if (formData.coveragePercent !== '' && formData.coveragePercent !== null) {
      const coverage = Number(formData.coveragePercent);
      if (isNaN(coverage) || coverage < 0 || coverage > 100) {
        newErrors.coveragePercent = 'نسبة التغطية يجب أن تكون بين 0 و 100';
      }
    }

    // Amount limit validation
    if (formData.amountLimit !== '' && formData.amountLimit !== null) {
      const amount = Number(formData.amountLimit);
      if (isNaN(amount) || amount < 0) {
        newErrors.amountLimit = 'حد المبلغ يجب أن يكون رقم موجب';
      }
    }

    // Times limit validation
    if (formData.timesLimit !== '' && formData.timesLimit !== null) {
      const times = Number(formData.timesLimit);
      if (isNaN(times) || times < 0 || !Number.isInteger(times)) {
        newErrors.timesLimit = 'حد المرات يجب أن يكون رقم صحيح موجب';
      }
    }

    // Waiting period validation
    if (formData.waitingPeriodDays !== '' && formData.waitingPeriodDays !== null) {
      const days = Number(formData.waitingPeriodDays);
      if (isNaN(days) || days < 0 || !Number.isInteger(days)) {
        newErrors.waitingPeriodDays = 'فترة الانتظار يجب أن تكون رقم صحيح موجب';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(() => {
    if (!validate()) return;

    const payload = {
      medicalCategoryId: formData.targetType === 'CATEGORY' ? Number(formData.medicalCategoryId) : null,
      medicalServiceId: formData.targetType === 'SERVICE' ? Number(formData.medicalServiceId) : null,
      coveragePercent: formData.coveragePercent !== '' ? Number(formData.coveragePercent) : null,
      amountLimit: formData.amountLimit !== '' ? Number(formData.amountLimit) : null,
      timesLimit: formData.timesLimit !== '' ? Number(formData.timesLimit) : null,
      waitingPeriodDays: formData.waitingPeriodDays !== '' ? Number(formData.waitingPeriodDays) : 0,
      requiresPreApproval: formData.requiresPreApproval,
      notes: formData.notes || null
    };

    onSubmit(payload);
  }, [formData, validate, onSubmit]);

  const handleClose = useCallback(() => {
    setFormData(INITIAL_FORM_STATE);
    setErrors({});
    onClose();
  }, [onClose]);

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isEdit ? 'تعديل قاعدة التغطية' : 'إضافة قاعدة تغطية جديدة'}</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {/* Target Type Selection */}
          <FormControl fullWidth error={!!errors.targetType} disabled={isEdit}>
            <InputLabel>نوع العنصر المغطى *</InputLabel>
            <Select value={formData.targetType} onChange={handleChange('targetType')} label="نوع العنصر المغطى *">
              <MenuItem value="CATEGORY">
                <Stack direction="row" spacing={1} alignItems="center">
                  <CategoryIcon fontSize="small" />
                  <span>تصنيف طبي (يشمل جميع خدماته)</span>
                </Stack>
              </MenuItem>
              <MenuItem value="SERVICE">
                <Stack direction="row" spacing={1} alignItems="center">
                  <ServiceIcon fontSize="small" />
                  <span>خدمة طبية محددة</span>
                </Stack>
              </MenuItem>
            </Select>
            {errors.targetType && <FormHelperText>{errors.targetType}</FormHelperText>}
          </FormControl>

          {/* Category Selector (shown when targetType = CATEGORY) */}
          {formData.targetType === 'CATEGORY' && (
            <FormControl fullWidth error={!!errors.medicalCategoryId} disabled={isEdit}>
              <InputLabel>التصنيف الطبي *</InputLabel>
              <Select
                value={formData.medicalCategoryId}
                onChange={handleChange('medicalCategoryId')}
                label="التصنيف الطبي *"
                disabled={loadingCategories}
              >
                {loadingCategories ? (
                  <MenuItem disabled>جاري التحميل...</MenuItem>
                ) : (
                  categories.map((cat) => (
                    <MenuItem key={cat.id} value={cat.id}>
                      {cat.name} ({cat.code})
                    </MenuItem>
                  ))
                )}
              </Select>
              {errors.medicalCategoryId && <FormHelperText>{errors.medicalCategoryId}</FormHelperText>}
            </FormControl>
          )}

          {/* Service Selector (shown when targetType = SERVICE) */}
          {formData.targetType === 'SERVICE' && (
            <Stack spacing={2}>
              <FormControl fullWidth disabled={isEdit} error={!!errors.medicalCategoryId}>
                <InputLabel>التصنيف الطبي *</InputLabel>
                <Select
                  value={formData.medicalCategoryId}
                  onChange={handleChange('medicalCategoryId')}
                  label="التصنيف الطبي *"
                  disabled={loadingCategories}
                >
                  <MenuItem value="" disabled>
                    اختر التصنيف أولاً
                  </MenuItem>
                  {loadingCategories ? (
                    <MenuItem disabled>جاري التحميل...</MenuItem>
                  ) : (
                    categories.map((cat) => (
                      <MenuItem key={cat.id} value={cat.id}>
                        {cat.name} ({cat.code})
                      </MenuItem>
                    ))
                  )}
                </Select>
                {errors.medicalCategoryId && <FormHelperText>{errors.medicalCategoryId}</FormHelperText>}
              </FormControl>

              <MedicalServiceSelector
                value={formData.medicalServiceObject}
                categoryId={formData.medicalCategoryId ? Number(formData.medicalCategoryId) : null}
                onChange={(service) => {
                  setFormData((prev) => ({
                    ...prev,
                    medicalServiceId: service?.id || '',
                    medicalServiceObject: service || null
                  }));
                  setErrors((prev) => ({ ...prev, medicalServiceId: null }));
                }}
                disabled={isEdit || !formData.medicalCategoryId}
                required
                error={!!errors.medicalServiceId}
                helperText={errors.medicalServiceId || (!formData.medicalCategoryId ? 'اختر التصنيف الطبي أولاً' : '')}
                label="الخدمة الطبية *"
                size="medium"
              />
            </Stack>
          )}

          {/* Coverage Percent */}
          <TextField
            label="نسبة التغطية"
            type="number"
            value={formData.coveragePercent}
            onChange={handleChange('coveragePercent')}
            error={!!errors.coveragePercent}
            helperText={errors.coveragePercent || 'اتركه فارغاً لاستخدام النسبة الافتراضية للوثيقة'}
            InputProps={{
              endAdornment: <InputAdornment position="end">%</InputAdornment>,
              inputProps: { min: 0, max: 100 }
            }}
            fullWidth
          />

          {/* Amount Limit */}
          <TextField
            label="الحد الأقصى للمبلغ"
            type="number"
            value={formData.amountLimit}
            onChange={handleChange('amountLimit')}
            error={!!errors.amountLimit}
            helperText={errors.amountLimit}
            InputProps={{
              endAdornment: <InputAdornment position="end">د.ل</InputAdornment>,
              inputProps: { min: 0 }
            }}
            fullWidth
          />

          {/* Times Limit */}
          <TextField
            label="الحد الأقصى للمرات"
            type="number"
            value={formData.timesLimit}
            onChange={handleChange('timesLimit')}
            error={!!errors.timesLimit}
            helperText={errors.timesLimit || 'عدد المرات المسموح بها خلال فترة الوثيقة'}
            InputProps={{
              inputProps: { min: 0, step: 1 }
            }}
            fullWidth
          />

          {/* Waiting Period */}
          <TextField
            label="فترة الانتظار"
            type="number"
            value={formData.waitingPeriodDays}
            onChange={handleChange('waitingPeriodDays')}
            error={!!errors.waitingPeriodDays}
            helperText={errors.waitingPeriodDays || 'عدد الأيام قبل سريان التغطية'}
            InputProps={{
              endAdornment: <InputAdornment position="end">يوم</InputAdornment>,
              inputProps: { min: 0, step: 1 }
            }}
            fullWidth
          />

          {/* Requires Pre-Approval */}
          <FormControlLabel
            control={<Switch checked={formData.requiresPreApproval} onChange={handleChange('requiresPreApproval')} color="primary" />}
            label="تتطلب موافقة مسبقة"
          />

          {/* Notes */}
          <TextField label="ملاحظات" value={formData.notes} onChange={handleChange('notes')} multiline rows={2} fullWidth />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          إلغاء
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={loading || !formData.targetType}
          startIcon={loading && <CircularProgress size={16} color="inherit" />}
        >
          {isEdit ? 'حفظ التعديلات' : 'إضافة القاعدة'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

RuleFormModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  initialData: PropTypes.object,
  isEdit: PropTypes.bool,
  loading: PropTypes.bool,
  categories: PropTypes.array,
  loadingCategories: PropTypes.bool
};

// ═══════════════════════════════════════════════════════════════════════════
// DELETE CONFIRMATION DIALOG
// ═══════════════════════════════════════════════════════════════════════════

const DeleteConfirmDialog = ({ open, ruleName, onConfirm, onCancel, loading }) => (
  <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
    <DialogTitle>حذف قاعدة التغطية</DialogTitle>
    <DialogContent>
      <DialogContentText>
        هل أنت متأكد من حذف قاعدة التغطية "{ruleName}"؟
        <br />
        سيتم إلغاء تفعيل هذه القاعدة.
      </DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={onCancel} disabled={loading}>
        إلغاء
      </Button>
      <Button
        onClick={onConfirm}
        color="error"
        variant="contained"
        disabled={loading}
        startIcon={loading && <CircularProgress size={16} color="inherit" />}
      >
        حذف
      </Button>
    </DialogActions>
  </Dialog>
);

DeleteConfirmDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  ruleName: PropTypes.string,
  onConfirm: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  loading: PropTypes.bool
};

// ═══════════════════════════════════════════════════════════════════════════
// CATEGORY COVERAGE MODAL
// ═══════════════════════════════════════════════════════════════════════════

const CategoryCoverageModal = ({
  open,
  onClose,
  canEdit,
  bulkSavingCoverage,
  categoriesCoverageRows,
  handleCoverageInputChange,
  saveCategoryCoverage,
  saveAllCategoryCoverage,
  deleteRule,
  createMutation,
  updateMutation,
  isLoading
}) => (
  <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
    <DialogTitle>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h5">القواعد الأساسية — التغطية حسب التصنيف</Typography>
        <Button
          size="small"
          variant="contained"
          color="primary"
          startIcon={bulkSavingCoverage ? <CircularProgress size={14} color="inherit" /> : <SaveIcon fontSize="small" />}
          onClick={saveAllCategoryCoverage}
          disabled={!canEdit || bulkSavingCoverage || isLoading}
        >
          حفظ جماعي
        </Button>
      </Stack>
    </DialogTitle>
    <DialogContent dividers sx={{ p: 0 }}>
      <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 1 }}>
        حدّد نسبة التغطية لكل تصنيف. هذه النسبة تُطبّق على جميع خدمات التصنيف ما لم توجد قاعدة خدمة خاصة.
      </Typography>
      <TableContainer sx={{ maxHeight: 520 }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>التصنيف</TableCell>
              <TableCell align="center" sx={{ width: 120 }}>النسبة الحالية</TableCell>
              <TableCell align="center" sx={{ width: 140 }}>نسبة التغطية (اختياري)</TableCell>
              <TableCell align="center" sx={{ width: 140 }}>عدد المرات</TableCell>
              <TableCell align="center" sx={{ width: 150 }}>سقف التصنيف</TableCell>
              <TableCell align="center" sx={{ width: 140 }}>الإجراءات</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {categoriesCoverageRows.map((row) => {
              const isRowSaving = createMutation.isPending || updateMutation.isPending;
              return (
                <TableRow key={row.category.id} hover>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Chip label={row.category.code || '-'} size="small" variant="outlined" sx={{ width: 'fit-content', fontFamily: 'monospace' }} />
                        {row.serviceRulesCount > 0 && (
                          <Tooltip title={`${row.serviceRulesCount} قاعدة خدمة مخصصة تُعدّل هذا التصنيف`}>
                            <Chip label={`${row.serviceRulesCount} خدمة`} size="small" color="secondary" variant="filled" />
                          </Tooltip>
                        )}
                      </Stack>
                      <Typography variant="body2" fontWeight={500}>
                        {row.category.nameAr || row.category.name || '-'}
                      </Typography>
                      {row.category.nameEn && (
                        <Typography variant="caption" color="text.secondary">
                          {row.category.nameEn}
                        </Typography>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell align="center">
                    {row.effectiveCoveragePercent !== null && row.effectiveCoveragePercent !== undefined
                      ? `${row.effectiveCoveragePercent}%`
                      : 'افتراضي الوثيقة'}
                  </TableCell>
                  <TableCell align="center" sx={{ width: 140 }}>
                    <TextField
                      size="small"
                      type="number"
                      value={row.coverageInputValue}
                      onChange={(e) => handleCoverageInputChange(row.category.id, 'coveragePercent', e.target.value)}
                      inputProps={{ min: 0, max: 100 }}
                      InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                      placeholder="افتراضي"
                      fullWidth
                      disabled={!canEdit || bulkSavingCoverage}
                    />
                  </TableCell>
                  <TableCell align="center" sx={{ width: 140 }}>
                    <TextField
                      size="small"
                      type="number"
                      value={row.timesLimitInputValue}
                      onChange={(e) => handleCoverageInputChange(row.category.id, 'timesLimit', e.target.value)}
                      inputProps={{ min: 0, step: 1 }}
                      fullWidth
                      disabled={!canEdit || bulkSavingCoverage}
                    />
                  </TableCell>
                  <TableCell align="center" sx={{ width: 150 }}>
                    <TextField
                      size="small"
                      type="number"
                      value={row.amountLimitInputValue}
                      onChange={(e) => handleCoverageInputChange(row.category.id, 'amountLimit', e.target.value)}
                      inputProps={{ min: 0 }}
                      InputProps={{ endAdornment: <InputAdornment position="end">د.ل</InputAdornment> }}
                      fullWidth
                      disabled={!canEdit || bulkSavingCoverage}
                    />
                  </TableCell>
                  <TableCell align="center" sx={{ width: 140 }}>
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                      <Button
                        size="small"
                        variant="contained"
                        startIcon={isRowSaving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon fontSize="small" />}
                        onClick={() => saveCategoryCoverage(row)}
                        disabled={!canEdit || isLoading || isRowSaving || bulkSavingCoverage}
                      >
                        حفظ
                      </Button>
                      {row.existingRule?.id && (
                        <Tooltip title="حذف قاعدة هذا التصنيف">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => deleteRule(row.existingRule)}
                              disabled={!canEdit || isLoading || isRowSaving || bulkSavingCoverage}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>إغلاق</Button>
    </DialogActions>
  </Dialog>
);

CategoryCoverageModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  canEdit: PropTypes.bool,
  bulkSavingCoverage: PropTypes.bool,
  categoriesCoverageRows: PropTypes.array,
  handleCoverageInputChange: PropTypes.func.isRequired,
  saveCategoryCoverage: PropTypes.func.isRequired,
  saveAllCategoryCoverage: PropTypes.func.isRequired,
  deleteRule: PropTypes.func.isRequired,
  createMutation: PropTypes.object.isRequired,
  updateMutation: PropTypes.object.isRequired,
  isLoading: PropTypes.bool
};

// ═══════════════════════════════════════════════════════════════════════════
// MAIN RULES TAB COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Benefit Policy Rules Tab
 *
 * Displays and manages coverage rules for a benefit policy
 */
const BenefitPolicyRulesTab = ({ policyId, policyStatus, policyDefaultCoveragePercent }) => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // Modal states
  const [formModal, setFormModal] = useState({ open: false, data: null, isEdit: false });
  const [deleteDialog, setDeleteDialog] = useState({ open: false, rule: null });
  const [ruleSearch, setRuleSearch] = useState('');
  const [categoryCoverageInputs, setCategoryCoverageInputs] = useState({});
  const [bulkSavingCoverage, setBulkSavingCoverage] = useState(false);
  const [categoriesModalOpen, setCategoriesModalOpen] = useState(false);

  // ═══════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ═══════════════════════════════════════════════════════════════════════════

  // Fetch rules
  const {
    data: rules = [],
    isLoading: loadingRules,
    error: rulesError,
    refetch: refetchRules
  } = useQuery({
    queryKey: ['benefit-policy-rules', policyId],
    queryFn: () => getPolicyRules(policyId),
    enabled: !!policyId
  });

  // Fetch categories for selector
  const { data: categories = [], isLoading: loadingCategories } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: getAllMedicalCategories
  });

  // NOTE: Services are now fetched dynamically by MedicalServiceSelector component

  // ═══════════════════════════════════════════════════════════════════════════
  // MUTATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  const createMutation = useMutation({
    mutationFn: (payload) => createPolicyRule(policyId, payload),
    onSuccess: () => {
      enqueueSnackbar('تمت إضافة القاعدة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries(['benefit-policy-rules', policyId]);
      setFormModal({ open: false, data: null, isEdit: false });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل إضافة القاعدة', { variant: 'error' });
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ ruleId, payload }) => updatePolicyRule(policyId, ruleId, payload),
    onSuccess: () => {
      enqueueSnackbar('تم تحديث القاعدة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries(['benefit-policy-rules', policyId]);
      setFormModal({ open: false, data: null, isEdit: false });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل تحديث القاعدة', { variant: 'error' });
    }
  });

  const toggleMutation = useMutation({
    mutationFn: (ruleId) => togglePolicyRuleActive(policyId, ruleId),
    onSuccess: () => {
      enqueueSnackbar('تم تغيير حالة القاعدة', { variant: 'success' });
      queryClient.invalidateQueries(['benefit-policy-rules', policyId]);
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل تغيير الحالة', { variant: 'error' });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId) => deletePolicyRule(policyId, ruleId),
    onSuccess: () => {
      enqueueSnackbar('تم حذف القاعدة', { variant: 'success' });
      queryClient.invalidateQueries(['benefit-policy-rules', policyId]);
      setDeleteDialog({ open: false, rule: null });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل حذف القاعدة', { variant: 'error' });
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════

  const handleAddRule = useCallback(() => {
    setFormModal({ open: true, data: null, isEdit: false });
  }, []);

  const handleEditRule = useCallback((rule) => {
    setFormModal({ open: true, data: rule, isEdit: true });
  }, []);

  const handleDeleteRule = useCallback((rule) => {
    setDeleteDialog({ open: true, rule });
  }, []);

  const handleToggleActive = useCallback(
    (rule) => {
      toggleMutation.mutate(rule.id);
    },
    [toggleMutation]
  );

  const handleFormSubmit = useCallback(
    (payload) => {
      if (formModal.isEdit && formModal.data) {
        updateMutation.mutate({ ruleId: formModal.data.id, payload });
      } else {
        createMutation.mutate(payload);
      }
    },
    [formModal, createMutation, updateMutation]
  );

  const handleFormClose = useCallback(() => {
    setFormModal({ open: false, data: null, isEdit: false });
  }, []);

  const handleDeleteConfirm = useCallback(() => {
    if (deleteDialog.rule) {
      deleteMutation.mutate(deleteDialog.rule.id);
    }
  }, [deleteDialog.rule, deleteMutation]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialog({ open: false, rule: null });
  }, []);

  // ═══════════════════════════════════════════════════════════════════════════
  // COMPUTED
  // ═══════════════════════════════════════════════════════════════════════════

  const canEdit = policyStatus !== 'CANCELLED';
  const isLoading = createMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  const categoryMap = useMemo(() => {
    const map = new Map();
    categories.forEach((cat) => map.set(cat.id, cat));
    return map;
  }, [categories]);

  const normalizedRules = useMemo(() => {
    return rules.map((rule) => {
      const isCategory = rule.ruleType === 'CATEGORY';
      const code = isCategory ? rule.medicalCategoryCode || '-' : rule.medicalServiceCode || '-';
      const nameAr = rule.label || (isCategory ? rule.medicalCategoryName : rule.medicalServiceName) || '-';
      const nameEn = isCategory ? rule.medicalCategoryNameEn || '-' : rule.medicalServiceNameEn || '-';

      let typeLabel = 'خدمة طبية';
      if (isCategory) {
        const cat = categoryMap.get(rule.medicalCategoryId);
        const isRoot = cat ? !cat.parentId : true;
        typeLabel = isRoot ? 'تصنيف طبي رئيسي' : 'تصنيف طبي فرعي';
      }

      const changedAt = rule.updatedAt || rule.lastModifiedAt || rule.modifiedAt || rule.createdAt || null;
      const searchable = `${code} ${nameAr} ${nameEn} ${typeLabel}`.toLowerCase();

      return {
        ...rule,
        code,
        nameAr,
        nameEn,
        typeLabel,
        changedAt,
        searchable
      };
    });
  }, [rules, categoryMap]);

  const filteredRules = useMemo(() => {
    const query = ruleSearch.trim().toLowerCase();
    if (!query) return normalizedRules;
    return normalizedRules.filter((rule) => rule.searchable.includes(query));
  }, [normalizedRules, ruleSearch]);

  const activeRulesCount = useMemo(() => normalizedRules.filter((rule) => rule.active).length, [normalizedRules]);

  const categoryRulesByCategoryId = useMemo(() => {
    const map = new Map();
    normalizedRules
      .filter((rule) => rule.ruleType === 'CATEGORY')
      .forEach((rule) => {
        if (!map.has(rule.medicalCategoryId)) {
          map.set(rule.medicalCategoryId, rule);
        }
      });
    return map;
  }, [normalizedRules]);

  // Count of service-level rules per category (for badge)
  const serviceRulesCountByCategoryId = useMemo(() => {
    const map = new Map();
    normalizedRules
      .filter((rule) => rule.ruleType === 'SERVICE' && rule.medicalCategoryId)
      .forEach((rule) => {
        map.set(rule.medicalCategoryId, (map.get(rule.medicalCategoryId) || 0) + 1);
      });
    return map;
  }, [normalizedRules]);

  const categoriesCoverageRows = useMemo(
    () =>
      categories.map((category) => {
        const existingRule = categoryRulesByCategoryId.get(category.id);
        const existingCoveragePercent = existingRule?.coveragePercent;
        const coverageInputValue =
          categoryCoverageInputs[category.id]?.coveragePercent !== undefined
            ? categoryCoverageInputs[category.id].coveragePercent
            : existingCoveragePercent !== null && existingCoveragePercent !== undefined
              ? String(existingCoveragePercent)
              : '';

        const timesLimitInputValue =
          categoryCoverageInputs[category.id]?.timesLimit !== undefined
            ? categoryCoverageInputs[category.id].timesLimit
            : existingRule?.timesLimit !== null && existingRule?.timesLimit !== undefined
              ? String(existingRule.timesLimit)
              : '';

        const amountLimitInputValue =
          categoryCoverageInputs[category.id]?.amountLimit !== undefined
            ? categoryCoverageInputs[category.id].amountLimit
            : existingRule?.amountLimit !== null && existingRule?.amountLimit !== undefined
              ? String(existingRule.amountLimit)
              : '';

        return {
          category,
          existingRule,
          coverageInputValue,
          timesLimitInputValue,
          amountLimitInputValue,
          effectiveCoveragePercent: existingRule?.effectiveCoveragePercent ?? existingCoveragePercent ?? null,
          serviceRulesCount: serviceRulesCountByCategoryId.get(category.id) || 0
        };
      }),
    [categories, categoryRulesByCategoryId, serviceRulesCountByCategoryId, categoryCoverageInputs, policyDefaultCoveragePercent]
  );

  const handleCoverageInputChange = useCallback((categoryId, field, value) => {
    setCategoryCoverageInputs((prev) => ({
      ...prev,
      [categoryId]: {
        ...prev[categoryId],
        [field]: value
      }
    }));
  }, []);

  const saveCategoryCoverage = useCallback(
    (row) => {
      const rawCoverage = (row.coverageInputValue ?? '').trim();
      const rawTimesLimit = (row.timesLimitInputValue ?? '').trim();
      const rawAmountLimit = (row.amountLimitInputValue ?? '').trim();

      // At least one limit must be specified
      if (rawCoverage === '' && rawTimesLimit === '' && rawAmountLimit === '') {
        enqueueSnackbar('يجب تحديد نسبة التغطية أو حد المبلغ أو حد المرات على الأقل', { variant: 'warning' });
        return;
      }

      const coveragePercent = rawCoverage !== '' ? Number(rawCoverage) : null;
      if (coveragePercent !== null && (Number.isNaN(coveragePercent) || coveragePercent < 0 || coveragePercent > 100)) {
        enqueueSnackbar('نسبة التغطية يجب أن تكون بين 0 و 100', { variant: 'warning' });
        return;
      }

      const timesLimit = rawTimesLimit !== '' ? Number(rawTimesLimit) : null;
      const amountLimit = rawAmountLimit !== '' ? Number(rawAmountLimit) : null;

      const payload = {
        medicalCategoryId: Number(row.category.id),
        medicalServiceId: null,
        coveragePercent,
        amountLimit,
        timesLimit,
        waitingPeriodDays: row.existingRule?.waitingPeriodDays ?? 0,
        requiresPreApproval: row.existingRule?.requiresPreApproval ?? false,
        notes: row.existingRule?.notes ?? null
      };

      if (row.existingRule?.id) {
        updateMutation.mutate({ ruleId: row.existingRule.id, payload });
      } else {
        createMutation.mutate(payload);
      }
    },
    [createMutation, enqueueSnackbar, updateMutation]
  );

  const saveAllCategoryCoverage = useCallback(async () => {
    const changedRows = categoriesCoverageRows.filter((row) => categoryCoverageInputs[row.category.id] !== undefined);

    if (changedRows.length === 0) {
      enqueueSnackbar('لا توجد تعديلات جديدة للحفظ', { variant: 'info' });
      return;
    }

    for (const row of changedRows) {
      const rawCoverage = (row.coverageInputValue ?? '').trim();
      const rawTimes = (row.timesLimitInputValue ?? '').trim();
      const rawAmount = (row.amountLimitInputValue ?? '').trim();
      const catName = row.category.nameAr || row.category.name || row.category.code;

      if (rawCoverage === '' && rawTimes === '' && rawAmount === '') {
        enqueueSnackbar(`يجب تحديد نسبة التغطية أو حد المبلغ أو حد المرات للتصنيف: ${catName}`, {
          variant: 'warning'
        });
        return;
      }

      if (rawCoverage !== '') {
        const cov = Number(rawCoverage);
        if (Number.isNaN(cov) || cov < 0 || cov > 100) {
          enqueueSnackbar(`قيمة التغطية غير صحيحة في التصنيف: ${catName}`, { variant: 'warning' });
          return;
        }
      }
    }

    setBulkSavingCoverage(true);
    try {
      const results = await Promise.allSettled(
        changedRows.map(async (row) => {
          const rawCoverage = (row.coverageInputValue ?? '').trim();
          const rawTimesLimit = (row.timesLimitInputValue ?? '').trim();
          const rawAmountLimit = (row.amountLimitInputValue ?? '').trim();

          const coveragePercent = rawCoverage !== '' ? Number(rawCoverage) : null;
          const timesLimit = rawTimesLimit !== '' ? Number(rawTimesLimit) : null;
          const amountLimit = rawAmountLimit !== '' ? Number(rawAmountLimit) : null;

          const payload = {
            medicalCategoryId: Number(row.category.id),
            medicalServiceId: null,
            coveragePercent,
            amountLimit,
            timesLimit,
            waitingPeriodDays: row.existingRule?.waitingPeriodDays ?? 0,
            requiresPreApproval: row.existingRule?.requiresPreApproval ?? false,
            notes: row.existingRule?.notes ?? null
          };

          if (row.existingRule?.id) {
            return updatePolicyRule(policyId, row.existingRule.id, payload);
          } else {
            return createPolicyRule(policyId, payload);
          }
        })
      );

      const succeeded = results.filter((r) => r.status === 'fulfilled').length;
      const failed = results.filter((r) => r.status === 'rejected').length;

      if (succeeded > 0) {
        setCategoryCoverageInputs({});
        queryClient.invalidateQueries(['benefit-policy-rules', policyId]);
      }
      if (failed === 0) {
        enqueueSnackbar(`تم حفظ ${succeeded} تصنيف بنجاح`, { variant: 'success' });
      } else if (succeeded === 0) {
        enqueueSnackbar(`فشل حفظ جميع التصنيفات (${failed})`, { variant: 'error' });
      } else {
        enqueueSnackbar(`تم حفظ ${succeeded} تصنيف، وفشل ${failed} تصنيف`, { variant: 'warning' });
      }
    } finally {
      setBulkSavingCoverage(false);
    }
  }, [categoriesCoverageRows, categoryCoverageInputs, enqueueSnackbar, policyId, queryClient]);

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════════════════════════════════════════

  if (loadingRules) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
        <CircularProgress />
      </Box>
    );
  }

  if (rulesError) {
    return <Alert severity="error">فشل تحميل قواعد التغطية: {rulesError.response?.data?.message || rulesError.message}</Alert>;
  }

  return (
    <>
      <MainCard
        title="قواعد التغطية"
        secondary={
          canEdit && (
            <Stack direction="row" spacing={1}>
              <Button
                variant="outlined"
                color="secondary"
                startIcon={<CategoryIcon />}
                onClick={() => setCategoriesModalOpen(true)}
                size="small"
              >
                القواعد الأساسية
              </Button>
              <Button variant="contained" color="primary" startIcon={<AddIcon />} onClick={handleAddRule} size="small">
                إضافة قاعدة
              </Button>
            </Stack>
          )
        }
      >
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }} alignItems={{ xs: 'stretch', md: 'center' }}>
          <TextField
            placeholder="بحث بالكود أو الاسم أو النوع..."
            value={ruleSearch}
            onChange={(e) => setRuleSearch(e.target.value)}
            size="small"
            sx={{ flexGrow: 1, maxWidth: { xs: '100%', md: 420 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
              ),
              endAdornment: ruleSearch ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setRuleSearch('')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
          <Chip size="small" variant="outlined" color="primary" label={`${normalizedRules.length} قاعدة`} sx={{ width: 'fit-content' }} />
          <Chip size="small" variant="outlined" color="success" label={`${activeRulesCount} نشطة`} sx={{ width: 'fit-content' }} />
        </Stack>

        <TableContainer sx={{ maxHeight: 560 }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow sx={{ backgroundColor: 'grey.50' }}>
                <TableCell>العنصر (القاموس الموحد)</TableCell>
                <TableCell align="center">النوع</TableCell>
                <TableCell align="center">نسبة التغطية</TableCell>
                <TableCell align="center">حد المبلغ</TableCell>
                <TableCell align="center">حد المرات</TableCell>
                <TableCell align="center">فترة الانتظار</TableCell>
                <TableCell align="center">موافقة مسبقة</TableCell>
                <TableCell align="center">نشط</TableCell>
                <TableCell align="center">آخر تحديث</TableCell>
                <TableCell align="center">الإجراءات</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rules.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">
                      لا توجد قواعد تغطية. استخدم &quot;القواعد الأساسية&quot; لتعيين التغطية لكل تصنيف، أو &quot;إضافة قاعدة&quot; لإضافة قاعدة مخصصة.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : filteredRules.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">لا توجد نتائج مطابقة للبحث</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                filteredRules.map((rule) => (
                  <TableRow key={rule.id} hover sx={{ '&:nth-of-type(even)': { bgcolor: 'grey.25' } }}>
                    {/* Covered Item */}
                    <TableCell>
                      <Stack direction="row" spacing={1} alignItems="center">
                        {rule.ruleType === 'CATEGORY' ? (
                          <Tooltip title="تصنيف طبي">
                            <CategoryIcon fontSize="small" color="primary" />
                          </Tooltip>
                        ) : (
                          <Tooltip title="خدمة طبية">
                            <ServiceIcon fontSize="small" color="secondary" />
                          </Tooltip>
                        )}
                        <Box>
                          <Chip label={rule.code} size="small" variant="outlined" color="primary" sx={{ mb: 0.5, fontFamily: 'monospace' }} />
                          <Typography variant="body2" fontWeight={500}>
                            {rule.nameAr}
                          </Typography>
                          {rule.nameEn !== '-' && <Typography variant="caption" color="text.secondary">{rule.nameEn}</Typography>}
                        </Box>
                      </Stack>
                    </TableCell>

                    <TableCell align="center">
                      <Chip
                        label={rule.typeLabel}
                        size="small"
                        color={rule.ruleType === 'CATEGORY' ? 'primary' : 'secondary'}
                        variant="outlined"
                      />
                    </TableCell>

                    {/* Coverage % */}
                    <TableCell align="center">
                      {rule.coveragePercent !== null && rule.coveragePercent !== undefined ? (
                        <Chip
                          label={`${rule.coveragePercent}%`}
                          size="small"
                          color="primary"
                          variant="filled"
                        />
                      ) : (
                        <Tooltip title={`افتراضي الوثيقة: ${rule.effectiveCoveragePercent}%`}>
                          <Chip
                            label={`${rule.effectiveCoveragePercent}% (افتراضي)`}
                            size="small"
                            color="default"
                            variant="outlined"
                          />
                        </Tooltip>
                      )}
                    </TableCell>

                    {/* Amount Limit */}
                    <TableCell align="center">
                      {rule.amountLimit ? `${Number(rule.amountLimit).toLocaleString('en-US')} د.ل` : '-'}
                    </TableCell>

                    {/* Times Limit */}
                    <TableCell align="center">{rule.timesLimit ?? '-'}</TableCell>

                    {/* Waiting Period */}
                    <TableCell align="center">{rule.waitingPeriodDays ? `${rule.waitingPeriodDays} يوم` : '-'}</TableCell>

                    {/* Requires Pre-Approval */}
                    <TableCell align="center">
                      {rule.requiresPreApproval ? (
                        <Chip label="نعم" size="small" color="warning" />
                      ) : (
                        <Chip label="لا" size="small" variant="outlined" />
                      )}
                    </TableCell>

                    {/* Active Toggle */}
                    <TableCell align="center">
                      <Tooltip title={rule.active ? 'تعطيل القاعدة' : 'تفعيل القاعدة'}>
                        <span>
                          <Switch
                            checked={rule.active}
                            onChange={() => handleToggleActive(rule)}
                            size="small"
                            disabled={!canEdit || toggleMutation.isPending}
                          />
                        </span>
                      </Tooltip>
                    </TableCell>

                    <TableCell align="center">
                      <Typography variant="caption" color="text.secondary">
                        {rule.changedAt ? new Date(rule.changedAt).toLocaleDateString('ar-LY') : '-'}
                      </Typography>
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="center">
                      {canEdit && (
                        <Stack direction="row" spacing={0} justifyContent="center">
                          <Tooltip title="تعديل">
                            <IconButton size="small" color="primary" onClick={() => handleEditRule(rule)}>
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="حذف">
                            <IconButton size="small" color="error" onClick={() => handleDeleteRule(rule)}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Stack>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </MainCard>

      {/* Categories Coverage Modal */}
      <CategoryCoverageModal
        open={categoriesModalOpen}
        onClose={() => setCategoriesModalOpen(false)}
        canEdit={canEdit}
        bulkSavingCoverage={bulkSavingCoverage}
        categoriesCoverageRows={categoriesCoverageRows}
        handleCoverageInputChange={handleCoverageInputChange}
        saveCategoryCoverage={saveCategoryCoverage}
        saveAllCategoryCoverage={saveAllCategoryCoverage}
        deleteRule={handleDeleteRule}
        createMutation={createMutation}
        updateMutation={updateMutation}
        isLoading={isLoading}
      />

      {/* Rule Form Modal */}
      <RuleFormModal
        open={formModal.open}
        onClose={handleFormClose}
        onSubmit={handleFormSubmit}
        initialData={formModal.data}
        isEdit={formModal.isEdit}
        loading={createMutation.isPending || updateMutation.isPending}
        categories={categories}
        loadingCategories={loadingCategories}
      />

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={deleteDialog.open}
        ruleName={deleteDialog.rule?.label || deleteDialog.rule?.medicalCategoryName || deleteDialog.rule?.medicalServiceName}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        loading={deleteMutation.isPending}
      />
    </>
  );
};

BenefitPolicyRulesTab.propTypes = {
  policyId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  policyStatus: PropTypes.string,
  policyDefaultCoveragePercent: PropTypes.number
};

export default BenefitPolicyRulesTab;
