/**
 * Benefit Packages List Page - Phase D2.4 (TbaDataTable Pattern)
 * Cloned from Medical Services Golden Reference
 *
 * ⚠️ Pattern: ModernPageHeader → MainCard → TbaDataTable
 *
 * Rules Applied:
 * 1. icon={Component} - NEVER JSX
 * 2. Arabic only - No English labels
 * 3. Defensive optional chaining
 * 4. TbaDataTable for server-side pagination/sorting/filtering
 * 5. TableRefreshContext for post-create/edit refresh (Phase D2.3)
 */

import { useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

// MUI Components
import { Box, Button, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material';

// MUI Icons - Always as Component, NEVER as JSX
import AddIcon from '@mui/icons-material/Add';
import VisibilityIcon from '@mui/icons-material/Visibility';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import TbaDataTable from 'components/tba/TbaDataTable';
import TableErrorBoundary from 'components/TableErrorBoundary';
import PermissionGuard from 'components/PermissionGuard';

// Contexts
import { useTableRefresh } from 'contexts/TableRefreshContext';

// Services
import { getBenefitPackages, deleteBenefitPackage } from 'services/api/benefit-packages.service';
import { formatCurrency as formatCurrencyGlobal } from 'utils/currency-formatter';

// ============================================================================
// CONSTANTS
// ============================================================================

const QUERY_KEY = 'benefit-packages';

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format currency with LYD
 */
const formatCurrency = (value) => {
  if (value === null || value === undefined) return '-';
  return formatCurrencyGlobal(value);
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const BenefitPackagesList = () => {
  const navigate = useNavigate();

  // ========================================
  // TABLE REFRESH CONTEXT (Phase D2.3)
  // ========================================

  const { refreshKey, triggerRefresh } = useTableRefresh();

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================

  const handleNavigateAdd = useCallback(() => {
    navigate('/benefit-packages/create');
  }, [navigate]);

  const handleNavigateView = useCallback(
    (id) => {
      if (!id) {
        console.error('[BenefitPackages] View: Missing package ID');
        return;
      }
      navigate(`/benefit-packages/view/${id}`);
    },
    [navigate]
  );

  const handleNavigateEdit = useCallback(
    (id) => {
      if (!id) {
        console.error('[BenefitPackages] Edit: Missing package ID');
        return;
      }
      navigate(`/benefit-packages/edit/${id}`);
    },
    [navigate]
  );

  const handleDelete = useCallback(
    async (id, name) => {
      const confirmMessage = `هل أنت متأكد من حذف الباقة "${name}"؟`;
      if (!window.confirm(confirmMessage)) return;

      try {
        await deleteBenefitPackage(id);
        // Trigger refresh via context - no page reload needed
        triggerRefresh();
      } catch (err) {
        console.error('[BenefitPackages] Delete failed:', err);
        alert('فشل حذف الباقة. يرجى المحاولة لاحقاً');
      }
    },
    [triggerRefresh]
  );

  // ========================================
  // FETCHER FUNCTION
  // ========================================

  const fetcher = useCallback(async (params) => {
    return getBenefitPackages(params);
  }, []);

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================

  const columns = useMemo(
    () => [
      // Code Column
      {
        accessorKey: 'code',
        header: 'الرمز',
        size: 100,
        Cell: ({ row }) => (
          <Typography variant="body2" fontWeight="medium">
            {row.original?.code || '-'}
          </Typography>
        )
      },

      // Name Column
      {
        accessorKey: 'name',
        header: 'الاسم',
        size: 180,
        Cell: ({ row }) => <Typography variant="body2">{row.original?.name || '-'}</Typography>
      },

      // Description Column
      {
        accessorKey: 'description',
        header: 'الوصف',
        size: 200,
        enableSorting: false,
        Cell: ({ row }) => (
          <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: '11.25rem' }}>
            {row.original?.description || '-'}
          </Typography>
        )
      },

      // Coverage Limit Column
      {
        accessorKey: 'coverageLimit',
        header: 'حد التغطية',
        size: 130,
        muiTableHeadCellProps: { align: 'right' },
        muiTableBodyCellProps: { align: 'right' },
        Cell: ({ row }) => (
          <Typography variant="body2" fontWeight="medium">
            {formatCurrency(row.original?.coverageLimit)}
          </Typography>
        )
      },

      // Deductible Column
      {
        accessorKey: 'deductible',
        header: 'نسبة التحمل',
        size: 100,
        muiTableHeadCellProps: { align: 'center' },
        muiTableBodyCellProps: { align: 'center' },
        Cell: ({ row }) => <Typography variant="body2">{row.original?.deductible != null ? `${row.original.deductible}%` : '-'}</Typography>
      },

      // Status Column
      {
        accessorKey: 'active',
        header: 'الحالة',
        size: 100,
        muiTableHeadCellProps: { align: 'center' },
        muiTableBodyCellProps: { align: 'center' },
        Cell: ({ row }) => (
          <Chip
            label={row.original?.active ? 'نشط' : 'غير نشط'}
            color={row.original?.active ? 'success' : 'default'}
            size="small"
            variant="light"
          />
        )
      },

      // Actions Column
      {
        id: 'actions',
        header: 'الإجراءات',
        size: 130,
        enableSorting: false,
        enableHiding: false,
        enableColumnFilter: false,
        muiTableHeadCellProps: { align: 'center' },
        muiTableBodyCellProps: { align: 'center' },
        Cell: ({ row }) => (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            <Tooltip title="عرض">
              <IconButton size="small" color="primary" onClick={() => handleNavigateView(row.original?.id)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Tooltip title="تعديل">
              <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.original?.id)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Tooltip title="حذف">
              <IconButton
                size="small"
                color="error"
                onClick={() => handleDelete(row.original?.id, row.original?.name || row.original?.code)}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        )
      }
    ],
    [handleNavigateView, handleNavigateEdit, handleDelete]
  );

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box>
      {/* ====== PAGE HEADER ====== */}
      <ModernPageHeader
        title="باقات المنافع"
        subtitle="إدارة باقات المنافع التأمينية"
        icon={CardGiftcardIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'باقات المنافع' }]}
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleNavigateAdd}>
            إضافة باقة جديدة
          </Button>
        }
      />

      {/* ====== MAIN CARD WITH TABLE ====== */}
      <MainCard>
        <TbaDataTable
          columns={columns}
          fetcher={fetcher}
          queryKey={QUERY_KEY}
          refreshKey={refreshKey}
          enableExport={true}
          enablePrint={true}
          enableFilters={true}
          exportFilename="benefit_packages"
          printTitle="تقرير باقات المنافع"
        />
      </MainCard>
    </Box>
  );
};

export default BenefitPackagesList;
