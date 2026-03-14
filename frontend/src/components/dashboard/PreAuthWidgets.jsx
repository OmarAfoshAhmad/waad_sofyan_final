import PropTypes from 'prop-types';
import {
  Card,
  CardContent,
  CardHeader,
  Typography,
  Box,
  Stack,
  Chip,
  Alert,
  LinearProgress,
  Avatar,
  IconButton,
  Tooltip
} from '@mui/material';
import { TrendingUp, TrendingDown, CheckCircle, Cancel, Pending, Warning, Schedule, Visibility, Edit, Delete } from '@mui/icons-material';
import { PieChart } from '@mui/x-charts/PieChart';
import { LineChart } from '@mui/x-charts/LineChart';
import { BarChart } from '@mui/x-charts/BarChart';
import { DataGrid } from '@mui/x-data-grid';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/ar';

dayjs.extend(relativeTime);
dayjs.locale('ar');

// ============================|| STATS CARD ||============================ //

export const StatsCard = ({ title, value, change, icon: Icon, color = 'primary', prefix = '', suffix = '' }) => {
  const isPositive = change > 0;
  const isNegative = change < 0;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {title}
              </Typography>
              <Typography variant="h4" fontWeight="bold">
                {prefix}
                {value?.toLocaleString('en-US') || 0}
                {suffix}
              </Typography>
            </Box>
            {Icon && (
              <Box
                sx={{
                  p: '0.75rem',
                  borderRadius: '0.125rem',
                  bgcolor: `${color}.lighter`,
                  color: `${color}.main`
                }}
              >
                <Icon />
              </Box>
            )}
          </Box>

          {change !== undefined && change !== null && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              {isPositive && <TrendingUp fontSize="small" color="success" />}
              {isNegative && <TrendingDown fontSize="small" color="error" />}
              <Typography
                variant="body2"
                color={isPositive ? 'success.main' : isNegative ? 'error.main' : 'text.secondary'}
                fontWeight="medium"
              >
                {isPositive && '+'}
                {change}% من الشهر الماضي
              </Typography>
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

StatsCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.number,
  change: PropTypes.number,
  icon: PropTypes.elementType,
  color: PropTypes.string,
  prefix: PropTypes.string,
  suffix: PropTypes.string
};

// ============================|| STATUS DISTRIBUTION PIE CHART ||============================ //

export const StatusDistributionChart = ({ data, loading }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="توزيع الحالات" />
        <CardContent>
          <Box sx={{ height: '18.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {loading ? <LinearProgress /> : <Typography color="text.secondary">لا توجد بيانات</Typography>}
          </Box>
        </CardContent>
      </Card>
    );
  }

  // Status colors mapping
  const statusColors = {
    PENDING: '#ff9800',
    REQUESTED: '#2196f3',
    APPROVED: '#4caf50',
    REJECTED: '#f44336',
    CANCELLED: '#9e9e9e',
    EXPIRED: '#795548',
    USED: '#607d8b'
  };

  // Status labels in Arabic
  const statusLabels = {
    PENDING: 'قيد الانتظار',
    REQUESTED: 'مطلوبة',
    APPROVED: 'معتمدة',
    REJECTED: 'مرفوضة',
    CANCELLED: 'ملغاة',
    EXPIRED: 'منتهية',
    USED: 'مستخدمة'
  };

  const chartData = data.map((item, index) => ({
    id: index,
    value: item.count || 0,
    label: statusLabels[item.status] || item.status,
    color: statusColors[item.status] || '#757575'
  }));

  return (
    <Card>
      <CardHeader title="توزيع الحالات" />
      <CardContent>
        <Box sx={{ height: '18.75rem', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
          <PieChart
            series={[
              {
                data: chartData,
                highlightScope: { faded: 'global', highlighted: 'item' },
                faded: { innerRadius: 30, additionalRadius: -30, color: 'gray' }
              }
            ]}
            height={300}
            slotProps={{
              legend: {
                direction: 'column',
                position: { vertical: 'middle', horizontal: 'right' },
                padding: 0
              }
            }}
          />
        </Box>
      </CardContent>
    </Card>
  );
};

StatusDistributionChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      status: PropTypes.string.isRequired,
      count: PropTypes.number.isRequired
    })
  ),
  loading: PropTypes.bool
};

// ============================|| HIGH PRIORITY QUEUE ||============================ //

export const HighPriorityQueue = ({ data, loading, onView, onEdit, onDelete }) => {
  const columns = [
    {
      field: 'referenceNumber',
      headerName: 'الرقم المرجعي',
      flex: 1,
      minWidth: '9.375rem'
    },
    {
      field: 'memberName',
      headerName: 'المريض',
      flex: 1,
      minWidth: '9.375rem'
    },
    {
      field: 'priority',
      headerName: 'الأولوية',
      width: '7.5rem',
      renderCell: (params) => {
        const color = params.value === 'EMERGENCY' ? 'error' : 'warning';
        const label = params.value === 'EMERGENCY' ? 'طارئ' : 'عاجل';
        return <Chip label={label} color={color} size="small" />;
      }
    },
    {
      field: 'requestedAmount',
      headerName: 'المبلغ',
      width: '7.5rem',
      renderCell: (params) => <Typography variant="body2">{params.value?.toLocaleString('en-US')} د.ل</Typography>
    },
    {
      field: 'submittedDate',
      headerName: 'تاريخ التقديم',
      width: '8.75rem',
      renderCell: (params) => dayjs(params.value).format('YYYY/MM/DD')
    },
    {
      field: 'actions',
      headerName: 'إجراءات',
      width: '9.375rem',
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={0.5}>
          <Tooltip title="عرض">
            <IconButton size="small" onClick={() => onView?.(params.row)}>
              <Visibility fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="تعديل">
            <IconButton size="small" onClick={() => onEdit?.(params.row)}>
              <Edit fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="حذف">
            <IconButton size="small" color="error" onClick={() => onDelete?.(params.row)}>
              <Delete fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      )
    }
  ];

  return (
    <Card>
      <CardHeader
        title="الطلبات العاجلة"
        subheader="الطلبات ذات الأولوية العالية (طارئ / عاجل)"
        action={<Chip label={`${data?.length || 0} طلب`} color="warning" size="small" />}
      />
      <CardContent>
        <DataGrid
          rows={data || []}
          columns={columns}
          loading={loading}
          autoHeight
          pageSize={5}
          rowsPerPageOptions={[5]}
          disableSelectionOnClick
          sx={{
            '& .MuiDataGrid-root': {
              border: 'none'
            },
            '& .MuiDataGrid-cell': {
              borderBottom: '1px solid #f0f0f0'
            },
            '& .MuiDataGrid-columnHeaders': {
              backgroundColor: '#fafafa',
              borderBottom: '2px solid #e0e0e0'
            }
          }}
        />
      </CardContent>
    </Card>
  );
};

HighPriorityQueue.propTypes = {
  data: PropTypes.arrayOf(PropTypes.object),
  loading: PropTypes.bool,
  onView: PropTypes.func,
  onEdit: PropTypes.func,
  onDelete: PropTypes.func
};

// ============================|| EXPIRING SOON ALERTS ||============================ //

export const ExpiringSoonAlerts = ({ data, loading, withinDays = 7 }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="تنبيهات الانتهاء" subheader={`الطلبات التي ستنتهي خلال ${withinDays} أيام`} />
        <CardContent>
          <Alert severity="info">لا توجد طلبات تنتهي قريباً</Alert>
        </CardContent>
      </Card>
    );
  }

  const calculateDaysLeft = (expiryDate) => {
    return Math.max(0, dayjs(expiryDate).diff(dayjs(), 'day'));
  };

  return (
    <Card>
      <CardHeader
        title="تنبيهات الانتهاء"
        subheader={`الطلبات التي ستنتهي خلال ${withinDays} أيام`}
        action={<Chip icon={<Warning />} label={`${data.length} تنبيه`} color="warning" size="small" />}
      />
      <CardContent>
        <Stack spacing={2}>
          {data.slice(0, 10).map((item) => {
            const daysLeft = calculateDaysLeft(item.expiryDate);
            const isUrgent = daysLeft <= 2;

            return (
              <Box
                key={item.id}
                sx={{
                  p: '1.0rem',
                  borderRadius: 1,
                  bgcolor: isUrgent ? 'error.lighter' : 'warning.lighter',
                  border: 1,
                  borderColor: isUrgent ? 'error.light' : 'warning.light'
                }}
              >
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="subtitle2" fontWeight="bold">
                      {item.referenceNumber}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {item.memberName}
                    </Typography>
                  </Box>
                  <Stack alignItems="center" spacing={0.5}>
                    <Chip icon={<Schedule />} label={`${daysLeft} يوم`} color={isUrgent ? 'error' : 'warning'} size="small" />
                    <Typography variant="caption" color="text.secondary">
                      ينتهي {dayjs(item.expiryDate).format('YYYY/MM/DD')}
                    </Typography>
                  </Stack>
                </Stack>
              </Box>
            );
          })}
        </Stack>
      </CardContent>
    </Card>
  );
};

ExpiringSoonAlerts.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.number,
      referenceNumber: PropTypes.string,
      memberName: PropTypes.string,
      expiryDate: PropTypes.string
    })
  ),
  loading: PropTypes.bool,
  withinDays: PropTypes.number
};

// ============================|| TRENDS LINE CHART ||============================ //

export const TrendsChart = ({ data, loading, days = 30 }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title={`اتجاهات الطلبات (آخر ${days} يوم)`} />
        <CardContent>
          <Box sx={{ height: '18.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {loading ? <LinearProgress /> : <Typography color="text.secondary">لا توجد بيانات</Typography>}
          </Box>
        </CardContent>
      </Card>
    );
  }

  // Transform data for chart
  const xLabels = data.map((item) => dayjs(item.date).format('MM/DD'));

  const requestedData = data.map((item) => item.requestedCount || 0);
  const approvedData = data.map((item) => item.approvedCount || 0);

  return (
    <Card>
      <CardHeader title={`اتجاهات الطلبات (آخر ${days} يوم)`} />
      <CardContent>
        <Box sx={{ height: '18.75rem' }}>
          <LineChart
            series={[
              { data: requestedData, label: 'المطلوبة', color: '#2196f3' },
              { data: approvedData, label: 'المعتمدة', color: '#4caf50' }
            ]}
            xAxis={[{ scaleType: 'point', data: xLabels }]}
            height={300}
            margin={{ left: '3.125rem', right: '1.25rem', top: '10.0rem', bottom: '15.0rem' }}
            slotProps={{
              legend: {
                direction: 'row',
                position: { vertical: 'top', horizontal: 'right' }
              }
            }}
          />
        </Box>
      </CardContent>
    </Card>
  );
};

TrendsChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      date: PropTypes.string,
      requestedCount: PropTypes.number,
      approvedCount: PropTypes.number
    })
  ),
  loading: PropTypes.bool,
  days: PropTypes.number
};

// ============================|| TOP PROVIDERS BAR CHART ||============================ //

export const TopProvidersChart = ({ data, loading, limit = 10 }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title={`أكثر ${limit} مقدمي خدمات`} subheader="حسب عدد الطلبات" />
        <CardContent>
          <Box sx={{ height: '18.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {loading ? <LinearProgress /> : <Typography color="text.secondary">لا توجد بيانات</Typography>}
          </Box>
        </CardContent>
      </Card>
    );
  }

  const xLabels = data.map((item) => item.providerName || 'غير محدد');
  const requestsData = data.map((item) => item.totalRequests || 0);
  const approvalRateData = data.map((item) => item.approvalRate || 0);

  return (
    <Card>
      <CardHeader title={`أكثر ${limit} مقدمي خدمات`} subheader="حسب عدد الطلبات ونسبة الموافقة" />
      <CardContent>
        <Box sx={{ height: '21.875rem' }}>
          <BarChart
            series={[
              { data: requestsData, label: 'عدد الطلبات', stack: 'A', color: '#2196f3' },
              { data: approvalRateData, label: 'نسبة الموافقة %', stack: 'B', color: '#4caf50' }
            ]}
            xAxis={[
              {
                scaleType: 'band',
                data: xLabels,
                tickLabelStyle: {
                  angle: -45,
                  textAnchor: 'end',
                  fontSize: '0.75rem'
                }
              }
            ]}
            height={350}
            margin={{ left: '3.125rem', right: '1.25rem', top: '10.0rem', bottom: '50.0rem' }}
            slotProps={{
              legend: {
                direction: 'row',
                position: { vertical: 'top', horizontal: 'right' }
              }
            }}
          />
        </Box>
      </CardContent>
    </Card>
  );
};

TopProvidersChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      providerName: PropTypes.string,
      totalRequests: PropTypes.number,
      approvalRate: PropTypes.number
    })
  ),
  loading: PropTypes.bool,
  limit: PropTypes.number
};

// ============================|| RECENT ACTIVITY TIMELINE ||============================ //

export const RecentActivityTimeline = ({ data, loading, limit = 10 }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="النشاط الأخير" subheader={`آخر ${limit} إجراءات`} />
        <CardContent>
          <Alert severity="info">لا يوجد نشاط حديث</Alert>
        </CardContent>
      </Card>
    );
  }

  const getActionIcon = (action) => {
    const icons = {
      CREATE: <Pending />,
      UPDATE: <Edit />,
      APPROVE: <CheckCircle />,
      REJECT: <Cancel />,
      CANCEL: <Cancel />,
      DELETE: <Delete />
    };
    return icons[action] || <Pending />;
  };

  const getActionColor = (action) => {
    const colors = {
      CREATE: 'info',
      UPDATE: 'warning',
      APPROVE: 'success',
      REJECT: 'error',
      CANCEL: 'secondary',
      DELETE: 'error'
    };
    return colors[action] || 'default';
  };

  const getActionLabel = (action) => {
    const labels = {
      CREATE: 'إنشاء',
      UPDATE: 'تحديث',
      APPROVE: 'موافقة',
      REJECT: 'رفض',
      CANCEL: 'إلغاء',
      DELETE: 'حذف'
    };
    return labels[action] || action;
  };

  return (
    <Card>
      <CardHeader title="النشاط الأخير" subheader={`آخر ${limit} إجراءات`} />
      <CardContent>
        <Stack spacing={2}>
          {data.map((activity, index) => (
            <Box
              key={activity.id || index}
              sx={{
                display: 'flex',
                gap: '1.0rem',
                p: '0.75rem',
                borderRadius: 1,
                bgcolor: 'background.default',
                '&:hover': { bgcolor: 'action.hover' }
              }}
            >
              <Avatar
                sx={{
                  bgcolor: `${getActionColor(activity.action)}.lighter`,
                  color: `${getActionColor(activity.action)}.main`
                }}
              >
                {getActionIcon(activity.action)}
              </Avatar>
              <Box sx={{ flex: 1 }}>
                <Typography variant="subtitle2">{activity.referenceNumber}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {getActionLabel(activity.action)} بواسطة {activity.changedBy}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {dayjs(activity.changeDate).fromNow()}
                </Typography>
              </Box>
              <Chip label={getActionLabel(activity.action)} color={getActionColor(activity.action)} size="small" />
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
};

RecentActivityTimeline.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.number,
      referenceNumber: PropTypes.string,
      action: PropTypes.string,
      changedBy: PropTypes.string,
      changeDate: PropTypes.string
    })
  ),
  loading: PropTypes.bool,
  limit: PropTypes.number
};



