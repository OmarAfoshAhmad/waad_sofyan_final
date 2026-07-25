import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  FormControlLabel,
  Switch,
  InputAdornment,
  CircularProgress,
  Autocomplete,
  Chip,
  Box,
  Typography,
  Alert,
  Grid
} from '@mui/material';
import { useSnackbar } from 'notistack';
import { createPolicyRule, updatePolicyRule } from 'services/api/benefit-policy-rules.service';
import { createBenefitGroup, upsertIndividualBenefitLimit } from 'services/api/benefit-structure.service';

const UnifiedCoverageModal = ({
  open,
  onClose,
  onSuccess,
  policyId,
  categories, 
  initialData, 
  isEdit,
  policyDefaultCoveragePercent
}) => {
  const { enqueueSnackbar } = useSnackbar();
  const [loading, setLoading] = useState(false);

  const [selectedCategories, setSelectedCategories] = useState([]);
  const [groupName, setGroupName] = useState('');
  
  const [encounterType, setEncounterType] = useState('ANY');
  const [coveragePercent, setCoveragePercent] = useState('');
  const [requiresPreApproval, setRequiresPreApproval] = useState(false);
  const [waitingPeriodDays, setWaitingPeriodDays] = useState(0);
  const [notes, setNotes] = useState('');
  
  const [amountLimit, setAmountLimit] = useState('');
  const [timesLimit, setTimesLimit] = useState('');
  const [daysLimit, setDaysLimit] = useState('');
  const [periodType, setPeriodType] = useState('YEARLY');

  const isGroup = selectedCategories.length > 1;

  useEffect(() => {
    const defaultCoverage = policyDefaultCoveragePercent !== undefined && policyDefaultCoveragePercent !== null ? String(policyDefaultCoveragePercent) : '';
    if (open && initialData) {
      if (initialData.groupSource || initialData.type === 'GROUP') {
         setGroupName(initialData.nameAr || '');
         setEncounterType(initialData.encounterType || initialData.contextType || 'ANY');
         if (initialData.groupMembers || initialData.rules) {
           const members = initialData.groupMembers || initialData.rules;
           const catIds = members.map(r => r.medicalCategoryId || r.id);
           setSelectedCategories(categories.filter(c => catIds.includes(c.id)));
         }
         const bucket = initialData.bucket;
         if (bucket) {
             setAmountLimit(bucket.amountLimit ?? '');
             setTimesLimit(bucket.timesLimit ?? '');
             setDaysLimit(bucket.daysLimit ?? '');
             setPeriodType(bucket.periodType ?? 'YEARLY');
         }
      } else {
         const cat = categories.find(c => c.id === initialData.medicalCategoryId || c.id === initialData.categoryId);
         if (cat) setSelectedCategories([cat]);
         setEncounterType(initialData.encounterType || 'ANY');
         setCoveragePercent(initialData.coveragePercent !== null && initialData.coveragePercent !== undefined ? String(initialData.coveragePercent) : defaultCoverage);
         setRequiresPreApproval(initialData.requiresPreApproval || false);
         setWaitingPeriodDays(initialData.waitingPeriodDays || 0);
         setNotes(initialData.notes || '');
         
         const limit = initialData.individualLimitLink?.bucket || initialData.individualBucket;
         if (limit) {
             setAmountLimit(limit.amountLimit ?? '');
             setTimesLimit(limit.timesLimit ?? '');
             setDaysLimit(limit.daysLimit ?? '');
             setPeriodType(limit.periodType ?? 'YEARLY');
         } else {
             setAmountLimit('');
             setTimesLimit('');
             setDaysLimit('');
             setPeriodType('YEARLY');
         }
      }
    } else if (open && !initialData) {
      setSelectedCategories([]);
      setGroupName('');
      setEncounterType('ANY');
      setCoveragePercent(defaultCoverage);
      setRequiresPreApproval(false);
      setWaitingPeriodDays(0);
      setNotes('');
      setAmountLimit('');
      setTimesLimit('');
      setDaysLimit('');
      setPeriodType('YEARLY');
    }
  }, [open, initialData, categories, policyDefaultCoveragePercent]);

  const handleSubmit = async () => {
    if (selectedCategories.length === 0) {
      enqueueSnackbar('يرجى اختيار تصنيف واحد على الأقل', { variant: 'warning' });
      return;
    }
    if (isGroup && !groupName.trim()) {
      enqueueSnackbar('يرجى إدخال اسم للمجموعة', { variant: 'warning' });
      return;
    }

    setLoading(true);
    try {
      if (isEdit) {
        const parsedAmount = amountLimit !== '' ? Number(amountLimit) : null;
        const parsedTimes = timesLimit !== '' ? Number(timesLimit) : null;
        const parsedDays = daysLimit !== '' ? Number(daysLimit) : null;
        const hasLimits = parsedAmount !== null || parsedTimes !== null || parsedDays !== null;

        if (!initialData.groupSource) {
          await updatePolicyRule(policyId, initialData.id, {
            coveragePercent: coveragePercent !== '' ? Number(coveragePercent) : null,
            waitingPeriodDays: waitingPeriodDays,
            requiresPreApproval: requiresPreApproval,
            encounterType: encounterType,
            notes: notes
          });

          if (hasLimits) {
            await upsertIndividualBenefitLimit(policyId, initialData.id, {
              amountLimit: parsedAmount,
              timesLimit: parsedTimes,
              daysLimit: parsedDays,
              periodType: periodType
            });
          }
        }
        enqueueSnackbar('تم تحديث البيانات بنجاح', { variant: 'success' });
        onSuccess();
        return;
      }

      const rulePromises = selectedCategories.map(cat => 
        createPolicyRule(policyId, {
          medicalCategoryId: cat.id,
          medicalServiceId: null,
          coveragePercent: coveragePercent !== '' ? Number(coveragePercent) : null,
          waitingPeriodDays: waitingPeriodDays,
          requiresPreApproval: requiresPreApproval,
          encounterType: encounterType,
          notes: notes
        })
      );
      
      const createdRules = await Promise.all(rulePromises);
      const parsedAmount = amountLimit !== '' ? Number(amountLimit) : null;
      const parsedTimes = timesLimit !== '' ? Number(timesLimit) : null;
      const parsedDays = daysLimit !== '' ? Number(daysLimit) : null;
      const hasLimits = parsedAmount !== null || parsedTimes !== null || parsedDays !== null;

      if (isGroup) {
        const ruleIds = createdRules.map(r => r.id);
        await createBenefitGroup(policyId, {
          nameAr: groupName,
          code: null,
          contextType: encounterType,
          aggregationMode: 'SHARED_LIMIT',
          active: true,
          amountLimit: parsedAmount,
          timesLimit: parsedTimes,
          daysLimit: parsedDays,
          periodType: periodType,
          countingMethod: 'FIFO',
          ruleIds: ruleIds
        });
      } else {
        if (hasLimits) {
          const ruleId = createdRules[0].id;
          await upsertIndividualBenefitLimit(policyId, ruleId, {
            amountLimit: parsedAmount,
            timesLimit: parsedTimes,
            daysLimit: parsedDays,
            periodType: periodType
          });
        }
      }

      enqueueSnackbar(isGroup ? 'تمت إضافة المجموعة بنجاح' : 'تمت إضافة المنفعة بنجاح', { variant: 'success' });
      onSuccess();
    } catch (error) {
      const msg = error.response?.data?.messageAr || error.response?.data?.message || error.message;
      enqueueSnackbar(`خطأ: ${msg}`, { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => !loading && onClose()} fullWidth maxWidth="md" dir="rtl">
      <DialogTitle>{isEdit ? 'تعديل التغطية' : 'إضافة تغطية (منفعة مفردة أو مجموعة)'}</DialogTitle>
      <DialogContent sx={{ pt: 1.5 }}>
        <Grid container spacing={2}>
          <Grid size={12}>
            <Alert severity={isGroup ? 'info' : 'success'} sx={{ mb: 2 }}>
              {isGroup 
                ? 'لقد قمت باختيار أكثر من تصنيف، سيتم إنشاء مجموعة منافع تشارك نفس السقف المالي والشروط.'
                : 'أنت الآن تقوم بإنشاء قاعدة لمنفعة مفردة. (اختر أكثر من تصنيف لتحويلها إلى مجموعة)'}
            </Alert>
          </Grid>
          <Grid size={12}>
            <Autocomplete
              multiple
              options={categories}
              value={selectedCategories}
              onChange={(e, newValue) => setSelectedCategories(newValue)}
              getOptionLabel={(option) => option.nameAr || option.name || option.code}
              isOptionEqualToValue={(option, value) => Number(option.id) === Number(value.id)}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => (
                  <Chip
                    variant="outlined"
                    color="primary"
                    label={option.nameAr || option.name || option.code}
                    {...getTagProps({ index })}
                  />
                ))
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="التصنيفات الطبية *"
                  placeholder="ابحث واختر..."
                />
              )}
            />
          </Grid>
          {isGroup && (
            <Grid size={12}>
              <TextField
                label="اسم المجموعة *"
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
                fullWidth
                required
                helperText="أدخل اسماً يصف هذه المجموعة (مثال: الولادة الطبيعية والقيصرية)"
              />
            </Grid>
          )}
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              select
              fullWidth
              label="سياق القاعدة"
              value={encounterType}
              onChange={(e) => setEncounterType(e.target.value)}
            >
              <MenuItem value="OUTPATIENT">عيادات خارجية</MenuItem>
              <MenuItem value="INPATIENT">إيواء</MenuItem>
              <MenuItem value="ANY">عام (ANY)</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="نسبة التغطية"
              type="number"
              value={coveragePercent}
              onChange={(e) => setCoveragePercent(e.target.value)}
              placeholder={policyDefaultCoveragePercent != null ? String(policyDefaultCoveragePercent) : "100"}
              helperText={policyDefaultCoveragePercent != null ? `النسبة الافتراضية للوثيقة: ${policyDefaultCoveragePercent}%` : "اتركه فارغاً لاستخدام النسبة الافتراضية"}
              InputProps={{
                endAdornment: <InputAdornment position="end">%</InputAdornment>,
                inputProps: { min: 0, max: 100 }
              }}
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <FormControlLabel
              control={<Switch checked={requiresPreApproval} onChange={(e) => setRequiresPreApproval(e.target.checked)} color="primary" />}
              label="موافقة مسبقة"
              sx={{ mt: 1 }}
            />
          </Grid>
          <Grid size={12}>
            <Typography variant="subtitle2" color="text.secondary" sx={{ mt: 1, mb: 1 }}>حدود المنفعة / السقوف</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="السقف المالي"
              type="number"
              value={amountLimit}
              onChange={(e) => setAmountLimit(e.target.value)}
              fullWidth
              helperText="د.ل (اتركه فارغاً للسقف المفتوح/الوثيقة)"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="حد المرات"
              type="number"
              value={timesLimit}
              onChange={(e) => setTimesLimit(e.target.value)}
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="حد الأيام"
              type="number"
              value={daysLimit}
              onChange={(e) => setDaysLimit(e.target.value)}
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              select
              fullWidth
              label="المدة الزمنية للسقف"
              value={periodType}
              onChange={(e) => setPeriodType(e.target.value)}
            >
              <MenuItem value="MONTHLY">شهرياً (Monthly)</MenuItem>
              <MenuItem value="ANNUAL">سنوياً (Annual)</MenuItem>
              <MenuItem value="POLICY_PERIOD">خلال الوثيقة (Per Policy)</MenuItem>
              <MenuItem value="PER_VISIT">لكل زيارة (Per Visit)</MenuItem>
              <MenuItem value="PER_SERVICE">لكل خدمة (Per Service)</MenuItem>
              <MenuItem value="DAILY">يومياً (Daily)</MenuItem>
              <MenuItem value="LIFETIME">مدى الحياة (Lifetime)</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          إلغاء
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={loading || selectedCategories.length === 0}
          startIcon={loading && <CircularProgress size={16} color="inherit" />}
        >
          {isEdit ? 'حفظ التعديلات' : (isGroup ? 'إنشاء المجموعة والقواعد' : 'إضافة المنفعة')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

UnifiedCoverageModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func.isRequired,
  policyId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  categories: PropTypes.array.isRequired,
  initialData: PropTypes.object,
  isEdit: PropTypes.bool
};

export default UnifiedCoverageModal;
