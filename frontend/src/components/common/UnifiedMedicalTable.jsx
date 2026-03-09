/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNIFIED MEDICAL TABLE - TBA WAAD MEDICAL TPA SYSTEM
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * THE SINGLE SOURCE OF TRUTH FOR ALL TABLES IN THE SYSTEM
 * Based on Provider Visits Log design pattern
 *
 * ARCHITECTURE PRINCIPLES:
 * ✅ ONE table design for the ENTIRE system
 * ✅ Full-width (100% viewport width)
 * ✅ Soft medical green header (#E8F5F1)
 * ✅ Fixed/sticky header
 * ✅ Filters ABOVE table (never inside)
 * ✅ Desktop-first professional medical UI
 * ✅ RTL support for Arabic
 *
 * FORBIDDEN:
 * ❌ MUI DataGrid
 * ❌ Filters in table headers
 * ❌ Different table styles per module
 * ❌ Cards wrapping tables
 * ❌ Nested scrollbars
 *
 * @author TBA WAAD Development Team
 * @version 2.0.0 - Unified Medical Standard
 * @since 2026-02-08
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Typography,
  CircularProgress,
  Stack,
  TableSortLabel,
  alpha,
  useTheme,
  Collapse,
  IconButton
} from '@mui/material';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { useState, Fragment } from 'react';

// ═══════════════════════════════════════════════════════════════════════════════
// MEDICAL COLOR THEME (SYSTEM-WIDE STANDARD)
// ═══════════════════════════════════════════════════════════════════════════════

const MEDICAL_TABLE_THEME = {
  // Header - Soft Medical Green (Light Mode)
  header: {
    light: {
      background: '#E8F5F1', // Soft mint green
      text: '#0D4731', // Dark green
      border: '#C8E6C9'
    },
    dark: {
      background: '#1E3A5F', // Professional dark blue
      text: '#FFFFFF'
    }
  },
  // Row hover - Very light medical tint
  row: {
    light: {
      odd: 'rgba(13, 71, 161, 0.04)',
      hover: 'rgba(13, 71, 161, 0.08)'
    },
    dark: {
      odd: 'rgba(13, 71, 161, 0.08)',
      hover: 'rgba(13, 71, 161, 0.15)'
    }
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED MEDICAL TABLE COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * UnifiedMedicalTable - The ONLY table component used in TBA WAAD system
 *
 * @example
 * <UnifiedMedicalTable
 *   columns={[
 *     { id: 'id', label: 'الرقم', minWidth: 80, icon: <BadgeIcon /> },
 *     { id: 'name', label: 'الاسم', minWidth: 160, icon: <PersonIcon /> }
 *   ]}
 *   rows={data}
 *   loading={false}
 *   totalCount={100}
 *   page={0}
 *   rowsPerPage={10}
 *   onPageChange={(newPage) => setPage(newPage)}
 *   onRowsPerPageChange={(newSize) => setPageSize(newSize)}
 *   renderCell={(row, column) => row[column.id]}
 * />
 */
const UnifiedMedicalTable = ({
  // Column Definition
  columns = [],

  // Data - accept both 'rows' and 'data' as aliases
  rows: rowsProp = [],
  data: dataProp,           // alias for rows
  loading = false,
  totalCount: totalCountProp = 0,
  totalItems: totalItemsProp, // alias for totalCount

  // Pagination
  page = 0,
  rowsPerPage = 10,
  onPageChange,
  onRowsPerPageChange,
  rowsPerPageOptions = [5, 10, 25, 50],

  // Sorting (Optional)
  sortBy,
  sortDirection,
  onSort,

  // Row Rendering
  renderCell,
  getRowKey = (row, index) => row.id || index,
  getRowSx,

  // Expandable Row
  renderExpandedRow,
  isRowExpandable,

  // Empty State - accept both individual props and emptyStateConfig object
  emptyMessage = 'لا توجد بيانات',
  emptyIcon: EmptyIcon = LocalHospitalIcon,
  emptyStateConfig,  // { icon, title, description } - alias for emptyMessage/emptyIcon

  // Loading State
  loadingMessage = 'جارِر التحميل...',

  // Error State (consumed here, not passed to DOM)
  error,
  onErrorClose,

  // Table Props
  stickyHeader = true,
  size = 'medium',
  hover = true,

  // Styling
  sx = {},
  tableContainerSx = {},
  ...otherProps
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  // Resolve aliased props
  const rows = dataProp !== undefined ? dataProp : rowsProp;
  const totalCount = totalItemsProp !== undefined ? totalItemsProp : totalCountProp;
  const resolvedEmptyMessage = emptyStateConfig?.title || emptyStateConfig?.description || emptyMessage;
  const ResolvedEmptyIcon = emptyStateConfig?.icon || EmptyIcon;

  // Theme colors
  const headerBg = isDark ? MEDICAL_TABLE_THEME.header.dark.background : MEDICAL_TABLE_THEME.header.light.background;
  const headerText = isDark ? MEDICAL_TABLE_THEME.header.dark.text : MEDICAL_TABLE_THEME.header.light.text;
  const rowOdd = isDark ? MEDICAL_TABLE_THEME.row.dark.odd : MEDICAL_TABLE_THEME.row.light.odd;
  const rowHover = isDark ? MEDICAL_TABLE_THEME.row.dark.hover : MEDICAL_TABLE_THEME.row.light.hover;

  // Pagination handlers
  const handlePageChange = (_, newPage) => {
    onPageChange?.(newPage);
  };

  const handleRowsPerPageChange = (event) => {
    const newSize = parseInt(event.target.value, 10);
    onRowsPerPageChange?.(newSize);
  };

  // Calculate columns span for loading/empty states
  const colSpan = columns.length + (renderExpandedRow ? 1 : 0);

  // Expansion state
  const [expandedRows, setExpandedRows] = useState({});

  const toggleRowExpansion = (rowId) => {
    setExpandedRows((prev) => ({
      ...prev,
      [rowId]: !prev[rowId]
    }));
  };

  // Handle sort
  const handleSortRequest = (columnId) => {
    if (onSort && columns.find((col) => col.id === columnId)?.sortable !== false) {
      const isAsc = sortBy === columnId && sortDirection === 'asc';
      onSort(columnId, isAsc ? 'desc' : 'asc');
    }
  };

  return (
    <Box
      sx={{
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        ...sx
      }}
      {...otherProps}
    >
      {/* Table Container - NO card wrapper, NO shadows */}
      <TableContainer
        component={Paper}
        variant="outlined"
        sx={{
          borderRadius: 2,
          overflow: 'auto',
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: 'none', // NO shadows
          width: '100%',
          flexGrow: 1,
          ...tableContainerSx
        }}
      >
        <Table size={size} stickyHeader={stickyHeader}>
          {/* Header - Soft Medical Green */}
          <TableHead>
            <TableRow>
              {renderExpandedRow && (
                <TableCell
                  padding="checkbox"
                  sx={{
                    bgcolor: headerBg,
                    borderBottom: `2px solid ${theme.palette.divider}`
                  }}
                />
              )}
              {columns.map((column) => {
                const isSortable = column.sortable !== false && onSort;
                const isActive = sortBy === column.id;

                return (
                  <TableCell
                    key={column.id}
                    align={column.align || 'left'}
                    sx={{
                      bgcolor: headerBg,
                      color: headerText,
                      fontWeight: 600,
                      py: 1.5,
                      minWidth: column.minWidth || 80,
                      width: column.width,
                      whiteSpace: 'nowrap',
                      borderBottom: `2px solid ${theme.palette.divider}`
                    }}
                  >
                    {isSortable ? (
                      <TableSortLabel
                        active={isActive}
                        direction={isActive ? sortDirection : 'asc'}
                        onClick={() => handleSortRequest(column.id)}
                        IconComponent={() => null} // Hides the default sort arrow entirely per user request
                        sx={{
                          color: headerText,
                          '&:hover': {
                            color: headerText,
                            opacity: 0.8
                          },
                          '&.Mui-active': {
                            color: headerText
                          }
                        }}
                      >
                        {column.icon ? (
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            {column.icon}
                            <span>{column.label}</span>
                          </Stack>
                        ) : (
                          column.label
                        )}
                      </TableSortLabel>
                    ) : column.icon ? (
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        {column.icon}
                        <span>{column.label}</span>
                      </Stack>
                    ) : (
                      column.label
                    )}
                  </TableCell>
                );
              })}
            </TableRow>
          </TableHead>

          {/* Body */}
          <TableBody>
            {/* Loading State */}
            {loading ? (
              <TableRow>
                <TableCell colSpan={colSpan} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={40} />
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    {loadingMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              /* Empty State */
              <TableRow>
                <TableCell colSpan={colSpan} align="center" sx={{ py: 4 }}>
                  <ResolvedEmptyIcon sx={{ fontSize: 48, color: 'action.disabled', mb: 1 }} />
                  <Typography variant="body1" color="textSecondary">
                    {resolvedEmptyMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              /* Data Rows */
              rows.map((row, rowIndex) => {
                const rowKey = getRowKey(row, rowIndex);
                const isExpanded = !!expandedRows[rowKey];
                const expandable = isRowExpandable ? isRowExpandable(row) : !!renderExpandedRow;

                return (
                  <Fragment key={rowKey}>
                    <TableRow
                      hover={hover}
                      sx={{
                        '&:nth-of-type(odd)': { bgcolor: rowOdd },
                        '&:hover': {
                          bgcolor: `${rowHover} !important`
                        },
                        transition: 'background-color 0.2s',
                        borderBottom: renderExpandedRow ? 'none' : `1px solid ${alpha(theme.palette.divider, 0.8)}`,
                        ...(getRowSx ? getRowSx(row, rowIndex) : {})
                      }}
                    >
                      {renderExpandedRow && (
                        <TableCell padding="checkbox" sx={{ py: 1.5, borderBottom: `1px solid ${alpha(theme.palette.divider, 0.8)}` }}>
                          {expandable && (
                            <IconButton size="small" onClick={() => toggleRowExpansion(rowKey)}>
                              {isExpanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                            </IconButton>
                          )}
                        </TableCell>
                      )}
                      {columns.map((column) => (
                        <TableCell
                          key={column.id}
                          align={column.align || 'left'}
                          sx={{
                            py: 1.5,
                            borderBottom: `1px solid ${alpha(theme.palette.divider, 0.8)}`
                          }}
                        >
                          {renderCell ? renderCell(row, column, rowIndex) : row[column.id]}
                        </TableCell>
                      ))}
                    </TableRow>
                    {renderExpandedRow && expandable && (
                      <TableRow sx={{ '& td': { padding: 0, borderBottom: isExpanded ? `1px solid ${alpha(theme.palette.divider, 0.8)}` : 'none' } }}>
                        <TableCell colSpan={colSpan}>
                          <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                            <Box sx={{ p: 2, bgcolor: alpha(theme.palette.primary.main, 0.02) }}>
                              {renderExpandedRow(row, rowIndex)}
                            </Box>
                          </Collapse>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Pagination - Always visible */}
      {onPageChange && onRowsPerPageChange && (
        <TablePagination
          component="div"
          count={totalCount}
          page={page}
          onPageChange={handlePageChange}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleRowsPerPageChange}
          rowsPerPageOptions={rowsPerPageOptions}
          labelRowsPerPage="صفوف لكل صفحة"
          labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
          sx={{
            borderTop: `1px solid ${theme.palette.divider}`,
            bgcolor: theme.palette.background.paper,
            overflow: 'hidden'
          }}
        />
      )}
    </Box>
  );
};

UnifiedMedicalTable.propTypes = {
  // Columns
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
      minWidth: PropTypes.number,
      width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      align: PropTypes.oneOf(['left', 'center', 'right']),
      icon: PropTypes.node,
      sortable: PropTypes.bool
    })
  ).isRequired,

  // Data
  rows: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number,

  // Pagination
  page: PropTypes.number,
  rowsPerPage: PropTypes.number,
  onPageChange: PropTypes.func,
  onRowsPerPageChange: PropTypes.func,
  rowsPerPageOptions: PropTypes.arrayOf(PropTypes.number),

  // Sorting
  sortBy: PropTypes.string,
  sortDirection: PropTypes.oneOf(['asc', 'desc']),
  onSort: PropTypes.func,

  // Row Rendering
  renderCell: PropTypes.func,
  getRowKey: PropTypes.func,
  getRowSx: PropTypes.func,

  // Expandable Row
  renderExpandedRow: PropTypes.func,
  isRowExpandable: PropTypes.func,

  // Empty State
  emptyMessage: PropTypes.string,
  emptyIcon: PropTypes.elementType,

  // Loading State
  loadingMessage: PropTypes.string,

  // Table Props
  stickyHeader: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium']),
  hover: PropTypes.bool,

  // Styling
  sx: PropTypes.object
};

export default UnifiedMedicalTable;
