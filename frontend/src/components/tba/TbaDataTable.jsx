/**
 * TbaDataTable - Unified Data Table Component
 * Phase D2.2 - Material React Table Integration
 *
 * ⚠️ This is the CONTRACT component for all data tables in the system.
 * Uses Material React Table internally with server-side mode.
 *
 * Features:
 * - Server-side pagination, sorting, filtering
 * - Arabic localization (RTL)
 * - Export CSV / Print support
 * - Error states (403, 404, 500)
 * - Loading skeleton
 * - Empty state
 */

import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { MaterialReactTable, useMaterialReactTable } from 'material-react-table';
import { MRT_Localization_AR } from 'material-react-table/locales/ar';

// MUI Components
import { Box, Button, IconButton, Stack, Tooltip } from '@mui/material';

// MUI Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import PrintIcon from '@mui/icons-material/Print';
import LockIcon from '@mui/icons-material/Lock';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import SearchOffIcon from '@mui/icons-material/SearchOff';

// Project Components
import ModernEmptyState from 'components/tba/ModernEmptyState';
import ExcelUploadButton from 'components/tba/ExcelUploadButton';

// ============================================================================
// CONSTANTS
// ============================================================================

const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Parse error and return Arabic message with icon
 */
const getErrorInfo = (error) => {
  const status = error?.response?.status || error?.status || 0;

  if (status === 403) {
    return {
      title: 'غير مصرح',
      message: 'ليس لديك صلاحية للوصول إلى هذه البيانات',
      icon: LockIcon
    };
  }

  if (status === 404) {
    return {
      title: 'غير موجود',
      message: 'البيانات المطلوبة غير موجودة',
      icon: SearchOffIcon
    };
  }

  if (status >= 500) {
    return {
      title: 'خطأ تقني',
      message: 'حدث خطأ في الخادم. يرجى المحاولة لاحقاً',
      icon: ErrorOutlineIcon
    };
  }

  return {
    title: 'خطأ',
    message: error?.message || 'حدث خطأ أثناء تحميل البيانات',
    icon: ErrorOutlineIcon
  };
};

/**
 * Export data to CSV
 */
const exportToCsv = (data, columns, filename = 'export') => {
  if (!Array.isArray(data) || data.length === 0) return;

  // Get visible columns with accessorKey
  const exportColumns = columns.filter((col) => col.accessorKey && col.enableHiding !== false);

  // Create header row
  const headers = exportColumns.map((col) => col.header || col.accessorKey);

  // Create data rows
  const rows = data.map((row) => {
    return exportColumns.map((col) => {
      const value = row[col.accessorKey];
      // Handle nested values
      if (typeof value === 'object' && value !== null) {
        return JSON.stringify(value);
      }
      return value ?? '';
    });
  });

  // Build CSV content
  const csvContent = [headers.join(','), ...rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))].join(
    '\n'
  );

  // Add BOM for Arabic support
  const bom = '\uFEFF';
  const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8;' });

  // Download
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${filename}_${new Date().toISOString().split('T')[0]}.csv`;
  link.click();
};

/**
 * Print table data
 */
const printTable = (data, columns, title = 'تقرير') => {
  if (!Array.isArray(data) || data.length === 0) return;

  const exportColumns = columns.filter((col) => col.accessorKey && col.enableHiding !== false);

  const printWindow = window.open('', '_blank');
  if (!printWindow) return;

  const html = `
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        body { font-family: 'Segoe UI', Tahoma, sans-serif; padding: 20px; direction: rtl; }
        h1 { text-align: center; margin-bottom: 20px; }
        table { width: '6.25rem'%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: right; }
        th { background-color: #f5f5f5; font-weight: bold; }
        tr:nth-child(even) { background-color: #fafafa; }
        .print-date { text-align: left; color: #666; font-size: 12px; margin-bottom: 10px; }
        @media print { body { -webkit-print-color-adjust: exact; } }
      </style>
    </head>
    <body>
      <div class="print-date">تاريخ الطباعة: ${new Date().toLocaleDateString('en-US')}</div>
      <h1>${title}</h1>
      <table>
        <thead>
          <tr>${exportColumns.map((col) => `<th>${col.header || col.accessorKey}</th>`).join('')}</tr>
        </thead>
        <tbody>
          ${data
            .map(
              (row) => `
            <tr>
              ${exportColumns
                .map((col) => {
                  const value = row[col.accessorKey];
                  return `<td>${value ?? '-'}</td>`;
                })
                .join('')}
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>
      <script>window.onload = () => { window.print(); }</script>
    </body>
    </html>
  `;

  printWindow.document.write(html);
  printWindow.document.close();
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

/**
 * TbaDataTable - Unified server-side data table
 *
 * @param {Object} props
 * @param {Array} props.columns - MRT column definitions
 * @param {Function} props.fetcher - async ({ page, size, sortBy, sortDir, search, ...filters }) => { items, total, page, size }
 * @param {string} props.queryKey - Unique key for logging/caching
 * @param {boolean} props.enableExport - Enable CSV export button
 * @param {boolean} props.enablePrint - Enable print button
 * @param {boolean} props.enableFilters - Enable column filters
 * @param {boolean} props.enableExcelUpload - Enable Excel upload button (UI only)
 * @param {Function} props.onExcelUpload - Callback when Excel file is uploaded: (file) => Promise<void>
 * @param {string} props.exportFilename - Filename for CSV export
 * @param {string} props.printTitle - Title for print view
 * @param {Function} props.onRowClick - Optional row click handler
 * @param {Object} props.initialFilters - Initial filter values
 * @param {number} props.refreshKey - External refresh trigger (from TableRefreshContext)
 *
 * ⚠️ REFRESH CONTRACT (Phase D2.3):
 * - refreshKey is controlled by TableRefreshContext
 * - When refreshKey changes, table fetches exactly ONCE
 * - Use triggerRefresh() from Create/Edit pages after successful save
 * - Do NOT pass a new refreshKey on every render
 */
const TbaDataTable = ({
  columns,
  fetcher,
  queryKey = 'data-table',
  enableExport = false,
  enablePrint = false,
  enableFilters = true,
  enableExcelUpload = false,
  onExcelUpload,
  exportFilename,
  printTitle,
  onRowClick,
  initialFilters = {},
  refreshKey = 0
}) => {
  // ========================================
  // REFS - Stable references to prevent re-renders
  // ========================================

  const fetcherRef = useRef(fetcher);
  const initialFiltersRef = useRef(initialFilters);
  const isFetchingRef = useRef(false);

  // Update refs when props change (but don't trigger re-render)
  useEffect(() => {
    fetcherRef.current = fetcher;
  }, [fetcher]);

  // ========================================
  // STATE
  // ========================================

  const [data, setData] = useState([]);
  const [totalRows, setTotalRows] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // MRT State
  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: DEFAULT_PAGE_SIZE
  });
  const [sorting, setSorting] = useState([{ id: 'createdAt', desc: true }]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [columnFilters, setColumnFilters] = useState([]);

  // ========================================
  // DATA FETCHING
  // ========================================

  // Serialize column filters for stable dependency
  const columnFiltersKey = useMemo(() => JSON.stringify(columnFilters), [columnFilters]);

  const fetchData = useCallback(async () => {
    // Prevent duplicate fetches
    if (isFetchingRef.current) {
      return;
    }

    isFetchingRef.current = true;
    setLoading(true);
    setError(null);

    try {
      // Map MRT state to API params
      const apiParams = {
        page: pagination.pageIndex + 1, // API uses 1-based
        size: pagination.pageSize,
        sortBy: sorting[0]?.id || 'createdAt',
        sortDir: sorting[0]?.desc ? 'desc' : 'asc',
        search: globalFilter || undefined,
        ...initialFiltersRef.current
      };

      // Add column filters
      const parsedFilters = JSON.parse(columnFiltersKey || '[]');
      parsedFilters.forEach((filter) => {
        if (filter.value !== undefined && filter.value !== '') {
          apiParams[filter.id] = filter.value;
        }
      });

      // Clean undefined values
      Object.keys(apiParams).forEach((key) => {
        if (apiParams[key] === undefined) {
          delete apiParams[key];
        }
      });

      console.log(`[TbaDataTable:${queryKey}] Fetching with params:`, apiParams);

      const response = await fetcherRef.current(apiParams);

      // Handle response - defensive
      const items = Array.isArray(response?.items) ? response.items : Array.isArray(response) ? response : [];

      const total = response?.total ?? response?.totalElements ?? items.length;

      // Only update state if data actually changed
      setData((prev) => {
        const newDataStr = JSON.stringify(items);
        const prevDataStr = JSON.stringify(prev);
        if (newDataStr === prevDataStr) return prev;
        return items;
      });

      setTotalRows((prev) => {
        if (prev === total) return prev;
        return total;
      });

      console.log(`[TbaDataTable:${queryKey}] Loaded ${items.length} items, total: ${total}`);
    } catch (err) {
      console.error(`[TbaDataTable:${queryKey}] Fetch error:`, err);
      setError(err);
      setData([]);
      setTotalRows(0);
    } finally {
      setLoading(false);
      isFetchingRef.current = false;
    }
    // refreshKey is intentionally in dependencies to trigger re-fetch (Phase D2.3 Contract)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagination.pageIndex, pagination.pageSize, sorting, globalFilter, columnFiltersKey, queryKey, refreshKey]);

  // Fetch on state change or external refresh - stable dependency
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // ========================================
  // HANDLERS
  // ========================================

  const handleRefresh = useCallback(() => {
    fetchData();
  }, [fetchData]);

  const handleExport = useCallback(() => {
    exportToCsv(data, columns, exportFilename || queryKey);
  }, [data, columns, exportFilename, queryKey]);

  const handlePrint = useCallback(() => {
    printTable(data, columns, printTitle || 'تقرير');
  }, [data, columns, printTitle]);

  const handleExcelUpload = useCallback(
    async (file) => {
      if (!onExcelUpload) {
        console.warn('[TbaDataTable] onExcelUpload handler not provided');
        return;
      }

      try {
        await onExcelUpload(file);
        // After successful upload, refresh table data
        handleRefresh();
      } catch (error) {
        console.error('[TbaDataTable] Excel upload error:', error);
        throw error; // Re-throw to let ExcelUploadButton handle UI error
      }
    },
    [onExcelUpload, handleRefresh]
  );

  // ========================================
  // CUSTOM TOOLBAR
  // ========================================

  const renderTopToolbarCustomActions = useCallback(
    () => (
      <Stack direction="row" spacing={1}>
        <Tooltip title="تحديث">
          <IconButton onClick={handleRefresh} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>

        {enableExport && (
          <Tooltip title="تصدير CSV">
            <IconButton onClick={handleExport} disabled={loading || data.length === 0}>
              <FileDownloadIcon />
            </IconButton>
          </Tooltip>
        )}

        {enablePrint && (
          <Tooltip title="طباعة">
            <IconButton onClick={handlePrint} disabled={loading || data.length === 0}>
              <PrintIcon />
            </IconButton>
          </Tooltip>
        )}

        {enableExcelUpload && onExcelUpload && (
          <ExcelUploadButton onUpload={handleExcelUpload} disabled={loading} buttonText="رفع Excel" size="small" variant="outlined" />
        )}
      </Stack>
    ),
    [
      handleRefresh,
      handleExport,
      handlePrint,
      handleExcelUpload,
      loading,
      enableExport,
      enablePrint,
      enableExcelUpload,
      onExcelUpload,
      data.length
    ]
  );

  // ========================================
  // TABLE CONFIGURATION
  // (Must be called before any early returns - Rules of Hooks)
  // ========================================

  const table = useMaterialReactTable({
    columns,
    data,

    // State
    state: {
      isLoading: loading,
      pagination,
      sorting,
      globalFilter,
      columnFilters,
      showGlobalFilter: true
    },

    // Server-side mode
    manualPagination: true,
    manualSorting: true,
    manualFiltering: true,
    rowCount: totalRows,

    // State handlers - with updater function support
    onPaginationChange: (updater) => {
      setPagination((prev) => (typeof updater === 'function' ? updater(prev) : updater));
    },
    onSortingChange: (updater) => {
      setSorting((prev) => (typeof updater === 'function' ? updater(prev) : updater));
    },
    onGlobalFilterChange: setGlobalFilter,
    onColumnFiltersChange: (updater) => {
      setColumnFilters((prev) => (typeof updater === 'function' ? updater(prev) : updater));
    },

    // Features
    enableColumnFilters: enableFilters,
    enableGlobalFilter: true,
    enableColumnResizing: true,
    enableDensityToggle: true,
    enableFullScreenToggle: true,
    enableHiding: true,
    enableStickyHeader: true,

    // Pagination
    paginationDisplayMode: 'pages',
    muiPaginationProps: {
      showRowsPerPage: true,
      rowsPerPageOptions: PAGE_SIZE_OPTIONS,
      shape: 'rounded',
      variant: 'outlined'
    },

    // Localization - Arabic
    localization: {
      ...MRT_Localization_AR,
      noRecordsToDisplay: 'لا توجد بيانات',
      noResultsFound: 'لم يتم العثور على نتائج',
      search: 'بحث...',
      showHideColumns: 'إظهار/إخفاء الأعمدة',
      showHideFilters: 'إظهار/إخفاء الفلاتر',
      rowsPerPage: 'عدد الصفوف:',
      of: 'من'
    },

    // Toolbar
    renderTopToolbarCustomActions,

    // Row click handler
    muiTableBodyRowProps: onRowClick
      ? ({ row }) => ({
          onClick: () => onRowClick(row.original),
          sx: { cursor: 'pointer' }
        })
      : undefined,

    // Empty state
    renderEmptyRowsFallback: () => (
      <Box sx={{ textAlign: 'center', py: '2.0rem' }}>
        <ModernEmptyState icon={SearchOffIcon} title="لا توجد بيانات" description="لم يتم العثور على أي بيانات مطابقة للبحث" />
      </Box>
    ),

    // Styling
    muiTableContainerProps: {
      sx: { maxHeight: 'calc(100vh - 350px)' }
    },

    muiTableProps: {
      sx: {
        tableLayout: 'fixed'
      }
    },

    // Initial state
    initialState: {
      density: 'comfortable',
      showColumnFilters: false
    }
  });

  // ========================================
  // ERROR STATE
  // ========================================

  if (error && !loading) {
    const errorInfo = getErrorInfo(error);
    const ErrorIcon = errorInfo.icon;

    return (
      <ModernEmptyState
        icon={ErrorIcon}
        title={errorInfo.title}
        description={errorInfo.message}
        action={
          <Button variant="contained" startIcon={<RefreshIcon />} onClick={handleRefresh}>
            إعادة المحاولة
          </Button>
        }
      />
    );
  }

  // ========================================
  // RENDER
  // ========================================

  return <MaterialReactTable table={table} />;
};

export default TbaDataTable;
