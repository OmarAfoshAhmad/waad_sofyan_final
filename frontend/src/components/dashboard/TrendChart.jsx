import { Card, CardContent, CardHeader } from '@mui/material';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

/**
 * Trend chart for PreAuth submissions over time
 */
const TrendChart = ({ data, loading, days = 30 }) => {
  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title={`الاتجاهات (آخر ${days} يوم)`} />
        <CardContent>
          <ResponsiveContainer width="100%" height={300}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>جاري التحميل...</div>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    );
  }

  // Transform data for chart
  const chartData = data.map((item) => ({
    date: new Date(item.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    count: item.count || 0
  }));

  return (
    <Card>
      <CardHeader title={`اتجاهات الطلبات (آخر ${days} يوم)`} />
      <CardContent>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" style={{ fontSize: '0.75rem', direction: 'rtl' }} />
            <YAxis />
            <Tooltip formatter={(value) => [value.toLocaleString('en-US'), 'العدد']} contentStyle={{ direction: 'rtl' }} />
            <Legend wrapperStyle={{ direction: 'rtl' }} />
            <Line type="monotone" dataKey="count" stroke="#1976d2" strokeWidth={2} name="عدد الطلبات" dot={{ r: 4 }} activeDot={{ r: 6 }} />
          </LineChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
};

export default TrendChart;


