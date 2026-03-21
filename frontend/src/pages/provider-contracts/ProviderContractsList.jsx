/**
 * Provider Contracts List Page - UNIFIED IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Pattern: UnifiedPageHeader → MainCard → GenericDataTable
 */

import { useMemo, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import EditIcon from '@mui/icons-material/Edit';
import DescriptionIcon from '@mui/icons-material/Description';
import DeleteIcon from '@mui/icons-material/Delete';
import UndoIcon from '@mui/icons-material/Undo';

import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import TableErrorBoundary from 'components/TableErrorBoundary';
import { SoftDeleteToggle } from 'components/tba';
import useTableState from 'hooks/useTableState';
import {
  getProviderContracts,
  getDeletedProviderContracts,
  restoreProviderContract,
  deleteProviderContract,
  CONTRACT_STATUS_CONFIG,
  PRICING_MODEL_CONFIG
} from 'services/api/provider-contracts.service';
import { useSnackbar } from 'notistack';

const QUERY_KEY = 'provider-contracts';
const MODULE_NAME = 'provider-contracts';

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  try {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch {
    return dateStr;
  }
};

const ProviderContractsList = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const [showDeleted, setShowDeleted] = useState(false);

  const tableState = useTableState({
    initialPageSize: 20,
    defaultSort: { field: 'id', direction: 'desc' },
    initialFilters: {}
  });

  const handleNavigateAdd = useCallback(() => navigate('/provider-contracts/create'), [navigate]);
  const handleNavigateView = useCallback(
    (id) => {
      if (!id) {
        console.error('[ProviderContracts] View: Missing contract ID');
        return;
      }
      navigate(`/provider-contracts/${id}`);
    },
    [navigate]
  );

  const handleNavigateEdit = useCallback(
    (id) => {
      if (!id) {
        console.error('[ProviderContracts] Edit: Missing contract ID');
        return;
      }
      navigate(`/provider-contracts/edit/${id}`);
    },
    [navigate]
  );

  const handleDelete = useCallback(
    async (id, code) => {
      if (!window.confirm(`هل تريد حذف العقد "${code || id}"؟`)) return;
      try {
        await deleteProviderContract(id);
        enqueueSnackbar('تم حذف العقد بنجاح', { variant: 'success' });
        queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      } catch (err) {
        enqueueSnackbar(err?.response?.data?.message || 'فشل حذف العقد', { variant: 'error' });
      }
    },
    [enqueueSnackbar, queryClient]
  );

  const handleRestore = useCallback(
    async (id, code) => {
      if (!window.confirm(`هل تريد استعادة العقد "${code || id}" من سجل المحذوفات؟`)) return;
      try {
        await restoreProviderContract(id);
        enqueueSnackbar('تمت استعادة العقد بنجاح', { variant: 'success' });
        queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      } catch (err) {
        enqueueSnackbar(err?.response?.data?.message || 'فشلت استعادة العقد', { variant: 'error' });
      }
    },
    [enqueueSnackbar, queryClient]
  );

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, showDeleted, tableState.page, tableState.pageSize, tableState.sorting, tableState.columnFilters],
    queryFn: async () => {
      const params = { page: tableState.page, size: tableState.pageSize };
      if (tableState.sorting.length > 0) {
        const sort = tableState.sorting[0];
        params.sort = `${sort.id},${sort.desc ? 'desc' : 'asc'}`;
      }
      Object.entries(tableState.columnFilters).forEach(([key, value]) => {
        if (value !== '' && value !== null && value !== undefined) params[key] = value;
      });
      if (showDeleted) {
        return await getDeletedProviderContracts(params);
      }
      return await getProviderContracts(params);
    },
    keepPreviousData: true
  });

  const columns = useMemo(
    () => [
      {
        id: 'contractCode',
        label: 'رمز العقد',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'provider',
        label: 'مقدم الخدمة',
        minWidth: '12.5rem',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'pricingModel',
        label: 'نموذج التسعير',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'discountPercent',
        label: 'نسبة الخصم',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'startDate',
        label: 'تاريخ البدء',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'endDate',
        label: 'تاريخ الانتهاء',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'actions',
        label: 'الإجراءات',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================

  const renderCell = useCallback(
    (contract, column) => {
      if (!contract) return null;

      switch (column.id) {
        case 'contractCode':
          return (
            <Typography variant="body2" fontWeight={600} color="primary">
              {contract.contractCode || '-'}
            </Typography>
          );

        case 'provider':
          return (
            <Stack spacing={0}>
              <Typography variant="body2" fontWeight={500}>
                {contract.providerName || contract.provider?.name || '-'}
              </Typography>
              {contract.provider?.city && (
                <Typography variant="caption" color="text.secondary">
                  {contract.provider.city}
                </Typography>
              )}
            </Stack>
          );

        case 'status':
          const config = CONTRACT_STATUS_CONFIG[contract.status] || { label: contract.status, color: 'default' };
          return <Chip label={config.label} color={config.color} size="small" />;

        case 'pricingModel':
          const modelConfig = PRICING_MODEL_CONFIG[contract.pricingModel] || { label: contract.pricingModel };
          return (
            <Typography variant="body2" color="text.secondary">
              {modelConfig.label || '-'}
            </Typography>
          );

        case 'discountPercent':
          return contract.discountPercent !== null && contract.discountPercent !== undefined ? (
            <Chip label={`${contract.discountPercent}%`} size="small" variant="outlined" color="info" />
          ) : (
            <Typography variant="body2">-</Typography>
          );

        case 'startDate':
          return <Typography variant="body2">{formatDate(contract.startDate)}</Typography>;

        case 'endDate':
          return <Typography variant="body2">{formatDate(contract.endDate)}</Typography>;

        case 'actions':
          return (
            <Stack direction="row" spacing={0.5} justifyContent="center">
              <Tooltip title="عرض التفاصيل">
                <IconButton size="small" color="primary" onClick={() => handleNavigateView(contract.id)}>
                  <VisibilityIcon fontSize="small" />
                </IconButton>
              </Tooltip>

              {showDeleted ? (
                <Tooltip title="استعادة">
                  <IconButton size="small" color="success" onClick={() => handleRestore(contract.id, contract.contractCode)}>
                    <UndoIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              ) : (
                <>
                  <Tooltip title="تعديل">
                    <IconButton
                      size="small"
                      color="info"
                      onClick={() => handleNavigateEdit(contract.id)}
                      disabled={contract.status === 'TERMINATED'}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="حذف">
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => handleDelete(contract.id, contract.contractCode)}
                      disabled={contract.status === 'ACTIVE'}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </>
              )}
            </Stack>
          );

        default:
          return null;
      }
    },
    [handleNavigateView, handleNavigateEdit, handleDelete, handleRestore, showDeleted]
  );

  return (
    <>
      <Box>
        <UnifiedPageHeader
          title="عقود مقدمي الخدمة"
          subtitle="إدارة عقود التسعير مع مقدمي الخدمات الصحية"
          icon={DescriptionIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/dashboard' }, { label: 'عقود مقدمي الخدمة' }]}
          showAddButton={true}
          addButtonLabel="إنشاء عقد جديد"
          onAddClick={handleNavigateAdd}
          requires="provider_contracts.create"
          additionalActions={<SoftDeleteToggle showDeleted={showDeleted} onToggle={() => setShowDeleted((v) => !v)} />}
        />
        <TableErrorBoundary>
          <UnifiedMedicalTable
            columns={columns}
            rows={Array.isArray(data) ? data : data?.content || data?.items || []}
            loading={isLoading}
            renderCell={renderCell}
            totalCount={
              typeof data?.totalElements === 'number'
                ? data.totalElements
                : typeof data?.total === 'number'
                  ? data.total
                  : 0
            }
            page={tableState.page}
            rowsPerPage={tableState.pageSize}
            onPageChange={(newPage) => tableState.setPage(newPage)}
            onRowsPerPageChange={(newSize) => tableState.setPageSize(newSize)}
            emptyIcon={DescriptionIcon}
            emptyMessage="لا توجد عقود مسجلة لمقدمي الخدمة"
          />
        </TableErrorBoundary>
      </Box>
    </>
  );
};

export default ProviderContractsList;
