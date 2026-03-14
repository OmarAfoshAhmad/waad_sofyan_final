import { Card, CardContent, CardHeader, Typography, Box, Chip } from '@mui/material';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';

const STATUS_COLORS = {
  PENDING: '#FFA726',
  APPROVED: '#66BB6A',
  REJECTED: '#EF5350',
  CANCELLED: '#9E9E9E'
};

const STATUS_LABELS = {
  PENDING: 'قيد المراجعة',
  APPROVED: 'موافق عليه',
  REJECTED: 'مرفوض',
  CANCELLED: 'ملغي'
};

/**
 * Status distribution pie chart
 */
const StatusPieChart = ({ data, loading }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="توزيع الحالات" />
        <CardContent>
          <Typography variant="body2" color="text.secondary" align="center">
            جاري التحميل...
          </Typography>
        </CardContent>
      </Card>
    );
  }

  // Transform data for pie chart
  const chartData = Object.entries(data).map(([status, count]) => ({
    name: STATUS_LABELS[status] || status,
    value: count,
    color: STATUS_COLORS[status] || '#757575'
  }));

  const total = chartData.reduce((sum, item) => sum + item.value, 0);

  const renderCustomLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent }) => {
    const RADIAN = Math.PI / 180;
    const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
    const x = cx + radius * Math.cos(-midAngle * RADIAN);
    const y = cy + radius * Math.sin(-midAngle * RADIAN);

    return (
      <text x={x} y={y} fill="white" textAnchor={x > cx ? 'start' : 'end'} dominantBaseline="central" fontWeight="bold">
        {`${(percent * 100).toFixed(0)}%`}
      </text>
    );
  };

  return (
    <Card>
      <CardHeader title="توزيع الحالات" subheader={`إجمالي: ${total.toLocaleString('en-US')}`} />
      <CardContent>
        <ResponsiveContainer width="100%" height={300}>
          <PieChart>
            <Pie
              data={chartData}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={renderCustomLabel}
              outerRadius={100}
              fill="#8884d8"
              dataKey="value"
            >
              {chartData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip formatter={(value) => value.toLocaleString('en-US')} contentStyle={{ direction: 'rtl' }} />
            <Legend
              wrapperStyle={{ direction: 'rtl' }}
              formatter={(value, entry) => (
                <span>
                  {value} ({entry.payload.value.toLocaleString('en-US')})
                </span>
              )}
            />
          </PieChart>
        </ResponsiveContainer>

        {/* Status chips */}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: '1.0rem', justifyContent: 'center' }}>
          {chartData.map((item) => (
            <Chip
              key={item.name}
              label={`${item.name}: ${item.value.toLocaleString('en-US')}`}
              sx={{ bgcolor: item.color, color: 'white' }}
              size="small"
            />
          ))}
        </Box>
      </CardContent>
    </Card>
  );
};

export default StatusPieChart;
