/**
 * Unified Member View Page
 *
 * Displays Principal member with expandable Dependents list.
 * Refactored to match UnifiedMemberCreate layout (Tabs).
 *
 * @module UnifiedMemberView
 * @since 2026-01-11
 */

import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Grid,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Tabs,
  Tab,
  Paper,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Alert,
  Avatar,
  Tooltip,
  TextField,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  FormHelperText,
  FormControlLabel,
  Switch,
  useTheme
} from '@mui/material';
import {
  Save as SaveIcon,
  Add as AddIcon,
  ArrowBack as ArrowBackIcon,
  Badge as BadgeIcon,
  ContactPhone as ContactPhoneIcon,
  Delete as DeleteIcon,
  DeleteOutline as DeleteOutlineIcon,
  Edit as EditIcon,
  ExpandMore as ExpandMoreIcon,
  FamilyRestroom as FamilyRestroomIcon,
  Person as PersonIcon,
  PersonAdd as PersonAddIcon,
  Print as PrintIcon,
  QrCode as QrCodeIcon,
  RestoreFromTrash as RestoreFromTrashIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { TablePagination } from '@mui/material';
import dayjs from 'dayjs';

// Projects Imports
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import MemberAvatar from 'components/tba/MemberAvatar';
import DependentModal from './DependentModal';
import { getMember, deleteMember, hardDeleteMember, restoreMember, MEMBER_TYPES, GENDERS, RELATIONSHIPS } from 'services/api/unified-members.service';
import { openSnackbar } from 'api/snackbar';

import { RELATIONSHIP_AR } from './member.shared';

/**
 * Unified Member View Component
 */
const UnifiedMemberView = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { id } = useParams();

  const [loading, setLoading] = useState(true);
  const [member, setMember] = useState(null);
  const [dependents, setDependents] = useState([]);
  const [tabValue, setTabValue] = useState(0);

  // Refactored Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedDependent, setSelectedDependent] = useState(null); // null = Add Mode
  const [showDeleted, setShowDeleted] = useState(false);

  // Pagination
  const [pg, setPg] = useState(0);
  const [rpp, setRpp] = useState(3);

  // Dialog States
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingMember, setDeletingMember] = useState(null);
  const [hardDeleteDepDialogOpen, setHardDeleteDepDialogOpen] = useState(false);
  const [hardDeletingDep, setHardDeletingDep] = useState(null);

  const handleChangePage = (event, newPage) => {
    setPg(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRpp(parseInt(event.target.value, 10));
    setPg(0);
  };

  useEffect(() => {
    if (id) {
      fetchMemberData();
    }
  }, [id]);

  const fetchMemberData = async () => {
    setLoading(true);
    try {
      const response = await getMember(id);
      setMember(response);
      setDependents(response.dependents || []);
    } catch (error) {
      console.error('Error fetching member:', error);
      openSnackbar({
        open: true,
        message: 'خطأ في جلب بيانات المنتفع',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  };

  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  // --- Action Handlers ---
  const handleAddClick = () => {
    setSelectedDependent(null);
    setModalOpen(true);
  };

  const handleEditClick = (dep) => {
    setSelectedDependent(dep);
    setModalOpen(true);
  };

  const handleModalSave = () => {
    fetchMemberData();
    setModalOpen(false);
  };

  const handleRestore = async (id) => {
    try {
      await restoreMember(id);
      openSnackbar({ open: true, message: 'تم استعادة التابع بنجاح', variant: 'alert', alert: { color: 'success' } });
      fetchMemberData();
    } catch (error) {
      console.error('Error restoring member:', error);
      openSnackbar({ open: true, message: 'خطأ في استعادة التابع', variant: 'alert', alert: { color: 'error' } });
    }
  };

  const handleHardDeleteDepConfirm = (dep) => {
    setHardDeletingDep(dep);
    setHardDeleteDepDialogOpen(true);
  };

  const handleHardDeleteDepExecute = async () => {
    if (!hardDeletingDep) return;
    try {
      await hardDeleteMember(hardDeletingDep.id);
      openSnackbar({ open: true, message: 'تم الحذف النهائي للتابع بنجاح', variant: 'alert', alert: { color: 'success' } });
      fetchMemberData();
    } catch (error) {
      console.error('Error hard-deleting dependent:', error);
      openSnackbar({
        open: true,
        message: error.response?.data?.message || 'خطأ في الحذف النهائي',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setHardDeleteDepDialogOpen(false);
      setHardDeletingDep(null);
    }
  };

  const handleDeleteConfirm = (targetMember) => {
    setDeletingMember(targetMember);
    setDeleteDialogOpen(true);
  };

  const handleDeleteExecute = async () => {
    if (!deletingMember) return;

    try {
      await deleteMember(deletingMember.id);

      const isPrincipal = deletingMember.type === MEMBER_TYPES.PRINCIPAL;

      openSnackbar({
        open: true,
        message: isPrincipal ? 'تم حذف المنتفع الرئيسي وجميع تابعيه بنجاح' : 'تم حذف المنتفع التابع بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      if (isPrincipal) {
        navigate('/members');
      } else {
        fetchMemberData();
      }
    } catch (error) {
      console.error('Error deleting member:', error);
      openSnackbar({
        open: true,
        message: error.response?.data?.message || 'خطأ في حذف المنتفع',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setDeleteDialogOpen(false);
      setDeletingMember(null);
    }
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (!member) {
    return (
      <Box>
        <Alert severity="error">لم يتم العثور على المنتفع</Alert>
        <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/members')} sx={{ mt: '1.0rem' }}>
          رجوع للقائمة
        </Button>
      </Box>
    );
  }

  const isPrincipal = member.type === MEMBER_TYPES.PRINCIPAL;

  return (
    <>
      <ModernPageHeader
        title={member.fullName}
        subtitle={isPrincipal ? 'منتفع رئيسي' : 'منتفع تابع'}
        icon={isPrincipal ? <BadgeIcon /> : <FamilyRestroomIcon />}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المنتفعين', href: '/members' }, { label: member.fullName }]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/members')}>
              رجوع
            </Button>
            <Button variant="outlined" color="primary" startIcon={<EditIcon />} onClick={() => navigate(`/members/${id}/edit`)}>
              تعديل
            </Button>
            <Button variant="outlined" color="error" startIcon={<DeleteIcon />} onClick={() => handleDeleteConfirm(member)}>
              حذف
            </Button>
          </Stack>
        }
      />

      <MainCard
        content={false}
        sx={{
          height: 'calc(100vh - 180px)',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'grey.50' }}>
          <Tabs
            value={tabValue}
            onChange={handleTabChange}
            aria-label="member tabs"
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              minHeight: '3.0rem',
              '& .MuiTab-root': {
                minHeight: '3.0rem',
                fontSize: theme.typography.body2.fontSize,
                fontWeight: 500,
                color: 'text.secondary',
                transition: 'all 0.2s',
                px: '1.5rem',
                '&.Mui-selected': {
                  color: 'primary.main',
                  bgcolor: 'primary.lighter',
                  fontWeight: 600
                }
              },
              '& .MuiTabs-indicator': {
                height: '0.1875rem',
                borderRadius: '3px 3px 0 0'
              }
            }}
          >
            <Tab label="بيانات المستفيد" icon={<PersonIcon />} iconPosition="start" />
            {isPrincipal && <Tab label={`التابعون (${dependents.length})`} icon={<FamilyRestroomIcon />} iconPosition="start" />}
          </Tabs>
        </Box>

        {/* Scrollable Content Area */}
        <Box sx={{ flex: 1, overflowY: 'auto', p: '1.5rem' }}>
          {/* Tab 0: Personal Info */}
          <div role="tabpanel" hidden={tabValue !== 0}>
            {tabValue === 0 && (
              <Grid container spacing={2}>
                {/* Side: Photo & IDs (Stretches across both rows) */}
                <Grid size={{ xs: 12, md: 3 }} sx={{ display: 'flex' }}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: '0.75rem',
                      flex: 1,
                      bgcolor: 'grey.50',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}
                  >
                    <MemberAvatar member={member} size={110} sx={{ mb: '0.75rem' }} />

                    <Stack spacing={1.5} alignItems="center" width="100%">
                      <Stack direction="row" spacing={1.5} justifyContent="center" width="100%">
                        <Chip
                          label={isPrincipal ? 'رئيسي' : 'تابع'}
                          color={isPrincipal ? 'primary' : 'secondary'}
                          size="small"
                          sx={{ height: '1.5rem', fontSize: '0.75rem' }}
                        />
                        <Chip
                          label={{ ACTIVE: 'نشط', TERMINATED: 'غير نشط', SUSPENDED: 'معلق', PENDING: 'قيد المراجعة' }[member.status] || member.status}
                          color={{ ACTIVE: 'success', TERMINATED: 'error', SUSPENDED: 'warning', PENDING: 'warning' }[member.status] || 'default'}
                          size="small"
                          sx={{ height: '1.5rem', fontSize: '0.75rem' }}
                        />
                      </Stack>

                      <Divider flexItem sx={{ width: '100%', my: 0.5 }} />

                      <Box
                        sx={{
                          width: '100%',
                          textAlign: 'center',
                          p: 1,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                          bgcolor: 'background.paper'
                        }}
                      >
                        <Typography variant="caption" color="text.secondary" display="block" fontWeight="600">
                          رقم البطاقة
                        </Typography>
                        <Typography variant="subtitle2" fontFamily="monospace" fontWeight="bold" sx={{ mt: 0.5 }}>
                          {member.cardNumber || '-'}
                        </Typography>
                      </Box>

                      {isPrincipal && member.barcode && (
                        <Box
                          sx={{
                            width: '100%',
                            textAlign: 'center',
                            p: 1,
                            bgcolor: 'primary.lighter',
                            border: '1px solid',
                            borderColor: 'primary.light',
                            borderRadius: 1
                          }}
                        >
                          <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5} sx={{ mb: 0.5 }}>
                            <QrCodeIcon color="primary" sx={{ fontSize: '1.125rem' }} />
                            <Typography variant="caption" color="primary.main" fontWeight="600">
                              Barcode
                            </Typography>
                          </Stack>
                          <Typography variant="subtitle2" color="primary.main" fontWeight="bold" fontFamily="monospace">
                            {member.barcode}
                          </Typography>
                        </Box>
                      )}
                    </Stack>
                  </Paper>
                </Grid>

                {/* Content: Personal Info (Row 1) + Secondary Info (Row 2) */}
                <Grid size={{ xs: 12, md: 9 }}>
                  <Stack spacing={2}>
                    {/* Personal Info Card */}
                    <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                      <Typography variant="subtitle2" color="primary" fontWeight="bold" gutterBottom>
                        البيانات الشخصية
                      </Typography>
                      <Grid container spacing={2}>
                        <Grid size={{ xs: 12, md: 4 }}>
                          <Typography variant="caption" color="text.secondary">
                            الاسم الكامل
                          </Typography>
                          <Typography variant="h6" fontWeight="bold" sx={{ lineHeight: 1.2 }}>
                            {member.fullName}
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 6, md: 3 }}>
                          <Typography variant="caption" color="text.secondary">
                            الرقم الوطني
                          </Typography>
                          <Typography variant="body2" fontFamily="monospace">
                            {member.nationalNumber || '-'}
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 6, md: 2 }}>
                          <Typography variant="caption" color="text.secondary">
                            الجنسية
                          </Typography>
                          <Typography variant="body2">{member.nationality || '-'}</Typography>
                        </Grid>
                        <Grid size={{ xs: 6, md: 3 }}>
                          <Typography variant="caption" color="text.secondary">
                            تاريخ الميلاد
                          </Typography>
                          <Typography variant="body2">{member.birthDate || '-'}</Typography>
                        </Grid>
                        <Grid size={{ xs: 6, md: 2 }}>
                          <Typography variant="caption" color="text.secondary">
                            الجنس
                          </Typography>
                          <Typography variant="body2">
                            {member.gender === GENDERS.MALE ? 'ذكر' : member.gender === GENDERS.FEMALE ? 'أنثى' : '-'}
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 12, md: 10 }}>
                          {member.notes && (
                            <Typography
                              variant="caption"
                              sx={{ display: 'block', bgcolor: 'warning.lighter', color: 'warning.dark', p: 0.5, borderRadius: 0.5 }}
                            >
                              ملاحظات: {member.notes}
                            </Typography>
                          )}
                        </Grid>
                      </Grid>
                    </Paper>

                    {/* Employment & Contact Container */}
                    <Grid container spacing={2}>
                      {isPrincipal && (
                        <Grid size={{ xs: 12, md: 6 }} sx={{ display: 'flex' }}>
                          <Paper variant="outlined" sx={{ p: '1.0rem', flex: 1 }}>
                            <Stack direction="row" spacing={1} sx={{ mb: '0.75rem' }}>
                              <BadgeIcon fontSize="small" color="action" />
                              <Typography variant="subtitle2" fontWeight="bold">
                                بيانات العمل
                              </Typography>
                            </Stack>
                            <Stack spacing={1.5}>
                              <Box>
                                <Typography variant="caption" color="text.secondary">
                                  جهة العمل
                                </Typography>
                                <Typography variant="body2" fontWeight="medium">
                                  {member.employerName || '-'}
                                </Typography>
                              </Box>
                              <Grid container>
                                <Grid size={6}>
                                  <Typography variant="caption" color="text.secondary">
                                    الرقم الوظيفي
                                  </Typography>
                                  <Typography variant="body2" fontFamily="monospace">
                                    {member.employeeNumber || '-'}
                                  </Typography>
                                </Grid>
                                <Grid size={6}>
                                  <Typography variant="caption" color="text.secondary">
                                    المهنة
                                  </Typography>
                                  <Typography variant="body2">{member.occupation || '-'}</Typography>
                                </Grid>
                              </Grid>
                            </Stack>
                          </Paper>
                        </Grid>
                      )}

                      <Grid size={{ xs: 12, md: isPrincipal ? 6 : 12 }} sx={{ display: 'flex' }}>
                        <Paper variant="outlined" sx={{ p: '1.0rem', flex: 1 }}>
                          <Stack direction="row" spacing={1} sx={{ mb: '0.75rem' }}>
                            <ContactPhoneIcon fontSize="small" color="action" />
                            <Typography variant="subtitle2" fontWeight="bold">
                              معلومات الاتصال
                            </Typography>
                          </Stack>
                          <Stack spacing={2}>
                            <Grid container>
                              <Grid size={6}>
                                <Typography variant="caption" color="text.secondary">
                                  رقم الهاتف
                                </Typography>
                                <Typography variant="body2" dir="ltr">
                                  {member.phone || '-'}
                                </Typography>
                              </Grid>
                              <Grid size={6}>
                                <Typography variant="caption" color="text.secondary">
                                  البريد الإلكتروني
                                </Typography>
                                <Typography variant="caption" display="block" sx={{ wordBreak: 'break-all' }}>
                                  {member.email || '-'}
                                </Typography>
                              </Grid>
                            </Grid>
                            <Box>
                              <Typography variant="caption" color="text.secondary">
                                العنوان
                              </Typography>
                              <Typography variant="body2">{member.address || '-'}</Typography>
                            </Box>
                          </Stack>
                        </Paper>
                      </Grid>
                    </Grid>
                  </Stack>
                </Grid>
              </Grid>
            )}
          </div>

          {/* Tab 1: Dependents (Principal Only) */}
          <div role="tabpanel" hidden={tabValue !== 1}>
            {tabValue === 1 && isPrincipal && (
              <Stack spacing={3}>
                {/* Header Actions */}
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: '1.0rem' }}>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Typography variant="subtitle1" fontWeight="bold">
                      التابعون المسجلون
                    </Typography>
                    <FormControlLabel
                      control={
                        <Switch checked={showDeleted} onChange={(e) => setShowDeleted(e.target.checked)} color="warning" size="small" />
                      }
                      label={
                        <Typography variant="body2" color={showDeleted ? 'warning.main' : 'text.secondary'}>
                          عرض المحذوفات
                        </Typography>
                      }
                    />
                  </Stack>
                  <Button
                    variant="contained"
                    startIcon={<AddIcon />}
                    onClick={handleAddClick}
                    disabled={showDeleted} // Disable add in deleted view
                  >
                    إضافة تابع
                  </Button>
                </Stack>

                <Divider />

                {/* Dependents List */}
                <Box>
                  {dependents.length === 0 ? (
                    <Typography variant="body2" align="center" color="text.secondary" sx={{ py: '1.5rem' }}>
                      لا يوجد تابعين مسجلين حالياً.
                    </Typography>
                  ) : (
                    <>
                      <TableContainer component={Paper} elevation={0} variant="outlined" sx={{ minHeight: '14.375rem' }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell align="center">#</TableCell>
                              <TableCell align="center">الصورة</TableCell>
                              <TableCell align="center">الاسم</TableCell>
                              <TableCell align="center">القرابة</TableCell>
                              <TableCell align="center">رقم البطاقة</TableCell>
                              <TableCell align="center">الرقم الوطني</TableCell>
                              <TableCell align="center">الجنس</TableCell>
                              <TableCell align="center">تاريخ الميلاد</TableCell>
                              <TableCell align="center">الحالة</TableCell>
                              <TableCell align="center">إجراءات</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dependents
                              // TODO: Improve filter logic if backend provides 'deleted' flag.
                              // For now, assuming deleted members are not returned by default OR we filter by status if soft deleted manually.
                              // If 'restore' feature is needed, we must ensure deleted members are FETCHED.
                              // Assuming for now that we filter based on a hypothetical 'deleted' property or specific status if available.
                              .filter((dep) =>
                                showDeleted ? dep.active === false || dep.status === 'TERMINATED' : dep.status !== 'TERMINATED'
                              )
                              .slice(pg * rpp, pg * rpp + rpp)
                              .map((dep, index) => (
                                <TableRow key={dep.id} hover>
                                  <TableCell align="center">{pg * rpp + index + 1}</TableCell>
                                  <TableCell align="center">
                                    <MemberAvatar member={dep} size={32} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <Typography variant="body2" fontWeight="medium">
                                      {dep.fullName}
                                    </Typography>
                                  </TableCell>
                                  <TableCell align="center">
                                    <Chip
                                      label={RELATIONSHIP_AR[dep.relationship] || dep.relationship}
                                      size="small"
                                      variant="outlined"
                                      color="primary"
                                    />
                                  </TableCell>
                                  <TableCell align="center">{dep.cardNumber || '-'}</TableCell>
                                  <TableCell align="center">{dep.nationalNumber || dep.civilId || '-'}</TableCell>
                                  <TableCell align="center">
                                    {dep.gender === GENDERS.MALE ? 'ذكر' : dep.gender === GENDERS.FEMALE ? 'أنثى' : '-'}
                                  </TableCell>
                                  <TableCell align="center">{dep.birthDate || '-'}</TableCell>
                                  <TableCell align="center">
                                    <Chip
                                      label={{ ACTIVE: 'نشط', TERMINATED: 'غير نشط', SUSPENDED: 'معلق', PENDING: 'قيد المراجعة' }[dep.status] || dep.status}
                                      color={{ ACTIVE: 'success', TERMINATED: 'error', SUSPENDED: 'warning', PENDING: 'warning' }[dep.status] || 'default'}
                                      size="small"
                                      sx={{ height: '1.5rem' }}
                                    />
                                  </TableCell>
                                  <TableCell align="center">
                                    <Stack direction="row" spacing={1} justifyContent="center">
                                      {showDeleted ? (
                                        <>
                                          <Tooltip title="استعادة">
                                            <IconButton size="small" color="success" onClick={() => handleRestore(dep.id)}>
                                              <RestoreFromTrashIcon fontSize="small" />
                                            </IconButton>
                                          </Tooltip>
                                          <Tooltip title="حذف نهائي">
                                            <IconButton size="small" color="error" onClick={() => handleHardDeleteDepConfirm(dep)}>
                                              <DeleteIcon fontSize="small" />
                                            </IconButton>
                                          </Tooltip>
                                        </>
                                      ) : (
                                        <>
                                          <Tooltip title="تعديل">
                                            <IconButton size="small" color="secondary" onClick={() => handleEditClick(dep)}>
                                              <EditIcon fontSize="small" />
                                            </IconButton>
                                          </Tooltip>
                                          <Tooltip title="حذف">
                                            <IconButton size="small" color="error" onClick={() => handleDeleteConfirm(dep)}>
                                              <DeleteIcon fontSize="small" />
                                            </IconButton>
                                          </Tooltip>
                                        </>
                                      )}
                                    </Stack>
                                  </TableCell>
                                </TableRow>
                              ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                      <TablePagination
                        rowsPerPageOptions={[3, 6, 9]}
                        component="div"
                        count={dependents.length}
                        rowsPerPage={rpp}
                        page={pg}
                        onPageChange={handleChangePage}
                        onRowsPerPageChange={handleChangeRowsPerPage}
                        labelRowsPerPage="صفوف لكل صفحة:"
                        labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`}
                      />
                    </>
                  )}
                </Box>
              </Stack>
            )}
          </div>
        </Box>
      </MainCard>

      <DependentModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        principalId={member?.id}
        dependent={selectedDependent}
        existingDependents={dependents}
        principalGender={member?.gender}
        onSave={handleModalSave}
      />

      {/* Hard Delete Dependent Confirmation Dialog */}
      <Dialog open={hardDeleteDepDialogOpen} onClose={() => setHardDeleteDepDialogOpen(false)}>
        <DialogTitle sx={{ fontWeight: 600 }}>حذف نهائي؟</DialogTitle>
        <DialogContent>
          <DialogContentText>
            سيتم حذف التابع <strong>{hardDeletingDep?.fullName}</strong> نهائياً من قاعدة البيانات. هذا الإجراء لا يمكن التراجع عنه!
            <Alert severity="error" sx={{ mt: '1.0rem' }}>
              <strong>تنبيه:</strong> إذا كان للتابع مطالبات أو زيارات مرتبطة سيفشل الحذف.
            </Alert>
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setHardDeleteDepDialogOpen(false)}>إلغاء</Button>
          <Button onClick={handleHardDeleteDepExecute} color="error" variant="contained" autoFocus>
            تأكيد الحذف النهائي
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle sx={{ fontWeight: 600 }}>تأكيد الحذف</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {deletingMember?.type === MEMBER_TYPES.PRINCIPAL ? (
              <>
                هل أنت متأكد من حذف المنتفع الرئيسي <strong>{deletingMember?.fullName}</strong>؟
                <Alert severity="warning" sx={{ mt: '1.0rem' }}>
                  <strong>تنبيه:</strong> سيتم حذف جميع التابعين ({member.dependentsCount || 0}) تلقائياً (CASCADE DELETE).
                </Alert>
              </>
            ) : (
              <>
                هل أنت متأكد من حذف التابع <strong>{deletingMember?.fullName}</strong>؟
              </>
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>إلغاء</Button>
          <Button onClick={handleDeleteExecute} color="error" variant="contained" autoFocus>
            تأكيد الحذف
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default UnifiedMemberView;


