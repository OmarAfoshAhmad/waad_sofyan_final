/**
 * Provider Contracts List Page - UNIFIED IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Pattern: UnifiedPageHeader → MainCard → GenericDataTable
 */

import { useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import EditIcon from '@mui/icons-material/Edit';
import DescriptionIcon from '@mui/icons-material/Description';

import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import TableErrorBoundary from 'components/TableErrorBoundary';
import useTableState from 'hooks/useTableState';
import { getProviderContracts, CONTRACT_STATUS_CONFIG, PRICING_MODEL_CONFIG } from 'services/api/provider-contracts.service';

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

  // Note: Edit redirects to View page since there's no dedicated Edit page yet
  const handleNavigateEdit = useCallback(
    (id) => {
      if (!id) {
        console.error('[ProviderContracts] Edit: Missing contract ID');
        return;
      }
      // Redirect to view page - edit functionality may be available there
      navigate(`/provider-contracts/${id}`);
    },
    [navigate]
  );

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, tableState.page, tableState.pageSize, tableState.sorting, tableState.columnFilters],
    queryFn: async () => {
      const params = { page: tableState.page, size: tableState.pageSize };
      if (tableState.sorting.length > 0) {
        const sort = tableState.sorting[0];
        params.sort = `${sort.id},${sort.desc ? 'desc' : 'asc'}`;
      }
      Object.entries(tableState.columnFilters).forEach(([key, value]) => {
        if (value !== '' && value !== null && value !== undefined) params[key] = value;
      });
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
                
            </Stack>
          );

        default:
          return null;
      }
    },
    [handleNavigateView, handleNavigateEdit]
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
        />
        <MainCard>
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
        </MainCard>
      </Box>
    </>
  );
};

export default ProviderContractsList;
