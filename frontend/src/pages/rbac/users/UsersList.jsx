/**
 * RBAC Users List Page - Simple Format
 * Similar to UnifiedMembersList - uses basic MUI Table
 *
 * Features:
 * - Simple table with pagination
 * - Toggle status (activate/deactivate)
 * - View and Edit actions
 */

import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  Grid,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
  Tooltip,
  CircularProgress,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from '@mui/material';
import {
  Add as AddIcon,
  Visibility as VisibilityIcon,
  Edit as EditIcon,
  Refresh as RefreshIcon,
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  AdminPanelSettings as AdminPanelSettingsIcon,
  PeopleAlt as PeopleAltIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import usersService from 'services/rbac/users.service';
import { openSnackbar } from 'api/snackbar';

/**
 * Get initials from name
 */
const getInitials = (name) => {
  if (!name) return '?';
  return name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
};

/**
 * Get role color based on role name
 */
const getRoleColor = (roleName) => {
  const roleColors = {
    SUPER_ADMIN: 'error',
    ACCOUNTANT: 'warning',
    MEDICAL_REVIEWER: 'secondary',
    PROVIDER_STAFF: 'info',
    EMPLOYER_ADMIN: 'primary',
    DATA_ENTRY: 'default',
    FINANCE_VIEWER: 'default'
  };
  return roleColors[roleName] || 'default';
};

/**
 * Users List Component
 */
const UsersList = () => {
  const navigate = useNavigate();

  // State
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);

  // Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);

  // Search
  const [searchTerm, setSearchTerm] = useState('');

  // Toggle Status Dialog
  const [toggleDialog, setToggleDialog] = useState({ open: false, user: null });
  const [toggling, setToggling] = useState(false);

  // Fetch users
  const [searchParams] = useSearchParams();
  const roleFilter = searchParams.get('role');

  useEffect(() => {
    fetchUsers();
  }, [page, rowsPerPage, roleFilter]);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const users = await usersService.getAllUsers();

      // Filter by role if provided in URL
      let filtered = users;
      if (roleFilter) {
        filtered = users.filter((u) => u.roles?.some((r) => r.name === roleFilter));
      }

      // Client-side filtering by search term
      if (searchTerm) {
        const term = searchTerm.toLowerCase();
        filtered = filtered.filter(
          (u) =>
            u.username?.toLowerCase().includes(term) || u.fullName?.toLowerCase().includes(term) || u.email?.toLowerCase().includes(term)
        );
      }

      // Client-side pagination
      const total = filtered.length;
      const start = page * rowsPerPage;
      const paginated = filtered.slice(start, start + rowsPerPage);

      setUsers(paginated);
      setTotalElements(total);
    } catch (error) {
      console.error('Error fetching users:', error);
      openSnackbar({
        open: true,
        message: 'خطأ في جلب المستخدمين',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    setPage(0);
    fetchUsers();
  };

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handlePageChange = (event, newPage) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleRefresh = () => {
    fetchUsers();
  };

  // Toggle Status
  const handleToggleClick = (user) => {
    setToggleDialog({ open: true, user });
  };

  const handleToggleClose = () => {
    setToggleDialog({ open: false, user: null });
  };

  const handleToggleConfirm = async () => {
    if (!toggleDialog.user) return;

    setToggling(true);
    try {
      const response = await usersService.toggleUserStatus(toggleDialog.user.id);

      openSnackbar({
        open: true,
        message: response?.message || 'تم تغيير حالة المستخدم بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      handleToggleClose();
      fetchUsers();
    } catch (error) {
      console.error('Error toggling user status:', error);
      openSnackbar({
        open: true,
        message: error?.response?.data?.message || 'فشل تغيير حالة المستخدم',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setToggling(false);
    }
  };

  const getStatusChip = (user) => {
    const isActive = user?.active !== false;
    return <Chip label={isActive ? 'نشط' : 'معطل'} color={isActive ? 'success' : 'default'} size="small" />;
  };

  const isSuperAdmin = (user) => {
    return user?.roles?.some((role) => role?.name === 'SUPER_ADMIN');
  };

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title="إدارة المستخدمين"
        subtitle="عرض وإدارة المستخدمين وصلاحياتهم"
        icon={PeopleAltIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'المستخدمين' }]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={handleRefresh}>
              تحديث
            </Button>
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/admin/users/create')}>
              إضافة مستخدم
            </Button>
          </Stack>
        }
      />

      <Grid container spacing={3}>
        {/* Search */}
        <Grid size={12}>
          <MainCard title="البحث">
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="بحث"
                  placeholder="اسم المستخدم، الاسم الكامل، البريد الإلكتروني..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  onKeyDown={handleSearchKeyDown}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <Button fullWidth variant="contained" onClick={handleSearch}>
                  بحث
                </Button>
              </Grid>
              <Grid size={{ xs: 12, md: 2 }}>
                <Button
                  fullWidth
                  variant="outlined"
                  onClick={() => {
                    setSearchTerm('');
                    setPage(0);
                    fetchUsers();
                  }}
                >
                  إعادة تعيين
                </Button>
              </Grid>
            </Grid>
          </MainCard>
        </Grid>

        {/* Users Table */}
        <Grid size={12}>
          <MainCard
            content={false}
            title={
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="h5">المستخدمين ({totalElements})</Typography>
                {loading && <CircularProgress size={24} />}
              </Stack>
            }
          >
            <TableContainer component={Paper} elevation={0}>
              <Table aria-label="users table">
                <TableHead>
                  <TableRow>
                    <TableCell width="5%">#</TableCell>
                    <TableCell width="25%">المستخدم</TableCell>
                    <TableCell width="20%">البريد الإلكتروني</TableCell>
                    <TableCell width="25%">الأدوار</TableCell>
                    <TableCell width="10%">الحالة</TableCell>
                    <TableCell align="center" width="15%">
                      إجراءات
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: '5.0rem' }}>
                        <CircularProgress />
                        <Typography variant="body2" color="text.secondary" sx={{ mt: '1.0rem' }}>
                          جاري تحميل المستخدمين...
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : users.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: '5.0rem' }}>
                        <Typography variant="h6" color="text.secondary">
                          لا توجد نتائج
                        </Typography>
                        <Button variant="outlined" startIcon={<AddIcon />} onClick={() => navigate('/admin/users/create')} sx={{ mt: '1.0rem' }}>
                          إضافة مستخدم
                        </Button>
                      </TableCell>
                    </TableRow>
                  ) : (
                    users.map((user, index) => (
                      <TableRow key={user.id} hover>
                        <TableCell>{page * rowsPerPage + index + 1}</TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={1.5} alignItems="center">
                            <Avatar sx={{ width: '2.0rem', height: '2.0rem', bgcolor: 'primary.main', fontSize: '0.875rem' }}>
                              {getInitials(user?.fullName || user?.username)}
                            </Avatar>
                            <Box>
                              <Typography variant="body2" fontWeight="medium">
                                {user?.fullName || '-'}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                @{user?.username || '-'}
                              </Typography>
                            </Box>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">
                            {user?.email || '-'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {user?.roles?.length > 0 ? (
                            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                              {user.roles.slice(0, 3).map((role) => (
                                <Chip
                                  key={role?.id || role?.name}
                                  label={role?.name || '-'}
                                  size="small"
                                  color={getRoleColor(role?.name)}
                                  variant="outlined"
                                  icon={<AdminPanelSettingsIcon sx={{ fontSize: '14px !important' }} />}
                                />
                              ))}
                              {user.roles.length > 3 && <Chip label={`+${user.roles.length - 3}`} size="small" variant="outlined" />}
                            </Stack>
                          ) : (
                            <Typography variant="caption" color="text.disabled">
                              لا توجد أدوار
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>{getStatusChip(user)}</TableCell>
                        <TableCell align="center">
                          <Stack direction="row" spacing={0.5} justifyContent="center">
                            <Tooltip title="عرض">
                              <IconButton size="small" color="primary" onClick={() => navigate(`/admin/users/${user.id}`)}>
                                <VisibilityIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="تعديل">
                              <IconButton size="small" color="info" onClick={() => navigate(`/admin/users/${user.id}/edit`)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title={user?.active !== false ? 'تعطيل' : 'تفعيل'}>
                              <span>
                                <IconButton
                                  size="small"
                                  color={user?.active !== false ? 'warning' : 'success'}
                                  onClick={() => handleToggleClick(user)}
                                  disabled={isSuperAdmin(user)}
                                >
                                  {user?.active !== false ? <BlockIcon fontSize="small" /> : <CheckCircleIcon fontSize="small" />}
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={totalElements}
              page={page}
              onPageChange={handlePageChange}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={handleRowsPerPageChange}
              rowsPerPageOptions={[10, 20, 50, 100]}
              labelRowsPerPage="عدد الصفوف:"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
            />
          </MainCard>
        </Grid>
      </Grid>

      {/* Toggle Status Confirmation Dialog */}
      <Dialog open={toggleDialog.open} onClose={handleToggleClose}>
        <DialogTitle>{toggleDialog.user?.active !== false ? 'تعطيل المستخدم' : 'تفعيل المستخدم'}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {toggleDialog.user?.active !== false
              ? `هل أنت متأكد من تعطيل المستخدم "${toggleDialog.user?.fullName || toggleDialog.user?.username}"؟ لن يتمكن من تسجيل الدخول.`
              : `هل أنت متأكد من تفعيل المستخدم "${toggleDialog.user?.fullName || toggleDialog.user?.username}"؟ سيتمكن من تسجيل الدخول مرة أخرى.`}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleToggleClose} disabled={toggling}>
            إلغاء
          </Button>
          <Button
            onClick={handleToggleConfirm}
            color={toggleDialog.user?.active !== false ? 'warning' : 'success'}
            variant="contained"
            disabled={toggling}
          >
            {toggling ? 'جاري التنفيذ...' : toggleDialog.user?.active !== false ? 'تعطيل' : 'تفعيل'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UsersList;
