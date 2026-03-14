import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

// material-ui
import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Skeleton,
  IconButton,
  useTheme
} from '@mui/material';
import EyeOutlined from '@ant-design/icons/EyeOutlined';

// project imports
import axios from 'utils/axios';

/**
 * RecentMembers Component
 * Phase B3 - Modern Dashboard Recent Members List
 *
 * Displays the most recently added members with:
 * - Full name
 * - Civil ID
 * - Employer name
 * - Status badge
 * - View action button
 */
export default function RecentMembers() {
  const theme = useTheme();
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchRecentMembers = async () => {
      try {
        setLoading(true);
        const response = await axios.get('/members/recent', {
          params: { limit: 5 }
        });
        setMembers(response.data.data || response.data || []);
      } catch (error) {
        console.error('Error fetching recent members:', error);
        setMembers([]);
      } finally {
        setLoading(false);
      }
    };

    fetchRecentMembers();
  }, []);

  const getStatusColor = (status) => {
    const statusColors = {
      ACTIVE: 'success',
      INACTIVE: 'default',
      SUSPENDED: 'error',
      PENDING: 'warning'
    };
    return statusColors[status] || 'default';
  };

  const getStatusLabel = (status) => {
    const statusLabels = {
      ACTIVE: 'نشط',
      INACTIVE: 'غير نشط',
      SUSPENDED: 'موقوف',
      PENDING: 'قيد المراجعة'
    };
    return statusLabels[status] || status;
  };

  return (
    <Card>
      <CardHeader
        title={
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            الأعضاء المضافون مؤخراً
          </Typography>
        }
        action={
          <Link to="/members" style={{ textDecoration: 'none' }}>
            <Typography variant="body2" color="primary" sx={{ cursor: 'pointer' }}>
              عرض الكل
            </Typography>
          </Link>
        }
      />
      <CardContent>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>الاسم الكامل</TableCell>
                <TableCell>الرقم المدني</TableCell>
                <TableCell>جهة العمل</TableCell>
                <TableCell align="center">الحالة</TableCell>
                <TableCell align="center">الإجراءات</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                // Loading skeletons
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      <Skeleton variant="text" width={150} />
                    </TableCell>
                    <TableCell>
                      <Skeleton variant="text" width={120} />
                    </TableCell>
                    <TableCell>
                      <Skeleton variant="text" width={180} />
                    </TableCell>
                    <TableCell align="center">
                      <Skeleton variant="rounded" width={70} height={24} sx={{ mx: 'auto' }} />
                    </TableCell>
                    <TableCell align="center">
                      <Skeleton variant="circular" width={32} height={32} sx={{ mx: 'auto' }} />
                    </TableCell>
                  </TableRow>
                ))
              ) : members.length === 0 ? (
                // No data message
                <TableRow>
                  <TableCell colSpan={5} align="center">
                    <Box sx={{ py: '2.0rem' }}>
                      <Typography variant="body2" color="text.secondary">
                        لا توجد بيانات للأعضاء المضافين مؤخراً
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              ) : (
                // Members list
                members.map((member) => (
                  <TableRow
                    key={member.id}
                    hover
                    sx={{
                      '&:hover': {
                        bgcolor: theme.palette.action.hover
                      }
                    }}
                  >
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {member.firstName} {member.lastName}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {member.nationalNumber || '-'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {member.employer?.name || '-'}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Chip
                        label={getStatusLabel(member.status)}
                        color={getStatusColor(member.status)}
                        size="small"
                        sx={{ minWidth: '4.375rem' }}
                      />
                    </TableCell>
                    <TableCell align="center">
                      <IconButton component={Link} to={`/members/view/${member.id}`} size="small" color="primary">
                        <EyeOutlined />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );
}
