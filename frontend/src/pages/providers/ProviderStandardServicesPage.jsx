/**
 * Bulk-provisioning of standard (invoice-priced) services -- pharmacy and
 * optics services with no fixed contract price list -- across many
 * providers at once. Preview is mandatory before Apply is enabled: the
 * scope (provider type / all active / selected providers) can match far
 * more providers than intended, and this is the only place a clerk can
 * see the effect before it is written.
 */
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';

import {
  Autocomplete,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Select,
  Stack,
  TextField,
  Typography,
  Button,
  Alert
} from '@mui/material';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';

import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { providerStandardServicesService } from 'services/api/providerStandardServices.service';
import { providersService } from 'services/api/providers.service';

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

const SummaryGrid = ({ summary, title }) => (
  <Box sx={{ mt: 2 }}>
    <Typography variant="subtitle2" sx={{ mb: 1 }}>{title}</Typography>
    <Grid container spacing={1}>
      {[
        ['مرافق مطابقة', summary.providersMatched],
        ['لا تحتاج تغييراً', summary.providersAlreadyComplete],
        ['تحتاج تحديثاً', summary.providersNeedingChanges],
        ['خدمات ستُضاف', summary.assignmentsToCreate],
        ['خدمات ستُعاد تفعيلها', summary.assignmentsToReactivate],
        ['خدمات مفعّلة أصلاً', summary.assignmentsAlreadyActive]
      ].map(([label, value]) => (
        <Grid size={{ xs: 6, sm: 4 }} key={label}>
          <Card variant="outlined">
            <CardContent sx={{ p: '0.75rem !important', textAlign: 'center' }}>
              <Typography variant="h5">{value}</Typography>
              <Typography variant="caption" color="text.secondary">{label}</Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  </Box>
);

export default function ProviderStandardServicesPage() {
  const navigate = useNavigate();
  const [selectedCodes, setSelectedCodes] = useState([]);
  const [scope, setScope] = useState('PROVIDER_TYPES');
  const [providerTypes, setProviderTypes] = useState([]);
  const [selectedProviders, setSelectedProviders] = useState([]);
  const [providerOptions, setProviderOptions] = useState([]);
  const [providerSearchLoading, setProviderSearchLoading] = useState(false);
  const [previewResult, setPreviewResult] = useState(null);
  const [applyResult, setApplyResult] = useState(null);

  const { data: standardServices = [], isLoading: loadingServices } = useQuery({
    queryKey: ['provider-standard-services-catalog'],
    queryFn: providerStandardServicesService.list
  });

  const buildRequest = () => ({
    serviceCodes: selectedCodes,
    scope,
    providerTypes: scope === 'PROVIDER_TYPES' ? providerTypes : undefined,
    providerIds: scope === 'SELECTED_PROVIDERS' ? selectedProviders.map((p) => p.id) : undefined
  });

  const previewMutation = useMutation({
    mutationFn: () => providerStandardServicesService.previewProvisioning(buildRequest()),
    onSuccess: (data) => {
      setPreviewResult(data);
      setApplyResult(null);
    }
  });

  const applyMutation = useMutation({
    mutationFn: () => providerStandardServicesService.applyProvisioning(buildRequest()),
    onSuccess: (data) => setApplyResult(data)
  });

  const canPreview = selectedCodes.length > 0
    && (scope !== 'PROVIDER_TYPES' || providerTypes.length > 0)
    && (scope !== 'SELECTED_PROVIDERS' || selectedProviders.length > 0);

  const requestSignature = useMemo(
    () => JSON.stringify(buildRequest()),
    [selectedCodes, scope, providerTypes, selectedProviders]
  );
  const [previewedSignature, setPreviewedSignature] = useState(null);
  const canApply = previewResult != null && previewedSignature === requestSignature;

  const handlePreview = () => {
    setPreviewedSignature(requestSignature);
    previewMutation.mutate();
  };

  const handleProviderSearch = async (query) => {
    if (!query || query.trim().length < 2) {
      setProviderOptions([]);
      return;
    }
    setProviderSearchLoading(true);
    try {
      const results = await providersService.search(query);
      setProviderOptions(Array.isArray(results) ? results : []);
    } finally {
      setProviderSearchLoading(false);
    }
  };

  return (
    <Box sx={{ p: 2 }}>
      <ModernPageHeader
        title="الخدمات المهنية القياسية للمرافق"
        subtitle="تطبيق خدمات مسعّرة بمبلغ الفاتورة يدوياً (صيدليات، بصريات) على عدة مرافق دفعة واحدة"
        icon={<MedicalServicesIcon />}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'مقدمي الخدمات', path: '/providers' },
          { label: 'الخدمات المهنية القياسية' }
        ]}
      />

      <MainCard sx={{ mt: 2 }} title="1. الخدمات القياسية">
        {loadingServices ? (
          <CircularProgress size={24} />
        ) : (
          <Grid container spacing={1.5}>
            {standardServices.map((service) => {
              const checked = selectedCodes.includes(service.code);
              return (
                <Grid size={{ xs: 12, sm: 6, md: 3 }} key={service.code}>
                  <Card
                    variant="outlined"
                    onClick={() => setSelectedCodes((prev) => (
                      checked ? prev.filter((c) => c !== service.code) : [...prev, service.code]
                    ))}
                    sx={{
                      cursor: 'pointer',
                      borderColor: checked ? 'primary.main' : undefined,
                      borderWidth: checked ? 2 : 1,
                      bgcolor: checked ? 'action.selected' : undefined
                    }}
                  >
                    <CardContent>
                      <Stack direction="row" alignItems="center" spacing={1}>
                        {checked && <CheckCircleIcon color="primary" fontSize="small" />}
                        <Typography variant="subtitle2">{service.name}</Typography>
                      </Stack>
                      <Chip size="small" label={service.categoryCode} sx={{ mt: 1 }} />
                      <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 1 }}>
                        القيمة تُدخَل يدوياً في المطالبة — لا توجد قائمة أسعار ثابتة
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}
      </MainCard>

      <MainCard sx={{ mt: 2 }} title="2. النطاق">
        <RadioGroup row value={scope} onChange={(e) => { setScope(e.target.value); setPreviewResult(null); }}>
          <FormControlLabel value="PROVIDER_TYPES" control={<Radio />} label="حسب نوع المرفق" />
          <FormControlLabel value="ALL_ACTIVE" control={<Radio />} label="كل المرافق النشطة" />
          <FormControlLabel value="SELECTED_PROVIDERS" control={<Radio />} label="مرافق محددة" />
        </RadioGroup>

        {scope === 'PROVIDER_TYPES' && (
          <FormControl fullWidth sx={{ mt: 1, maxWidth: 420 }} size="small">
            <InputLabel id="provider-types-label">أنواع المرافق</InputLabel>
            <Select
              labelId="provider-types-label"
              multiple
              value={providerTypes}
              label="أنواع المرافق"
              onChange={(e) => { setProviderTypes(e.target.value); setPreviewResult(null); }}
              renderValue={(selected) => selected
                .map((v) => PROVIDER_TYPES.find((t) => t.value === v)?.label || v).join('، ')}
            >
              {PROVIDER_TYPES.map((type) => (
                <MenuItem key={type.value} value={type.value}>{type.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        )}

        {scope === 'SELECTED_PROVIDERS' && (
          <Autocomplete
            multiple
            sx={{ mt: 1, maxWidth: 520 }}
            options={providerOptions}
            value={selectedProviders}
            getOptionLabel={(option) => option.name || ''}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            loading={providerSearchLoading}
            onChange={(_, value) => { setSelectedProviders(value); setPreviewResult(null); }}
            onInputChange={(_, value) => handleProviderSearch(value)}
            renderInput={(params) => (
              <TextField {...params} size="small" label="ابحث عن مرافق" placeholder="اسم المرفق..." />
            )}
          />
        )}
      </MainCard>

      <MainCard sx={{ mt: 2 }} title="3. معاينة وتطبيق">
        <Stack direction="row" spacing={2}>
          <Button
            variant="outlined"
            startIcon={<PlayCircleOutlineIcon />}
            disabled={!canPreview || previewMutation.isPending}
            onClick={handlePreview}
          >
            {previewMutation.isPending ? 'جارٍ المعاينة…' : 'معاينة قبل التطبيق'}
          </Button>
          <Button
            variant="contained"
            disabled={!canApply || applyMutation.isPending}
            onClick={() => applyMutation.mutate()}
          >
            {applyMutation.isPending ? 'جارٍ التطبيق…' : 'تطبيق الخدمات القياسية'}
          </Button>
        </Stack>

        {previewMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {previewMutation.error?.response?.data?.message || 'تعذرت المعاينة. تحقق من البيانات المُدخلة.'}
          </Alert>
        )}
        {applyMutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {applyMutation.error?.response?.data?.message || 'تعذر التطبيق. حاول مرة أخرى.'}
          </Alert>
        )}

        {previewResult && !applyResult && <SummaryGrid summary={previewResult} title="نتيجة المعاينة (لم يُحفظ شيء بعد)" />}
        {applyResult && (
          <>
            <Alert severity="success" sx={{ mt: 2 }}>تم التطبيق بنجاح.</Alert>
            <SummaryGrid summary={applyResult} title="نتيجة التطبيق" />
            <Button sx={{ mt: 2 }} onClick={() => navigate('/providers')}>العودة إلى قائمة المرافق</Button>
          </>
        )}
      </MainCard>
    </Box>
  );
}
