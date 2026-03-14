/**
 * DependentModal Component
 *
 * A unified Modal (Dialog) for Adding and Editing Dependent Members.
 * Features:
 * - Centered Dialog Layout
 * - MemberAvatar Integration for standardized photo display
 * - Grid Layout for compact form
 * - Handles both Create (Add) and Update (Edit) modes
 *
 * @module DependentModal
 * @since 2026-02-02
 */

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Badge,
  Typography,
  Stack,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  IconButton,
  CircularProgress,
  Grid,
  FormControlLabel,
  Switch,
  useMediaQuery,
  useTheme
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { Save as SaveIcon, Close as CloseIcon, CloudUpload as CloudUploadIcon } from '@mui/icons-material';
import dayjs from 'dayjs';
import { updateMember, addDependent, uploadPhoto, GENDERS, RELATIONSHIPS } from 'services/api/unified-members.service';
import { openSnackbar } from 'api/snackbar';
import MemberAvatar from '../../components/tba/MemberAvatar';
import { RELATIONSHIP_AR } from './member.shared';

const DependentModal = ({ open, onClose, dependent, principalId, onSave, existingDependents = [], principalGender = '' }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const isEditMode = Boolean(dependent);
  const title = isEditMode ? 'تعديل بيانات التابع' : 'إضافة تابع جديد';

  // Relationships excluded based on principal's gender:
  // - MALE principal cannot have a HUSBAND
  // - FEMALE principal cannot have a WIFE
  const GENDER_EXCLUDED_RELATIONSHIPS =
    principalGender === 'MALE' ? ['HUSBAND'] : principalGender === 'FEMALE' ? ['WIFE'] : [];

  // Relationships that can only be used once per principal
  // HUSBAND is unique only for FEMALE principals; WIFE can be multiple for MALE principals
  const UNIQUE_RELATIONSHIPS =
    principalGender === 'FEMALE' ? ['HUSBAND', 'FATHER', 'MOTHER'] : ['FATHER', 'MOTHER'];

  // Compute which unique relationships are already taken (excluding current dependent in edit mode)
  const usedUniqueRelationships = existingDependents
    .filter((d) => !isEditMode || d.id !== dependent?.id)
    .filter((d) => d.active !== false && d.status !== 'TERMINATED')
    .map((d) => d.relationship)
    .filter((r) => UNIQUE_RELATIONSHIPS.includes(r));

  // Form State
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    fullName: '',
    nationalNumber: '',
    birthDate: null,
    gender: '',
    relationship: '',
    nationality: 'ليبي',
    active: true
  });
  const [photo, setPhoto] = useState(null);
  const [photoPreview, setPhotoPreview] = useState(null);
  const [errors, setErrors] = useState({});

  // Initialize/Reset Form
  useEffect(() => {
    if (open) {
      if (isEditMode && dependent) {
        setFormData({
          fullName: dependent.fullName || dependent.nameAr || dependent.nameEn || '',
          nationalNumber: dependent.nationalNumber || dependent.civilId || '',
          birthDate: dependent.birthDate ? dayjs(dependent.birthDate) : null,
          gender: dependent.gender || '',
          relationship: dependent.relationship || '',
          nationality: dependent.nationality || 'ليبي',
          active: dependent.status === 'ACTIVE'
        });
        setPhotoPreview(dependent.photoUrl || null);
      } else {
        // Reset for Add Mode
        setFormData({
          fullName: '',
          nationalNumber: '',
          birthDate: null,
          gender: '',
          relationship: '',
          nationality: 'ليبي',
          active: true
        });
        setPhotoPreview(null);
      }
      setPhoto(null);
      setErrors({});
    }
  }, [open, dependent, isEditMode]);

  const handleChange = (prop) => (event) => {
    let value = event.target.value;
    if (prop === 'nationalNumber') {
      // Prevent entering more than 12 digits or non-digits
      value = value.replace(/\D/g, '').slice(0, 12);
    }
    setFormData({ ...formData, [prop]: value });
    if (errors[prop]) setErrors({ ...errors, [prop]: null });
  };

  const handleDateChange = (date) => {
    setFormData({ ...formData, birthDate: date });
    if (errors.birthDate) setErrors({ ...errors, birthDate: null });
  };

  const handleActiveChange = (event) => {
    setFormData({ ...formData, active: event.target.checked });
  };

  const handlePhotoChange = (event) => {
    const file = event.target.files[0];
    if (file) {
      setPhoto(file);
      setPhotoPreview(URL.createObjectURL(file));
    }
  };

  const validate = () => {
    const newErrors = {};
    if (!formData.fullName) newErrors.fullName = 'الاسم مطلوب';
    if (!formData.relationship) newErrors.relationship = 'صلة القرابة مطلوبة';
    if (!formData.gender) newErrors.gender = 'الجنس مطلوب';
    if (!formData.birthDate) newErrors.birthDate = 'تاريخ الميلاد مطلوب';

    if (formData.nationalNumber && formData.nationalNumber.length !== 12) {
      newErrors.nationalNumber = 'الرقم الوطني يجب أن يتكون من 12 خانة';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;

    setLoading(true);
    try {
      const payload = {
        fullName: formData.fullName,
        nationalNumber: formData.nationalNumber || null,
        birthDate: formData.birthDate ? formData.birthDate.format('YYYY-MM-DD') : null,
        gender: formData.gender,
        relationship: formData.relationship,
        nationality: formData.nationality,
        status: formData.active ? 'ACTIVE' : 'SUSPENDED'
      };

      let savedMember;

      if (isEditMode) {
        savedMember = await updateMember(dependent.id, payload);
        openSnackbar({
          open: true,
          message: 'تم تحديث بيانات التابع بنجاح',
          variant: 'alert',
          alert: { color: 'success' },
          close: false
        });
      } else {
        if (!principalId) {
          throw new Error('Principal ID is missing for addition.');
        }
        savedMember = await addDependent(principalId, payload);
        openSnackbar({
          open: true,
          message: 'تم إضافة التابع بنجاح',
          variant: 'alert',
          alert: { color: 'success' },
          close: false
        });
      }

      // Handle Photo Upload if changed
      if (photo && savedMember?.id) {
        await uploadPhoto(savedMember.id, photo);
      }

      onSave(); // Refresh parent
      onClose();
    } catch (error) {
      console.error('Error saving dependent:', error);
      openSnackbar({
        open: true,
        message: isEditMode ? 'تعذر تحديث البيانات' : 'تعذر إضافة التابع',
        variant: 'alert',
        alert: { color: 'error' },
        close: false
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" fullScreen={isMobile}>
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          px: '1.5rem',
          py: '1.0rem'
        }}
      >
        <Typography variant="h6" component="span" fontWeight="bold">
          {title}
        </Typography>
        <IconButton onClick={onClose} sx={{ color: 'inherit' }}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers sx={{ p: '1.5rem' }}>
        <Grid container spacing={3}>
          {/* Left Column: Photo & Status (3 columns) */}
          <Grid size={{ xs: 12, sm: 4 }} sx={{ textAlign: 'center' }}>
            <Box sx={{ position: 'relative', display: 'inline-block', mb: '1.0rem' }}>
              <Badge
                overlap="circular"
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                badgeContent={
                  <IconButton
                    color="primary"
                    aria-label="upload picture"
                    component="label"
                    sx={{
                      bgcolor: 'background.paper',
                      boxShadow: 2,
                      '&:hover': { bgcolor: 'background.paper' },
                      width: '2.25rem',
                      height: '2.25rem',
                      border: '2px solid white'
                    }}
                  >
                    <input hidden accept="image/*" type="file" onChange={handlePhotoChange} />
                    <CloudUploadIcon sx={{ fontSize: '1.25rem' }} />
                  </IconButton>
                }
              >
                <MemberAvatar
                  member={{
                    fullName: formData.fullName,
                    photoUrl: photoPreview
                  }}
                  size={120}
                  sx={{
                    fontSize: '3rem',
                    border: '4px solid',
                    borderColor: 'background.paper',
                    boxShadow: 1
                  }}
                />
              </Badge>
            </Box>

            <FormControlLabel
              control={<Switch checked={formData.active} onChange={handleActiveChange} color="success" />}
              label={formData.active ? 'نشط' : 'غير نشط'}
              sx={{ display: 'flex', justifyContent: 'center', mr: 0 }}
            />
          </Grid>

          {/* Right Column: Form Fields (8 columns) */}
          <Grid size={{ xs: 12, sm: 8 }}>
            <Stack spacing={2}>
              <TextField
                label="الاسم الكامل"
                fullWidth
                value={formData.fullName}
                onChange={handleChange('fullName')}
                error={!!errors.fullName}
                helperText={errors.fullName}
                required
              />

              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <FormControl fullWidth required error={!!errors.relationship}>
                    <InputLabel>القرابة</InputLabel>
                    <Select value={formData.relationship} onChange={handleChange('relationship')} label="القرابة">
                      {Object.keys(RELATIONSHIPS)
                        .filter((key) => !GENDER_EXCLUDED_RELATIONSHIPS.includes(key))
                        .filter((key) => !usedUniqueRelationships.includes(key) || key === formData.relationship)
                        .map((key) => (
                          <MenuItem key={key} value={key}>
                            {RELATIONSHIP_AR[key] || key}
                          </MenuItem>
                        ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <FormControl fullWidth required error={!!errors.gender}>
                    <InputLabel>الجنس</InputLabel>
                    <Select value={formData.gender} onChange={handleChange('gender')} label="الجنس">
                      <MenuItem value={GENDERS.MALE}>ذكر</MenuItem>
                      <MenuItem value={GENDERS.FEMALE}>أنثى</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>

              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <DatePicker
                    label="تاريخ الميلاد"
                    value={formData.birthDate}
                    onChange={handleDateChange}
                    disableFuture
                    slotProps={{
                      textField: {
                        fullWidth: true,
                        required: true,
                        error: !!errors.birthDate,
                        helperText: errors.birthDate
                      }
                    }}
                  />
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <TextField
                    label="رقم البطاقة الوطنية"
                    fullWidth
                    value={formData.nationalNumber}
                    onChange={handleChange('nationalNumber')}
                    inputProps={{ maxLength: 12 }}
                    error={!!errors.nationalNumber}
                    helperText={errors.nationalNumber}
                  />
                </Grid>
              </Grid>

              <TextField label="الجنسية" fullWidth value={formData.nationality} onChange={handleChange('nationality')} />
            </Stack>
          </Grid>
        </Grid>
      </DialogContent>

      <DialogActions sx={{ p: '1.0rem', bgcolor: 'background.neutral' }}>
        <Button onClick={onClose} variant="outlined" color="inherit">
          إلغاء
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
          disabled={loading}
        >
          {isEditMode ? 'حفظ التغييرات' : 'إضافة التابع'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DependentModal;

