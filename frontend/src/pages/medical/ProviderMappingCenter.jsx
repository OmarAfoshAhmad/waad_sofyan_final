/**
 * Provider Mapping Center
 *
 * Features:
 *  - Provider selector dropdown (only providers with active contracts)
 *  - Table of raw services (default: status=PENDING)
 *  - Auto Match button per row
 *  - Manual Map button (opens modal with searchable medical_services list)
 *  - Reject button per row
 *  - Confirmed mappings automatically register the raw name as an alias
 *    in the unified medical catalog (ent_service_aliases).
 *
 * Access: SUPER_ADMIN, DATA_ENTRY
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  MenuItem,
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
  Tooltip,
  Typography
} from '@mui/material';

import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import BlockIcon from '@mui/icons-material/Block';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import SearchIcon from '@mui/icons-material/Search';
import MapIcon from '@mui/icons-material/Map';

import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import { lookupMedicalServices } from 'services/api/medical-services.service';
import providerMappingService from 'services/medical/providerMapping.service';

// ─── Status chip colours ────────────────────────────────────────────────────

const STATUS_COLOR = {
  PENDING: 'warning',
  AUTO_MATCHED: 'info',
  MANUAL_CONFIRMED: 'success',
  REJECTED: 'error'
};

const STATUS_LABEL = {
  PENDING: 'معلق',
  AUTO_MATCHED: 'مطابقة تلقائية',
  MANUAL_CONFIRMED: 'مؤكد يدوياً',
  REJECTED: 'مرفوض'
};

// ─── Component ───────────────────────────────────────────────────────────────

const ProviderMappingCenter = () => {
  const qc = useQueryClient();

  const [selectedProvider, setSelectedProvider] = useState(null);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [manualMapRow, setManualMapRow] = useState(null);      // raw service being manually mapped
  const [serviceSearch, setServiceSearch] = useState('');
  const [serviceCategoryId, setServiceCategoryId] = useState('');
  const [selectedService, setSelectedService] = useState(null); // chosen medical service in modal

  // ── Load providers with active contracts only ────────────────────────────
  const { data: providers = [], isLoading: providersLoading } = useQuery({
    queryKey: ['providers-with-active-contracts'],
    queryFn: () => providerMappingService.getProvidersWithActiveContracts(),
    staleTime: 5 * 60 * 1000
  });

  // ── Load raw services ─────────────────────────────────────────────────────
  const { data: rawServices = [], isLoading: rawLoading, refetch } = useQuery({
    queryKey: ['provider-raw-services', selectedProvider?.id, statusFilter],
    queryFn: () => providerMappingService.getRawServices(selectedProvider.id, statusFilter),
    enabled: !!selectedProvider,
    staleTime: 30 * 1000
  });

  // ── Load categories for manual-map filter ─────────────────────────────────
  const { data: categoryOptions = [] } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: () => getAllMedicalCategories(),
    staleTime: 10 * 60 * 1000
  });

  // ── Category-aware lookup for manual mapping ──────────────────────────────
  const { data: filteredMedical = [] } = useQuery({
    queryKey: ['medical-services-lookup', serviceSearch, serviceCategoryId],
    queryFn: () =>
      lookupMedicalServices({
        q: serviceSearch?.trim() || undefined,
        categoryId: serviceCategoryId ? Number(serviceCategoryId) : undefined
      }),
    enabled: !!manualMapRow,
    staleTime: 30 * 1000
  });

  // ── Mutations ─────────────────────────────────────────────────────────────
  const autoMatchMutation = useMutation({
    mutationFn: (rawId) => providerMappingService.autoMatch(rawId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-raw-services'] })
  });

  const manualMapMutation = useMutation({
    mutationFn: ({ rawId, medicalServiceId }) =>
      providerMappingService.manualMap(rawId, medicalServiceId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['provider-raw-services'] });
      setManualMapRow(null);
      setSelectedService(null);
      setServiceSearch('');
      setServiceCategoryId('');
    }
  });

  const rejectMutation = useMutation({
    mutationFn: (rawId) => providerMappingService.reject(rawId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-raw-services'] })
  });

  // ── Handlers ─────────────────────────────────────────────────────────────
  const handleConfirmManualMap = () => {
    if (!manualMapRow || !selectedService) return;
    manualMapMutation.mutate({
      rawId: manualMapRow.id,
      medicalServiceId: selectedService.id
    });
  };

  const mutationError =
    autoMatchMutation.error?.response?.data?.message ||
    manualMapMutation.error?.response?.data?.message ||
    rejectMutation.error?.response?.data?.message;

  // ─────────────────────────────────────────────────────────────────────────
  // Render
  // ─────────────────────────────────────────────────────────────────────────
  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Stack direction="row" alignItems="center" spacing={1} mb={3}>
        <MapIcon color="primary" />
        <Typography variant="h5" fontWeight={700}>
          مركز تعيين خدمات المزودين
        </Typography>
      </Stack>

      {/* Info: alias registration notice */}
      <Alert severity="info" icon={<InfoOutlinedIcon />} sx={{ mb: 2 }}>
        عند تأكيد ربط خدمة (تلقائيًا أو يدويًا)، يُضاف اسم الخدمة الخام تلقائيًا كاسم بديل (Alias) في القاموس الموحد للخدمات الطبية.
        القائمة تعرض فقط مقدمي الخدمة الذين لديهم عقد نشط.
      </Alert>

      {/* Error Banner */}
      {mutationError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {mutationError}
        </Alert>
      )}

      {/* Controls Row */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mb={3} alignItems="flex-end">
        {/* Provider selector */}
        <Autocomplete
          sx={{ minWidth: 300 }}
          options={providers}
          loading={providersLoading}
          value={selectedProvider}
          onChange={(_, v) => setSelectedProvider(v)}
          getOptionLabel={(o) => `${o.name ?? ''}${o.code ? ` (${o.code})` : ''}${o.providerType ? ` — ${o.providerType}` : ''}`}
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

        {/* Status filter */}
        <TextField
          select
          label="الحالة"
          size="small"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          sx={{ minWidth: 180 }}
          SelectProps={{ native: true }}
        >
          <option value="PENDING">معلق</option>
          <option value="AUTO_MATCHED">مطابقة تلقائية</option>
          <option value="MANUAL_CONFIRMED">مؤكد يدوياً</option>
          <option value="REJECTED">مرفوض</option>
        </TextField>

        <Button variant="outlined" size="small" onClick={() => refetch()}>
          تحديث
        </Button>
      </Stack>

      {/* Table */}
      {!selectedProvider ? (
        <Alert severity="info">الرجاء اختيار مزود لعرض الخدمات الخام.</Alert>
      ) : rawLoading ? (
        <Stack spacing={1}>
          {[1, 2, 3].map((i) => <Skeleton key={i} variant="rectangular" height={48} />)}
        </Stack>
      ) : rawServices.length === 0 ? (
        <Alert severity="success">لا توجد سجلات بحالة "{STATUS_LABEL[statusFilter] ?? statusFilter}".</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow sx={{ '& th': { fontWeight: 700, bgcolor: 'grey.50' } }}>
                <TableCell>#</TableCell>
                <TableCell>الاسم الخام</TableCell>
                <TableCell>الاسم المُعالَج</TableCell>
                <TableCell>الحالة</TableCell>
                <TableCell>الخدمة المُطابَقة</TableCell>
                <TableCell align="center">الإجراءات</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rawServices.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>{row.id}</TableCell>
                  <TableCell sx={{ maxWidth: 240, wordBreak: 'break-word' }}>
                    {row.rawName}
                  </TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontSize: 12 }}>
                    {row.normalizedName ?? '—'}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={STATUS_LABEL[row.status] ?? row.status}
                      color={STATUS_COLOR[row.status] ?? 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {row.mapping ? (
                      <Stack>
                        <Typography variant="body2" fontWeight={600}>
                          {row.mapping.medicalServiceCode}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {row.mapping.medicalServiceName}
                        </Typography>
                      </Stack>
                    ) : '—'}
                  </TableCell>
                  <TableCell align="center">
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                      {/* Auto Match */}
                      {['PENDING', 'AUTO_MATCHED'].includes(row.status) && (
                        <Tooltip title="مطابقة تلقائية">
                          <span>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<AutoFixHighIcon />}
                              disabled={autoMatchMutation.isPending}
                              onClick={() => autoMatchMutation.mutate(row.id)}
                            >
                              تلقائي
                            </Button>
                          </span>
                        </Tooltip>
                      )}

                      {/* Manual Map */}
                      {row.status !== 'REJECTED' && (
                        <Tooltip title="تعيين يدوي">
                          <Button
                            size="small"
                            variant="outlined"
                            color="primary"
                            startIcon={<CheckCircleIcon />}
                            onClick={() => setManualMapRow(row)}
                          >
                            يدوي
                          </Button>
                        </Tooltip>
                      )}

                      {/* Reject */}
                      {row.status !== 'REJECTED' && row.status !== 'MANUAL_CONFIRMED' && (
                        <Tooltip title="رفض">
                          <Button
                            size="small"
                            variant="outlined"
                            color="error"
                            startIcon={<BlockIcon />}
                            disabled={rejectMutation.isPending}
                            onClick={() => rejectMutation.mutate(row.id)}
                          >
                            رفض
                          </Button>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Manual Map Modal */}
      <Dialog
        open={!!manualMapRow}
        onClose={() => { setManualMapRow(null); setSelectedService(null); setServiceSearch(''); setServiceCategoryId(''); }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          تعيين يدوي — <Typography component="span" variant="body1" color="primary">
            {manualMapRow?.rawName}
          </Typography>
        </DialogTitle>
        <DialogContent dividers>
          <TextField
            fullWidth
            size="small"
            placeholder="ابحث بالكود أو الاسم..."
            value={serviceSearch}
            onChange={(e) => setServiceSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              )
            }}
            sx={{ mb: 2 }}
          />

          <TextField
            fullWidth
            select
            size="small"
            label="تصفية حسب التصنيف"
            value={serviceCategoryId}
            onChange={(e) => setServiceCategoryId(e.target.value)}
            sx={{ mb: 2 }}
          >
            <MenuItem value="">كل التصنيفات</MenuItem>
            {categoryOptions.map((cat) => (
              <MenuItem key={cat.id} value={String(cat.id)}>
                {cat.code} — {cat.name}
              </MenuItem>
            ))}
          </TextField>

          <TableContainer sx={{ maxHeight: 320 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>الكود</TableCell>
                  <TableCell>الاسم</TableCell>
                  <TableCell>التصنيف</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredMedical.slice(0, 50).map((svc) => (
                  <TableRow
                    key={svc.id}
                    hover
                    selected={selectedService?.id === svc.id}
                    sx={{ cursor: 'pointer' }}
                    onClick={() => setSelectedService(svc)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {svc.code}
                      </Typography>
                    </TableCell>
                    <TableCell>{svc.name}</TableCell>
                    <TableCell>{svc.categoryName || '—'}</TableCell>
                    <TableCell>
                      {selectedService?.id === svc.id && (
                        <CheckCircleIcon color="success" fontSize="small" />
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {filteredMedical.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      <Typography variant="body2" color="text.secondary">
                        لا توجد نتائج
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          {filteredMedical.length > 50 && (
            <Typography variant="caption" color="text.secondary">
              يُعرض أول 50 نتيجة — حدّد البحث للتضييق
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => { setManualMapRow(null); setSelectedService(null); setServiceSearch(''); setServiceCategoryId(''); }}
          >
            إلغاء
          </Button>
          <Button
            variant="contained"
            disabled={!selectedService || manualMapMutation.isPending}
            onClick={handleConfirmManualMap}
            startIcon={manualMapMutation.isPending ? <CircularProgress size={14} /> : <CheckCircleIcon />}
          >
            تأكيد التعيين
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ProviderMappingCenter;
