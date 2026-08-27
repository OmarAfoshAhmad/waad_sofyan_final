/**
 * RBAC Users List Page - Simple Format
 * Similar to UnifiedMembersList - uses basic MUI Table
 *
 * Features:
 * - Simple table with pagination
 * - Toggle status (activate/deactivate)
 * - View and Edit actions
 */

import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
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
  DialogActions,
  InputAdornment,
  MenuItem,
  useMediaQuery
} from '@mui/material';
import {
  Add as AddIcon,
  Visibility as VisibilityIcon,
  Edit as EditIcon,
  Refresh as RefreshIcon,
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  AdminPanelSettings as AdminPanelSettingsIcon,
  PeopleAlt as PeopleAltIcon,
  Search as SearchIcon,
  FilterAltOff as FilterAltOffIcon,
  UploadFile as UploadFileIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import usersService from 'services/rbac/users.service';
import { openSnackbar } from 'api/snackbar';
import { getRoleDisplayName, SystemRole } from 'constants/rbac';
import ProviderUsersImportModal from './ProviderUsersImportModal';
import useAuth from 'hooks/useAuth';

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
    MEDICAL_REVIEW_HEAD: 'primary',
    INSURANCE_MANAGER: 'primary',
    PROVIDER_STAFF: 'info',
    EMPLOYER_ADMIN: 'primary',
    DATA_ENTRY: 'default',
    FINANCE_VIEWER: 'default'
  };
  return roleColors[roleName] || 'default';
};

const TABLE_BADGE_SX = {
  width: '10.625rem',
  maxWidth: 'none',
  justifyContent: 'center',
  fontWeight: 600,
  '& .MuiChip-label': { px: 1.25, whiteSpace: 'nowrap', overflow: 'visible', textOverflow: 'clip' }
};

/**
 * Users List Component
 */
const UsersList = () => {
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const permissions = new Set(currentUser?.permissions || []);
  const canManageUsers = permissions.has('USER_MANAGE');
  const [searchParams] = useSearchParams();
  const preferredPageSize = useMediaQuery('(max-height:900px)') ? 6 : 7;

  // State
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);

  // Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(preferredPageSize);

  // Search
  const [searchTerm, setSearchTerm] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [selectedRole, setSelectedRole] = useState(searchParams.get('role') || '');
  const [activeFilter, setActiveFilter] = useState('');
  const [providerLinkFilter, setProviderLinkFilter] = useState('');

  // Toggle Status Dialog
  const [toggleDialog, setToggleDialog] = useState({ open: false, user: null });
  const [toggling, setToggling] = useState(false);

  // Import Dialog
  const [importModalOpen, setImportModalOpen] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const result = await usersService.getUsersTable({
        page: page + 1,
        size: rowsPerPage,
        search: appliedSearch,
        role: selectedRole,
        active: activeFilter,
        providerLink: providerLinkFilter
      });

      setUsers(result.items || []);
      setTotalElements(result.total || 0);
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
  }, [page, rowsPerPage, appliedSearch, selectedRole, activeFilter, providerLinkFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  useEffect(() => {
    setRowsPerPage((currentSize) => (currentSize === 6 || currentSize === 7 ? preferredPageSize : currentSize));
    setPage(0);
  }, [preferredPageSize]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setPage(0);
      setAppliedSearch(searchTerm.trim());
    }, 350);
    return () => clearTimeout(timer);
  }, [searchTerm]);

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
    return <Chip label={isActive ? 'نشط' : 'معطل'} color={isActive ? 'success' : 'default'} size="small" sx={TABLE_BADGE_SX} />;
  };

  const isSuperAdmin = (user) => {
    return user?.roles?.some((role) => role?.name === 'SUPER_ADMIN');
  };

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', overflow: 'hidden', width: '100%' }}>
      {/* Page Header */}
      <ModernPageHeader
        title="إدارة المستخدمين"
        subtitle="عرض وإدارة المستخدمين وصلاحياتهم"
        icon={<PeopleAltIcon />}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'المستخدمين' }]}
        actions={
          <Stack direction="row" spacing={1} flexWrap="nowrap" sx={{ overflowX: 'auto', maxWidth: '100%' }}>
            {canManageUsers && <Button
              variant="outlined"
              color="secondary"
              startIcon={<UploadFileIcon />}
              onClick={() => setImportModalOpen(true)}
              sx={{ minWidth: '9.6875rem', whiteSpace: 'nowrap' }}
            >
              استيراد مستخدمي المرافق
            </Button>}
            {canManageUsers && <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => navigate('/admin/users/create')}
              sx={{ minWidth: '9.6875rem', whiteSpace: 'nowrap' }}
            >
              إضافة مستخدم
            </Button>}
          </Stack>
        }
        sx={{ mb: 0.5 }}
      />

      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 1, overflow: 'hidden' }}>
        {/* Search */}
        <Box sx={{ flexShrink: 0 }}>
          <MainCard sx={{ mb: 1 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Tooltip title="تحديث">
                <IconButton
                  onClick={handleRefresh}
                  color="primary"
                  sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
                >
                  <RefreshIcon />
                </IconButton>
              </Tooltip>
              <Chip
                icon={<PeopleAltIcon fontSize="small" />}
                label={`${totalElements} مستخدم`}
                variant="outlined"
                color="primary"
                sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: 1 }}
              />
              <Box sx={{ flexGrow: 1, minWidth: { md: '12.5rem' } }}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="اسم المستخدم، الاسم الكامل، البريد الإلكتروني..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon color="action" />
                      </InputAdornment>
                    ),
                    sx: { height: '2.5rem' }
                  }}
                />
              </Box>
              <TextField
                select
                size="small"
                label="الدور"
                value={selectedRole}
                onChange={(event) => {
                  setSelectedRole(event.target.value);
                  setPage(0);
                }}
                sx={{ minWidth: '11rem', bgcolor: 'background.paper' }}
                InputProps={{ sx: { height: '2.5rem' } }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="">الكل</MenuItem>
                {Object.values(SystemRole).map((role) => (
                  <MenuItem key={role} value={role}>
                    {getRoleDisplayName(role, 'ar')}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                size="small"
                label="الحالة"
                value={activeFilter}
                onChange={(event) => {
                  setActiveFilter(event.target.value);
                  setPage(0);
                }}
                sx={{ minWidth: '8rem', bgcolor: 'background.paper' }}
                InputProps={{ sx: { height: '2.5rem' } }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="">الكل</MenuItem>
                <MenuItem value="true">نشط</MenuItem>
                <MenuItem value="false">معطل</MenuItem>
              </TextField>
              <TextField
                select
                size="small"
                label="الارتباط بالمرفق"
                value={providerLinkFilter}
                onChange={(event) => {
                  setProviderLinkFilter(event.target.value);
                  setPage(0);
                }}
                sx={{ minWidth: '10rem', bgcolor: 'background.paper' }}
                InputProps={{ sx: { height: '2.5rem' } }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="">الكل</MenuItem>
                <MenuItem value="LINKED">مرتبط</MenuItem>
                <MenuItem value="UNLINKED">غير مرتبط</MenuItem>
              </TextField>
              <Button
                variant="outlined"
                color="secondary"
                startIcon={<FilterAltOffIcon />}
                onClick={() => {
                  setSearchTerm('');
                  setAppliedSearch('');
                  setSelectedRole('');
                  setActiveFilter('');
                  setProviderLinkFilter('');
                  setPage(0);
                }}
                sx={{ minWidth: '7.5rem', height: '2.5rem' }}
              >
                إعادة ضبط
              </Button>
            </Stack>
          </MainCard>
        </Box>

        {/* Users Table */}
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex' }}>
          <Paper
            variant="outlined"
            sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden', borderRadius: 1 }}
          >
            <TableContainer sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
              <Table stickyHeader size="small" aria-label="users table">
                <TableHead>
                  <TableRow>
                    <TableCell width="5%">#</TableCell>
                    <TableCell width="25%">المستخدم</TableCell>
                    <TableCell width="20%">البريد الإلكتروني</TableCell>
                    <TableCell width="20%">الأدوار</TableCell>
                    <TableCell width="10%">الحالة</TableCell>
                    <TableCell align="center" width="10%">
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
                        {canManageUsers && <Button
                          variant="outlined"
                          startIcon={<AddIcon />}
                          onClick={() => navigate('/admin/users/create')}
                          sx={{ mt: '1.0rem' }}
                        >
                          إضافة مستخدم
                        </Button>}
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
                          {(() => {
                            const userRoles = user?.roles || (user?.role ? [{ name: user.role }] : []);
                            return userRoles.length > 0 ? (
                              <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                                {userRoles.slice(0, 3).map((role) => (
                                  <Chip
                                    key={role?.id || role?.name}
                                    label={getRoleDisplayName(role?.name, 'ar') || role?.name || '-'}
                                    size="small"
                                    color={getRoleColor(role?.name)}
                                    variant="outlined"
                                    icon={<AdminPanelSettingsIcon sx={{ fontSize: '14px !important' }} />}
                                    sx={TABLE_BADGE_SX}
                                  />
                                ))}
                                {userRoles.length > 3 && <Chip label={`+${userRoles.length - 3}`} size="small" variant="outlined" />}
                              </Stack>
                            ) : (
                              <Typography variant="caption" color="text.disabled">
                                لا توجد أدوار
                              </Typography>
                            );
                          })()}
                        </TableCell>
                        <TableCell>{getStatusChip(user)}</TableCell>
                        <TableCell align="center">
                          <Stack direction="row" spacing={0.5} justifyContent="center">
                            <Tooltip title="عرض">
                              <IconButton size="small" color="primary" onClick={() => navigate(`/admin/users/${user.id}`)}>
                                <VisibilityIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            {canManageUsers && <Tooltip title="تعديل">
                              <IconButton size="small" color="info" onClick={() => navigate(`/admin/users/${user.id}/edit`)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>}
                            {canManageUsers && <Tooltip title={user?.active !== false ? 'تعطيل' : 'تفعيل'}>
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
                            </Tooltip>}
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
              rowsPerPageOptions={[6, 7, 10, 20, 50]}
              labelRowsPerPage="عدد الصفوف:"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
              sx={{ flexShrink: 0, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper', overflow: 'hidden' }}
            />
          </Paper>
        </Box>
      </Box>

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
            variant="outlined"
            startIcon={<AdminPanelSettingsIcon />}
            onClick={() => navigate('/admin/users/roles')}
          >
            الأدوار والصلاحيات
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

      {/* Import Modal */}
      <ProviderUsersImportModal
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={() => {
          setImportModalOpen(false);
          fetchUsers();
        }}
      />
    </Box>
  );
};

export default UsersList;
