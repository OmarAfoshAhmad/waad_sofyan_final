import {
  Alert,
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { MedicalServices as MedicalServicesIcon } from '@mui/icons-material';

const CLAIM_CONTEXT_LABELS = {
  OUTPATIENT: 'عيادات خارجية',
  INPATIENT: 'إيواء',
  MATERNITY: 'ولادة',
  EMERGENCY: 'طوارئ',
  DENTAL: 'أسنان',
  OPTICAL: 'بصريات'
};

const claimContextLabel = (code) => CLAIM_CONTEXT_LABELS[String(code || '').toUpperCase()] || 'سياق المطالبة الحالي';

const normalizeArabicSearch = (value = '') =>
  String(value || '')
    .toLowerCase()
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/[\u064B-\u065F\u0670]/g, '')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();

/**
 * Lets staff add a brand-new shared medical service to the catalog on the fly,
 * directly from claim entry. It does not create a provider-contract price item;
 * the entered amount is the current unit price for the active claim line, and
 * quantity remains editable.
 */
export function CustomServiceDialog({
  open,
  onClose,
  medicalCategories,
  customServiceData,
  customServiceError,
  addingCustomService,
  claimContextCode,
  onFieldChange,
  onClearError,
  onSubmit
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
        <MedicalServicesIcon color="primary" />
        إضافة خدمة طبية عامة للمطالبات
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {customServiceError && (
            <Alert severity="error" onClose={onClearError}>
              {customServiceError}
            </Alert>
          )}

          <Autocomplete
            fullWidth
            options={medicalCategories}
            value={medicalCategories.find((cat) => String(cat.id) === String(customServiceData.categoryId)) || null}
            onChange={(event, newValue) => onFieldChange('categoryId', newValue?.id || '')}
            getOptionLabel={(option) => `${option.nameAr || option.name || ''}${option.code ? ` (${option.code})` : ''}`}
            filterOptions={(options, state) => {
              const query = normalizeArabicSearch(state.inputValue);
              if (!query) return options;
              return options.filter((option) =>
                [option.nameAr, option.name, option.nameEn, option.code]
                  .filter(Boolean)
                  .some((value) => normalizeArabicSearch(value).includes(query))
              );
            }}
            isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
            renderOption={(props, option) => (
              <li {...props} key={option.id}>
                <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                  <Typography variant="body2" fontWeight={700}>
                    {option.nameAr || option.name || ''}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {option.code || '-'}
                  </Typography>
                </Box>
              </li>
            )}
            renderInput={(params) => (
              <TextField
                {...params}
                required
                label="التصنيف الطبي الموحد"
                placeholder="اختر التصنيف المعتمد للقواعد والتغطية..."
                helperText="هذا التصنيف هو المرجع الوحيد للتغطية والسقوف؛ لا يوجد تصنيف رئيسي/فرعي في النظام الحديث."
              />
            )}
          />

          <TextField
            fullWidth
            required
            label="اسم الخدمة الطبية"
            placeholder="مثال: كشف طبيب عام، تحليل دم كامل..."
            value={customServiceData.serviceName}
            onChange={(e) => onFieldChange('serviceName', e.target.value)}
          />

          <TextField
            fullWidth
            label="رمز الخدمة (تلقائي/اختياري)"
            placeholder="سيتم إنشاؤه تلقائياً إذا ترك فارغاً"
            value={customServiceData.serviceCode}
            onChange={(e) => onFieldChange('serviceCode', e.target.value)}
            helperText="رمز داخلي فريد في الفهرس الطبي العام؛ اتركه فارغاً ليولّده النظام تلقائياً."
          />

          <TextField
            fullWidth
            required
            type="number"
            label="سعر الوحدة الحالي (دينار ليبي)"
            placeholder="0.00"
            value={customServiceData.contractPrice}
            onChange={(e) => onFieldChange('contractPrice', e.target.value)}
            InputProps={{
              endAdornment: (
                <Typography variant="body2" color="text.secondary">
                  LYD
                </Typography>
              )
            }}
            helperText={`ستُضاف كخدمة عامة بسعر وحدة قابل للكمية، وسياقها الافتراضي: ${claimContextLabel(claimContextCode)}`}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={addingCustomService}>
          إلغاء
        </Button>
        <Button
          variant="contained"
          onClick={onSubmit}
          disabled={
            addingCustomService || !customServiceData.categoryId || !customServiceData.serviceName || !customServiceData.contractPrice
          }
        >
          {addingCustomService ? <CircularProgress size={24} color="inherit" /> : 'إضافة الخدمة العامة واختيارها'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
