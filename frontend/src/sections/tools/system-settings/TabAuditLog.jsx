import { useMemo, useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';

// third-party
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  flexRender
} from '@tanstack/react-table';
import { useQuery } from '@tanstack/react-query';

// project imports
import MainCard from 'components/MainCard';
import ScrollX from 'components/ScrollX';
import { CSVExport } from 'components/third-party/react-table';
import axiosClient from 'utils/axios';

// icons
import { FileTextOutlined, SafetyOutlined, UserOutlined, DatabaseOutlined, CaretUpOutlined, CaretDownOutlined } from '@ant-design/icons';

// ==============================|| ACTION CONFIG ||============================== //

const ACTION_CONFIG = {
  LOGIN: { icon: SafetyOutlined, color: 'success' },
  LOGOUT: { icon: SafetyOutlined, color: 'default' },
  CREATE_CLAIM: { icon: FileTextOutlined, color: 'primary' },
  UPDATE_CLAIM: { icon: FileTextOutlined, color: 'info' },
  DELETE_CLAIM: { icon: FileTextOutlined, color: 'error' },
  CREATE_MEMBER: { icon: UserOutlined, color: 'primary' },
  UPDATE_SETTINGS: { icon: DatabaseOutlined, color: 'warning' },
  EXPORT_DATA: { icon: FileTextOutlined, color: 'info' }
};

const DEFAULT_ICON = FileTextOutlined;
const DEFAULT_COLOR = 'default';

const modules = ['Authentication', 'Claims', 'Members', 'Settings', 'Reports'];

// ==============================|| AUDIT LOG TABLE ||============================== //

export default function TabAuditLog() {
  const [globalFilter, setGlobalFilter] = useState('');
  const [moduleFilter, setModuleFilter] = useState('all');
  const [userFilter, setUserFilter] = useState('');

  const { data: rawData, isLoading, isError } = useQuery({
    queryKey: ['admin-audit-log'],
    queryFn: async () => {
      const response = await axiosClient.get('/admin/audit', { params: { size: 200 } });
      const payload = response.data?.data || response.data;
      // Backend may return paginated {content:[...]} or a plain array
      if (Array.isArray(payload?.content)) return payload.content;
      if (Array.isArray(payload)) return payload;
      return [];
    },
    staleTime: 30_000
  });

  const data = useMemo(() => {
    if (!rawData) return [];
    return rawData.map((entry) => {
      const actionCfg = ACTION_CONFIG[entry.action] || {};
      return {
        ...entry,
        actionIcon: actionCfg.icon || DEFAULT_ICON,
        actionColor: actionCfg.color || DEFAULT_COLOR
      };
    });
  }, [rawData]);

  const columns = useMemo(
    () => [
      {
        header: 'Timestamp',
        accessorKey: 'timestamp',
        cell: ({ getValue }) => {
          const date = new Date(getValue());
          return (
            <Stack>
              <span>{date.toLocaleDateString('en-GB')}</span>
              <span style={{ fontSize: '0.75rem', color: '#666' }}>{date.toLocaleTimeString('en-GB')}</span>
            </Stack>
          );
        }
      },
      {
        header: 'User',
        accessorKey: 'user'
      },
      {
        header: 'Action',
        accessorKey: 'action',
        cell: ({ row }) => {
          const Icon = row.original.actionIcon;
          return <Chip icon={<Icon />} label={row.original.action.replace(/_/g, ' ')} color={row.original.actionColor} size="small" />;
        }
      },
      {
        header: 'Module',
        accessorKey: 'module'
      },
      {
        header: 'IP Address',
        accessorKey: 'ipAddress'
      },
      {
        header: 'Status',
        accessorKey: 'status',
        cell: ({ getValue }) => (
          <Chip label={getValue()} color={getValue() === 'success' ? 'success' : 'error'} size="small" variant="outlined" />
        )
      }
    ],
    []
  );

  const filteredData = useMemo(() => {
    let filtered = data;

    // Module filter
    if (moduleFilter !== 'all') {
      filtered = filtered.filter((row) => row.module === moduleFilter);
    }

    // User filter
    if (userFilter) {
      filtered = filtered.filter((row) => row.user.toLowerCase().includes(userFilter.toLowerCase()));
    }

    return filtered;
  }, [data, moduleFilter, userFilter]);

  const table = useReactTable({
    data: filteredData,
    columns,
    state: {
      globalFilter
    },
    onGlobalFilterChange: setGlobalFilter,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: {
      pagination: {
        pageSize: 10
      }
    }
  });

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard
          title="Audit Log"
          content={false}
          secondary={
            <Stack direction="row" spacing={2}>
              <CSVExport data={filteredData} filename="audit-log.csv" />
            </Stack>
          }
        >
          {/* Loading / Error states */}
          {isLoading && (
            <Stack alignItems="center" justifyContent="center" sx={{ p: 6 }}>
              <CircularProgress size={32} />
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                جارٍ تحميل سجل التدقيق...
              </Typography>
            </Stack>
          )}
          {isError && (
            <Stack alignItems="center" sx={{ p: 4 }}>
              <Typography variant="body2" color="error">
                تعذّر تحميل سجل التدقيق. تحقق من الصلاحيات أو اتصل بالمسؤول.
              </Typography>
            </Stack>
          )}
          {!isLoading && !isError && (
            <>
          {/* Filters */}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ p: 3, pb: 0 }}>
            <TextField
              fullWidth
              select
              label="Filter by Module"
              value={moduleFilter}
              onChange={(e) => setModuleFilter(e.target.value)}
              size="small"
            >
              <MenuItem value="all">All Modules</MenuItem>
              {modules.map((module) => (
                <MenuItem key={module} value={module}>
                  {module}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              fullWidth
              label="Search by User"
              value={userFilter}
              onChange={(e) => setUserFilter(e.target.value)}
              placeholder="Search user email..."
              size="small"
            />

            <TextField
              fullWidth
              label="Global Search"
              value={globalFilter ?? ''}
              onChange={(e) => setGlobalFilter(e.target.value)}
              placeholder="Search all fields..."
              size="small"
            />
          </Stack>

          {/* Table */}
          <ScrollX>
            <Stack>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  {table.getHeaderGroups().map((headerGroup) => (
                    <tr key={headerGroup.id}>
                      {headerGroup.headers.map((header) => (
                        <th
                          key={header.id}
                          onClick={header.column.getToggleSortingHandler()}
                          style={{
                            padding: '12px 16px',
                            textAlign: 'left',
                            borderBottom: '2px solid #f0f0f0',
                            cursor: header.column.getCanSort() ? 'pointer' : 'default',
                            userSelect: 'none',
                            fontWeight: 600
                          }}
                        >
                          <Stack direction="row" spacing={1} alignItems="center">
                            <span>{flexRender(header.column.columnDef.header, header.getContext())}</span>
                            {header.column.getIsSorted() ? (
                              header.column.getIsSorted() === 'desc' ? (
                                <CaretDownOutlined />
                              ) : (
                                <CaretUpOutlined />
                              )
                            ) : null}
                          </Stack>
                        </th>
                      ))}
                    </tr>
                  ))}
                </thead>
                <tbody>
                  {table.getRowModel().rows.length === 0 ? (
                    <tr>
                      <td colSpan={columns.length} style={{ padding: '40px', textAlign: 'center', color: '#999' }}>
                        No audit log entries found
                      </td>
                    </tr>
                  ) : (
                    table.getRowModel().rows.map((row) => (
                      <tr key={row.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                        {row.getVisibleCells().map((cell) => (
                          <td key={cell.id} style={{ padding: '12px 16px' }}>
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                          </td>
                        ))}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>

              {/* Pagination */}
              <Stack direction="row" spacing={2} justifyContent="space-between" alignItems="center" sx={{ p: 2 }}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <span>
                    Page {table.getState().pagination.pageIndex + 1} of {table.getPageCount()}
                  </span>
                  <span style={{ color: '#999' }}>
                    ({filteredData.length} {filteredData.length === 1 ? 'entry' : 'entries'})
                  </span>
                </Stack>

                <Stack direction="row" spacing={1}>
                  <TextField
                    select
                    size="small"
                    value={table.getState().pagination.pageSize}
                    onChange={(e) => table.setPageSize(Number(e.target.value))}
                    sx={{ width: '120px' }}
                  >
                    {[10, 25, 50, 100].map((size) => (
                      <MenuItem key={size} value={size}>
                        Show {size}
                      </MenuItem>
                    ))}
                  </TextField>

                  <Stack direction="row" spacing={0.5}>
                    <button
                      onClick={() => table.previousPage()}
                      disabled={!table.getCanPreviousPage()}
                      style={{
                        padding: '8px 12px',
                        border: '1px solid #ddd',
                        background: '#fff',
                        cursor: table.getCanPreviousPage() ? 'pointer' : 'not-allowed',
                        borderRadius: '4px'
                      }}
                    >
                      Previous
                    </button>
                    <button
                      onClick={() => table.nextPage()}
                      disabled={!table.getCanNextPage()}
                      style={{
                        padding: '8px 12px',
                        border: '1px solid #ddd',
                        background: '#fff',
                        cursor: table.getCanNextPage() ? 'pointer' : 'not-allowed',
                        borderRadius: '4px'
                      }}
                    >
                      Next
                    </button>
                  </Stack>
                </Stack>
              </Stack>
            </Stack>
          </ScrollX>
            </>
          )}
        </MainCard>
      </Grid>
    </Grid>
  );
}
