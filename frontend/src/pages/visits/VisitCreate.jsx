import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Grid,
  Typography,
  Stack,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  InputAdornment
} from '@mui/material';
import { Save as SaveIcon, Cancel as CancelIcon, LocalHospital as LocalHospitalIcon } from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import visitsService, { VISIT_TYPES } from 'services/api/visits.service';
import { useAllMembers } from 'hooks/useMembers';
import { useAllProviders } from 'hooks/useProviders';

/**
 * Visit Create Page
 * CONTRACT: VISIT_API_CONTRACT.md - VisitCreateDto
 *
 * Required fields:
 * - memberId (number)
 * - visitDate (string "yyyy-MM-dd")
 * - doctorName (string, not blank)
 *
 * Optional fields:
 * - providerId (number)
 * - specialty (string)
 * - diagnosis (string)
 * - treatment (string)
 * - totalAmount (number)
 * - notes (string)
 * - visitType (enum, default: OUTPATIENT)
 */
const VisitCreate = () => {
  const navigate = useNavigate();
  const { data: members, loading: membersLoading } = useAllMembers();
  const { data: providers, loading: providersLoading } = useAllProviders();

  // Form state matching VisitCreateDto from contract
  const [form, setForm] = useState({
    memberId: '',
    providerId: '',
    visitDate: '',
    doctorName: '', // REQUIRED - اسم الطبيب
    specialty: '', // Optional - التخصص
    diagnosis: '', // Optional - التشخيص
    treatment: '', // Optional - العلاج
    totalAmount: '', // Optional - المبلغ الإجمالي
    notes: '', // Optional - ملاحظات
    visitType: 'OUTPATIENT' // Optional - default OUTPATIENT
  });

  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  /**
   * Validate form according to VisitCreateDto contract
   */
  const validate = () => {
    const newErrors = {};

    // Required fields per contract
    if (!form.memberId) newErrors.memberId = 'المؤمَّن عليه مطلوب';
    if (!form.visitDate) newErrors.visitDate = 'تاريخ الزيارة مطلوب';
    if (!form.doctorName || !form.doctorName.trim()) newErrors.doctorName = 'اسم الطبيب مطلوب';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validate()) return;

    setLoading(true);
    setApiError(null);

    try {
      // Build payload matching VisitCreateDto exactly
      const payload = {
        memberId: parseInt(form.memberId, 10),
        visitDate: form.visitDate,
        doctorName: form.doctorName.trim()
      };

      // Add optional fields only if provided
      if (form.providerId) payload.providerId = parseInt(form.providerId, 10);
      if (form.specialty?.trim()) payload.specialty = form.specialty.trim();
      if (form.diagnosis?.trim()) payload.diagnosis = form.diagnosis.trim();
      if (form.treatment?.trim()) payload.treatment = form.treatment.trim();
      if (form.totalAmount) payload.totalAmount = parseFloat(form.totalAmount);
      if (form.notes?.trim()) payload.notes = form.notes.trim();
      if (form.visitType) payload.visitType = form.visitType;

      await visitsService.create(payload);
      navigate('/visits');
    } catch (err) {
      console.error('Failed to create visit:', err);
      setApiError(err.response?.data?.message || err.message || 'فشل إنشاء الزيارة');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    navigate('/visits');
  };

  const breadcrumbs = [{ title: 'الزيارات', path: '/visits' }, { title: 'إضافة زيارة جديدة' }];

  return (
    <>
      <ModernPageHeader
        title="إضافة زيارة جديدة"
        subtitle="أدخل بيانات الزيارة الجديدة"
        icon={LocalHospitalIcon}
        breadcrumbs={breadcrumbs}
      />

      <MainCard>
        {apiError && (
          <Alert severity="error" sx={{ mb: '1.5rem' }} onClose={() => setApiError(null)}>
            {apiError}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            {/* Section: Basic Info */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom>
                معلومات الزيارة الأساسية
              </Typography>
            </Grid>

            {/* Member - Required */}
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControl fullWidth required error={!!errors.memberId}>
                <InputLabel>المؤمَّن عليه *</InputLabel>
                <Select value={form.memberId} onChange={handleChange('memberId')} label="المؤمَّن عليه *" disabled={membersLoading}>
                  <MenuItem value="">-- اختر المؤمَّن عليه --</MenuItem>
                  {members?.map((member) => (
                    <MenuItem key={member.id} value={member.id}>
                      {member.fullName || `مؤمن ${member.id}`}
                    </MenuItem>
                  ))}
                </Select>
                {errors.memberId && (
                  <Typography variant="caption" color="error" sx={{ mt: 0.5, ml: '1.0rem' }}>
                    {errors.memberId}
                  </Typography>
                )}
              </FormControl>
            </Grid>

            {/* Visit Date - Required */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                required
                type="date"
                label="تاريخ الزيارة *"
                value={form.visitDate}
                onChange={handleChange('visitDate')}
                error={!!errors.visitDate}
                helperText={errors.visitDate}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>

            {/* Doctor Name - Required */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                required
                label="اسم الطبيب *"
                value={form.doctorName}
                onChange={handleChange('doctorName')}
                error={!!errors.doctorName}
                helperText={errors.doctorName}
                placeholder="د. محمد أحمد"
              />
            </Grid>

            {/* Specialty - Optional */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="التخصص"
                value={form.specialty}
                onChange={handleChange('specialty')}
                placeholder="باطنية، عظام، جلدية..."
              />
            </Grid>

            {/* Provider - Optional */}
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControl fullWidth>
                <InputLabel>مقدم الخدمة</InputLabel>
                <Select value={form.providerId} onChange={handleChange('providerId')} label="مقدم الخدمة" disabled={providersLoading}>
                  <MenuItem value="">-- بدون مقدم خدمة --</MenuItem>
                  {providers?.map((provider) => (
                    <MenuItem key={provider.id} value={provider.id}>
                      {provider.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Visit Type - Optional with default */}
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControl fullWidth>
                <InputLabel>نوع الزيارة</InputLabel>
                <Select value={form.visitType} onChange={handleChange('visitType')} label="نوع الزيارة">
                  {Object.values(VISIT_TYPES).map((type) => (
                    <MenuItem key={type.value} value={type.value}>
                      {type.labelAr}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Section: Medical Info */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
                المعلومات الطبية
              </Typography>
            </Grid>

            {/* Diagnosis - Optional */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                multiline
                rows={2}
                label="التشخيص"
                value={form.diagnosis}
                onChange={handleChange('diagnosis')}
                placeholder="التشخيص الطبي..."
              />
            </Grid>

            {/* Treatment - Optional */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                multiline
                rows={2}
                label="العلاج"
                value={form.treatment}
                onChange={handleChange('treatment')}
                placeholder="الخطة العلاجية..."
              />
            </Grid>

            {/* Total Amount - Optional */}
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                type="number"
                label="المبلغ الإجمالي"
                value={form.totalAmount}
                onChange={handleChange('totalAmount')}
                InputProps={{
                  endAdornment: <InputAdornment position="end">د.ل</InputAdornment>
                }}
                inputProps={{ min: 0, step: 0.01 }}
              />
            </Grid>

            {/* Notes - Optional */}
            <Grid size={12}>
              <TextField
                fullWidth
                multiline
                rows={3}
                label="الملاحظات"
                value={form.notes}
                onChange={handleChange('notes')}
                placeholder="ملاحظات إضافية..."
              />
            </Grid>

            {/* Actions */}
            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                <Button variant="outlined" startIcon={<CancelIcon />} onClick={handleCancel} disabled={loading}>
                  إلغاء
                </Button>
                <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
                  {loading ? 'جارٍ الحفظ...' : 'حفظ'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </form>
      </MainCard>
    </>
  );
};

export default VisitCreate;
