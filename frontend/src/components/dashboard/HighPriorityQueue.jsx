import {
  Card,
  CardContent,
  CardHeader,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Typography,
  Box,
  IconButton
} from '@mui/material';
import { Warning, ChevronLeft } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';

const PRIORITY_COLORS = {
  EMERGENCY: 'error',
  URGENT: 'warning',
  NORMAL: 'info',
  LOW: 'default'
};

const PRIORITY_LABELS = {
  EMERGENCY: 'طوارئ',
  URGENT: 'عاجل',
  NORMAL: 'عادي',
  LOW: 'منخفض'
};

/**
 * High priority queue table
 */
const HighPriorityQueue = ({ data, loading }) => {
  const navigate = useNavigate();

  if (loading || !data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="قائمة الأولويات العالية" avatar={<Warning color="error" />} />
        <CardContent>
          <Typography variant="body2" color="text.secondary" align="center">
            {loading ? 'جاري التحميل...' : 'لا توجد طلبات عالية الأولوية'}
          </Typography>
        </CardContent>
      </Card>
    );
  }

  const handleRowClick = (id) => {
    navigate(`/pre-approvals/${id}`);
  };

  return (
    <Card>
      <CardHeader title="قائمة الأولويات العالية" avatar={<Warning color="error" />} subheader={`${data.length} طلب`} />
      <CardContent sx={{ p: 0 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>رقم المرجع</TableCell>
                <TableCell>المريض</TableCell>
                <TableCell>الأولوية</TableCell>
                <TableCell>تاريخ الإنشاء</TableCell>
                <TableCell align="center">الإجراء</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((item) => (
                <TableRow key={item.id} hover sx={{ cursor: 'pointer' }} onClick={() => handleRowClick(item.id)}>
                  <TableCell>
                    <Typography variant="body2" fontWeight="medium">
                      {item.referenceNumber}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{item.memberName}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={PRIORITY_LABELS[item.priority] || item.priority}
                      color={PRIORITY_COLORS[item.priority] || 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {new Date(item.requestDate).toLocaleDateString('en-GB')}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <IconButton size="small">
                      <ChevronLeft />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );
};

export default HighPriorityQueue;
