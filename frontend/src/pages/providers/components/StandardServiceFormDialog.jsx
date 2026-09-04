/**
 * Create/edit form for a single standard (invoice-priced, MANUAL_AMOUNT)
 * professional service -- the catalog entry itself, distinct from
 * assigning/revoking it across providers (the rest of
 * ProviderStandardServicesPage). code is immutable once created (mirrors
 * MedicalService.code on the backend) so it is only editable while creating.
 */
import { useEffect, useState } from 'react';
import {
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Alert
} from '@mui/material';

const PROVIDER_TYPES = [
  { value: 'HOSPITAL', label: 'مستشفى' },
  { value: 'CLINIC', label: 'عيادة تخصصية' },
  { value: 'CLINIC_DEN', label: 'عيادة أسنان' },
  { value: 'LAB', label: 'مختبر طبي' },
  { value: 'PHARMACY', label: 'صيدلية' },
  { value: 'RADIOLOGY', label: 'مركز أشعة' },
  { value: 'PHYSIOTHERAPY', label: 'علاج طبيعي' },
  { value: 'OPTICS', label: 'بصريات وعيون' }
];

const emptyForm = {
  code: '',
  nameAr: '',
  nameEn: '',
  categoryId: null,
  active: true,
  defaultProviderTypes: []
};

export default function StandardServiceFormDialog({ open, onClose, onSubmit, submitting, error, service, categories = [] }) {
  const isEdit = Boolean(service);
  const [form, setForm] = useState(emptyForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(
      service
        ? {
            code: service.code,
            nameAr: service.nameAr || service.name || '',
            nameEn: service.nameEn || '',
            categoryId: service.categoryId ?? null,
            active: service.active,
            defaultProviderTypes: service.defaultProviderTypes || []
          }
        : emptyForm
    );
  }, [open, service]);

  const selectedCategory = categories.find((c) => c.id === form.categoryId) || null;
  const selectedProviderTypes = PROVIDER_TYPES.filter((t) => form.defaultProviderTypes.includes(t.value));

  const isValid = form.code.trim() && form.nameAr.trim() && form.categoryId;

  const handleSubmit = () => {
    setTouched(true);
    if (!isValid) return;
    onSubmit({
      ...(isEdit ? {} : { code: form.code.trim() }),
      nameAr: form.nameAr.trim(),
      nameEn: form.nameEn?.trim() || null,
      categoryId: form.categoryId,
      ...(isEdit ? { active: form.active } : {}),
      defaultProviderTypes: form.defaultProviderTypes
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth dir="rtl">
      <DialogTitle>{isEdit ? 'تعديل خدمة مهنية قياسية' : 'إضافة خدمة مهنية قياسية جديدة'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="رمز الخدمة"
            value={form.code}
            disabled={isEdit}
            required
            error={touched && !form.code.trim()}
            helperText={isEdit ? 'لا يمكن تغيير الرمز بعد الإنشاء' : 'مثال: SYS-DRUG-CARDIAC'}
            onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
            fullWidth
            size="small"
          />
          <TextField
            label="الاسم بالعربية"
            value={form.nameAr}
            required
            error={touched && !form.nameAr.trim()}
            onChange={(e) => setForm((f) => ({ ...f, nameAr: e.target.value }))}
            fullWidth
            size="small"
          />
          <TextField
            label="الاسم بالإنجليزية (اختياري)"
            value={form.nameEn}
            onChange={(e) => setForm((f) => ({ ...f, nameEn: e.target.value }))}
            fullWidth
            size="small"
          />
          <Autocomplete
            options={categories}
            value={selectedCategory}
            getOptionLabel={(c) => c.nameAr || c.name || ''}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            onChange={(_, value) => setForm((f) => ({ ...f, categoryId: value?.id ?? null }))}
            renderInput={(params) => (
              <TextField
                {...params}
                label="التصنيف الطبي"
                required
                error={touched && !form.categoryId}
                size="small"
              />
            )}
          />
          <Autocomplete
            multiple
            options={PROVIDER_TYPES}
            value={selectedProviderTypes}
            getOptionLabel={(t) => t.label}
            isOptionEqualToValue={(a, b) => a.value === b.value}
            onChange={(_, values) => setForm((f) => ({ ...f, defaultProviderTypes: values.map((v) => v.value) }))}
            renderInput={(params) => (
              <TextField
                {...params}
                label="أنواع المرافق الافتراضية (تُقترح تلقائياً)"
                size="small"
                helperText="عند إنشاء مرفق جديد من هذه الأنواع، تُسنَد له هذه الخدمة تلقائياً"
              />
            )}
          />
          {isEdit && (
            <FormControlLabel
              control={
                <Switch
                  checked={form.active}
                  onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
                />
              }
              label="مفعّلة"
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>إلغاء</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={submitting}>
          {submitting ? 'جارٍ الحفظ…' : isEdit ? 'حفظ التعديلات' : 'إنشاء الخدمة'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
