import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  Divider,
  Grid,
  InputAdornment,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';

import AssignmentIcon from '@mui/icons-material/Assignment';
import SearchIcon from '@mui/icons-material/Search';

import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import { lookupMedicalServices } from 'services/api/medical-services.service';
import providerMappingService from 'services/medical/providerMapping.service';
import medicalServicesMappingService from 'services/medical/medicalServicesMapping.service';

const statCards = [
  { key: 'total', label: 'إجمالي الخدمات الخام' },
  { key: 'pending', label: 'قيد المعالجة' },
  { key: 'mapped', label: 'تم ربطها' },
  { key: 'rejected', label: 'مرفوضة' },
  { key: 'providersWithRawServices', label: 'مزودون لديهم خدمات' },
  { key: 'medicalServicesTotal', label: 'الخدمات الطبية الموحدة' }
];

const normalizeError = (error, fallback) =>
  error?.response?.data?.message || error?.message || fallback;

const MedicalServicesPage = () => {
  const qc = useQueryClient();

  const [selectedProvider, setSelectedProvider] = useState(null);
  const [rawSearch, setRawSearch] = useState('');
  const [selectedRawIds, setSelectedRawIds] = useState([]);

  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newCategoryId, setNewCategoryId] = useState('');

  const [serviceSearch, setServiceSearch] = useState('');
  const [existingService, setExistingService] = useState(null);

  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['medical-services-mapping-stats'],
    queryFn: () => medicalServicesMappingService.getStats(),
    staleTime: 30 * 1000
  });

  const { data: providers = [], isLoading: providersLoading } = useQuery({
    queryKey: ['providers-with-active-contracts'],
    queryFn: () => providerMappingService.getProvidersWithActiveContracts(),
    staleTime: 5 * 60 * 1000
  });

  const { data: rawServices = [], isLoading: rawLoading, refetch: refetchRaw } = useQuery({
    queryKey: ['provider-raw-services-pending', selectedProvider?.id],
    queryFn: () => providerMappingService.getRawServices(selectedProvider.id, 'PENDING'),
    enabled: !!selectedProvider,
    staleTime: 30 * 1000
  });

  const { data: categoryOptions = [] } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: () => getAllMedicalCategories(),
    staleTime: 10 * 60 * 1000
  });

  const { data: serviceOptions = [], isLoading: serviceLookupLoading } = useQuery({
    queryKey: ['medical-services-lookup', serviceSearch],
    queryFn: () => lookupMedicalServices({ q: serviceSearch.trim() }),
    enabled: serviceSearch.trim().length >= 1,
    staleTime: 30 * 1000
  });

  const filteredRawServices = useMemo(() => {
    const q = rawSearch.trim().toLowerCase();
    if (!q) return rawServices;
    return rawServices.filter((row) => {
      const rawName = row.rawName?.toLowerCase() || '';
      const normalized = row.normalizedName?.toLowerCase() || '';
      const code = row.code?.toLowerCase() || '';
      return rawName.includes(q) || normalized.includes(q) || code.includes(q);
    });
  }, [rawServices, rawSearch]);

  // Use a Set for O(1) membership checks during render
  const selectedRawIdSet = useMemo(() => new Set(selectedRawIds), [selectedRawIds]);

  const allVisibleSelected =
    filteredRawServices.length > 0 &&
    filteredRawServices.every((row) => selectedRawIdSet.has(row.id));

  const handleToggleRaw = (id) => {
    setSelectedRawIds((prev) =>
      selectedRawIdSet.has(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const handleToggleAllVisible = () => {
    if (allVisibleSelected) {
      const visibleIds = new Set(filteredRawServices.map((row) => row.id));
      setSelectedRawIds((prev) => prev.filter((id) => !visibleIds.has(id)));
      return;
    }

    const merged = new Set(selectedRawIds);
    filteredRawServices.forEach((row) => merged.add(row.id));
    setSelectedRawIds(Array.from(merged));
  };

  const resetSelection = () => setSelectedRawIds([]);

  const invalidateAfterSuccess = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['provider-raw-services-pending'] }),
      qc.invalidateQueries({ queryKey: ['medical-services-mapping-stats'] }),
      qc.invalidateQueries({ queryKey: ['medical-services-lookup'] })
    ]);
  };

  const createAndMapMutation = useMutation({
    mutationFn: (payload) => medicalServicesMappingService.createAndMap(payload),
    onSuccess: async (mappedRows) => {
      await invalidateAfterSuccess();
      setActionError('');
      setActionSuccess(`تم إنشاء الخدمة وربط ${mappedRows?.length ?? 0} سجل بنجاح.`);
      setNewCode('');
      setNewName('');
      setNewCategoryId('');
      resetSelection();
    },
    onError: (err) => {
      setActionSuccess('');
      setActionError(normalizeError(err, 'تعذر إنشاء الخدمة وربطها.'));
    }
  });

  const linkAndMapMutation = useMutation({
    mutationFn: (payload) => medicalServicesMappingService.linkAndMap(payload),
    onSuccess: async (mappedRows) => {
      await invalidateAfterSuccess();
      setActionError('');
      setActionSuccess(`تم ربط ${mappedRows?.length ?? 0} سجل بالخدمة المختارة.`);
      setExistingService(null);
      resetSelection();
    },
    onError: (err) => {
      setActionSuccess('');
      setActionError(normalizeError(err, 'تعذر ربط الخدمات المحددة.'));
    }
  });

  const createDisabled =
    !newCode.trim() ||
    !newName.trim() ||
    !newCategoryId ||
    selectedRawIds.length === 0 ||
    createAndMapMutation.isPending;

  const linkDisabled =
    !existingService?.id ||
    selectedRawIds.length === 0 ||
    linkAndMapMutation.isPending;

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={3}>
        <AssignmentIcon color="primary" />
        <Typography variant="h5" fontWeight={700}>
          الخدمات الطبية
        </Typography>
      </Stack>

      <Alert severity="info" sx={{ mb: 3 }}>
        اختر مزودًا ثم حدد الخدمات الخام المطلوب ربطها. يمكنك إنشاء خدمة جديدة وربطها مباشرة، أو ربط المحدد بخدمة موجودة من القاموس الموحد.
      </Alert>

      {actionError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {actionError}
        </Alert>
      )}

      {actionSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {actionSuccess}
        </Alert>
      )}

      <Grid container spacing={2} mb={3}>
        {statCards.map((card) => (
          <Grid item xs={12} sm={6} md={4} lg={2} key={card.key}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="caption" color="text.secondary">
                  {card.label}
                </Typography>
                <Typography variant="h5" fontWeight={700} mt={0.5}>
                  {statsLoading ? <Skeleton width={50} /> : stats?.[card.key] ?? 0}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mb={2} alignItems="flex-end">
              <Autocomplete
                sx={{ minWidth: 320, flex: 1 }}
                options={providers}
                loading={providersLoading}
                value={selectedProvider}
                onChange={(_, value) => {
                  setSelectedProvider(value);
                  setSelectedRawIds([]);
                }}
                getOptionLabel={(o) => `${o.name ?? ''}${o.code ? ` (${o.code})` : ''}`}
                isOptionEqualToValue={(o, v) => o.id === v.id}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="اختر المزود"
                    size="small"
                    InputProps={{
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {providersLoading && <CircularProgress size={16} />}
                          {params.InputProps.endAdornment}
                        </>
                      )
                    }}
                  />
                )}
              />

              <TextField
                size="small"
                label="بحث في الخدمات الخام"
                value={rawSearch}
                onChange={(e) => setRawSearch(e.target.value)}
                sx={{ minWidth: 250 }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  )
                }}
              />

              <Button variant="outlined" onClick={() => refetchRaw()} disabled={!selectedProvider}>
                تحديث
              </Button>
            </Stack>

            {!selectedProvider ? (
              <Alert severity="info">الرجاء اختيار مزود لعرض الخدمات الخام.</Alert>
            ) : rawLoading ? (
              <Stack spacing={1}>
                {[1, 2, 3, 4].map((i) => (
                  <Skeleton key={i} variant="rectangular" height={44} />
                ))}
              </Stack>
            ) : filteredRawServices.length === 0 ? (
              <Alert severity="warning">لا توجد خدمات خام معلقة لهذا المزود وفق المرشح الحالي.</Alert>
            ) : (
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ '& th': { fontWeight: 700, bgcolor: 'grey.50' } }}>
                      <TableCell padding="checkbox">
                        <Checkbox
                          checked={allVisibleSelected}
                          indeterminate={!allVisibleSelected && selectedRawIds.length > 0}
                          onChange={handleToggleAllVisible}
                        />
                      </TableCell>
                      <TableCell>#</TableCell>
                      <TableCell>الاسم الخام</TableCell>
                      <TableCell>الاسم المُعالَج</TableCell>
                      <TableCell>الكود</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredRawServices.map((row) => (
                      <TableRow key={row.id} hover selected={selectedRawIdSet.has(row.id)}>
                        <TableCell padding="checkbox">
                          <Checkbox
                            checked={selectedRawIdSet.has(row.id)}
                            onChange={() => handleToggleRaw(row.id)}
                          />
                        </TableCell>
                        <TableCell>{row.id}</TableCell>
                        <TableCell sx={{ maxWidth: 260, wordBreak: 'break-word' }}>{row.rawName}</TableCell>
                        <TableCell sx={{ color: 'text.secondary', fontSize: 12 }}>
                          {row.normalizedName || '—'}
                        </TableCell>
                        <TableCell>{row.code || '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            <Typography variant="body2" color="text.secondary" mt={1.5}>
              عدد السجلات المحددة: {selectedRawIds.length}
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="h6" fontWeight={700} mb={2}>
              إجراءات الربط
            </Typography>

            <Typography variant="subtitle2" mb={1}>
              1) إنشاء خدمة جديدة وربطها
            </Typography>

            <Stack spacing={1.5} mb={2}>
              <TextField
                size="small"
                label="كود الخدمة الجديدة"
                value={newCode}
                onChange={(e) => setNewCode(e.target.value)}
              />
              <TextField
                size="small"
                label="اسم الخدمة الجديدة"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
              />
              <TextField
                select
                size="small"
                label="التصنيف"
                value={newCategoryId}
                onChange={(e) => setNewCategoryId(e.target.value)}
                SelectProps={{ native: true }}
              >
                <option value="">اختر التصنيف</option>
                {categoryOptions.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.nameAr || cat.nameEn || cat.code}
                  </option>
                ))}
              </TextField>
              <Button
                variant="contained"
                onClick={() => {
                  setActionError('');
                  setActionSuccess('');
                  createAndMapMutation.mutate({
                    code: newCode.trim(),
                    name: newName.trim(),
                    categoryId: Number(newCategoryId),
                    rawServiceIds: selectedRawIds
                  });
                }}
                disabled={createDisabled}
              >
                {createAndMapMutation.isPending ? 'جاري التنفيذ...' : 'إنشاء وربط'}
              </Button>
            </Stack>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" mb={1}>
              2) ربط بخدمة موجودة
            </Typography>

            <Stack spacing={1.5}>
              <Autocomplete
                options={serviceOptions}
                loading={serviceLookupLoading}
                value={existingService}
                onChange={(_, value) => setExistingService(value)}
                inputValue={serviceSearch}
                onInputChange={(_, value) => setServiceSearch(value)}
                getOptionLabel={(o) => `${o.code ?? ''} - ${o.name ?? ''}`}
                isOptionEqualToValue={(o, v) => o.id === v.id}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    size="small"
                    label="ابحث عن خدمة موحدة"
                    InputProps={{
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {serviceLookupLoading && <CircularProgress size={16} />}
                          {params.InputProps.endAdornment}
                        </>
                      )
                    }}
                  />
                )}
              />

              <Button
                variant="outlined"
                onClick={() => {
                  setActionError('');
                  setActionSuccess('');
                  linkAndMapMutation.mutate({
                    medicalServiceId: existingService.id,
                    rawServiceIds: selectedRawIds
                  });
                }}
                disabled={linkDisabled}
              >
                {linkAndMapMutation.isPending ? 'جاري التنفيذ...' : 'ربط المحدد'}
              </Button>
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default MedicalServicesPage;
