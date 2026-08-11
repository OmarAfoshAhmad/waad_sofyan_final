/**
 * Unified Member View Page
 *
 * Displays Principal member with expandable Dependents list.
 * Orchestrates three tabs (personal info / dependents / medical history)
 * and the page-level dialogs; each tab's own layout and the medical-history
 * fetch/filter logic live in ./view -- this file owns only the state that's
 * genuinely shared across tabs (member/dependents data, the status-change
 * menu, and the four dialogs).
 *
 * @module UnifiedMemberView
 * @since 2026-01-11
 */

import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, MenuItem, Menu, Stack, Tab, Tabs, useTheme } from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  Badge as BadgeIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  FamilyRestroom as FamilyRestroomIcon,
  History as HistoryIcon,
  Person as PersonIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import DependentModal from './DependentModal';
import {
  getMember,
  deleteMember,
  hardDeleteMember,
  restoreMember,
  changeMemberStatus,
  MEMBER_TYPES
} from 'services/api/unified-members.service';
import { openSnackbar } from 'api/snackbar';

import MemberPersonalInfoTab from './view/MemberPersonalInfoTab';
import MemberDependentsTab from './view/MemberDependentsTab';
import MemberMedicalHistoryTab from './view/MemberMedicalHistoryTab';
import MemberViewDialogs from './view/MemberViewDialogs';
import { useMemberMedicalHistory } from './view/useMemberMedicalHistory';
import { MEMBER_STATUS_OPTIONS } from './view/memberView.helpers';

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

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedDependent, setSelectedDependent] = useState(null); // null = Add Mode
  const [showDeleted, setShowDeleted] = useState(false);
  const medicalTabIndex = member?.type === MEMBER_TYPES.PRINCIPAL ? 2 : 1;
  const medicalHistory = useMemberMedicalHistory(member?.id, tabValue === medicalTabIndex);

  // Dependents table pagination
  const [pg, setPg] = useState(0);
  const [rpp, setRpp] = useState(6);

  // Dialog States
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingMember, setDeletingMember] = useState(null);
  const [hardDeleteDepDialogOpen, setHardDeleteDepDialogOpen] = useState(false);
  const [hardDeletingDep, setHardDeletingDep] = useState(null);
  const [photoDialogOpen, setPhotoDialogOpen] = useState(false);
  const [statusMenuAnchor, setStatusMenuAnchor] = useState(null);
  const [statusMenuTargetId, setStatusMenuTargetId] = useState(null);
  const [statusChangeDialog, setStatusChangeDialog] = useState({ open: false, targetId: null, targetStatus: null, reason: '' });
  const [statusChangeLoading, setStatusChangeLoading] = useState(false);

  const applyStatusChange = async (targetId, targetStatus, reason) => {
    setStatusChangeLoading(true);
    try {
      await changeMemberStatus(targetId, targetStatus, reason);
      openSnackbar({ open: true, message: 'تم تحديث حالة المستفيد بنجاح', variant: 'alert', alert: { color: 'success' } });
      setStatusChangeDialog({ open: false, targetId: null, targetStatus: null, reason: '' });
      fetchMemberData();
    } catch (err) {
      openSnackbar({
        open: true,
        message: err?.response?.data?.message || 'تعذر تحديث حالة المستفيد',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setStatusChangeLoading(false);
    }
  };

  const handleOpenStatusMenu = (event, targetId) => {
    setStatusMenuAnchor(event.currentTarget);
    setStatusMenuTargetId(targetId);
  };

  const handleSelectStatus = (targetStatus) => {
    const targetId = statusMenuTargetId;
    setStatusMenuAnchor(null);
    setStatusMenuTargetId(null);
    if (targetStatus === 'SUSPENDED') {
      setStatusChangeDialog({ open: true, targetId, targetStatus, reason: '' });
      return;
    }
    applyStatusChange(targetId, targetStatus, null);
  };

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const fetchMemberData = async () => {
    setLoading(true);
    try {
      const response = await getMember(id);
      setMember(response);
      setDependents(response.dependents || []);
    } catch (error) {
      console.error('Error fetching member:', error);
      openSnackbar({ open: true, message: 'خطأ في جلب بيانات المنتفع', variant: 'alert', alert: { color: 'error' } });
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

  const handleRestore = async (depId) => {
    try {
      await restoreMember(depId);
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

      const isPrincipalDeleted = deletingMember.type === MEMBER_TYPES.PRINCIPAL;

      openSnackbar({
        open: true,
        message: isPrincipalDeleted ? 'تم حذف الموظف وجميع تابعيه بنجاح' : 'تم حذف المنتفع التابع بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      if (isPrincipalDeleted) {
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
  // Status menu is shared by both the principal header chip and every dependent
  // row's chip, so it's anchored/opened via event.currentTarget regardless of
  // which tab triggered it -- it must render unconditionally at this level
  // (not nested inside a tab's conditional block) or opening it from a
  // non-personal-info tab would silently do nothing.
  const statusMenuCurrentStatus =
    statusMenuTargetId === member.id ? member.status : dependents.find((d) => d.id === statusMenuTargetId)?.status;

  return (
    <>
      <ModernPageHeader
        title={member.fullName}
        subtitle={isPrincipal ? 'موظف' : 'منتفع تابع'}
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

      <MainCard content={false} sx={{ height: 'calc(100vh - 180px)', display: 'flex', flexDirection: 'column' }}>
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
                '&.Mui-selected': { color: 'primary.main', bgcolor: 'primary.lighter', fontWeight: 600 }
              },
              '& .MuiTabs-indicator': { height: '0.1875rem', borderRadius: '3px 3px 0 0' }
            }}
          >
            <Tab label="بيانات المستفيد" icon={<PersonIcon />} iconPosition="start" />
            {isPrincipal && <Tab label={`التابعون (${dependents.length})`} icon={<FamilyRestroomIcon />} iconPosition="start" />}
            <Tab label="السجل الطبي" icon={<HistoryIcon />} iconPosition="start" />
          </Tabs>
        </Box>

        <Box sx={{ flex: 1, overflowY: 'auto', p: '1.5rem' }}>
          <div role="tabpanel" hidden={tabValue !== 0}>
            {tabValue === 0 && (
              <MemberPersonalInfoTab
                member={member}
                isPrincipal={isPrincipal}
                onOpenPhoto={() => setPhotoDialogOpen(true)}
                onOpenStatusMenu={handleOpenStatusMenu}
              />
            )}
          </div>

          <div role="tabpanel" hidden={!isPrincipal || tabValue !== 1}>
            {tabValue === 1 && isPrincipal && (
              <MemberDependentsTab
                dependents={dependents}
                showDeleted={showDeleted}
                onToggleShowDeleted={setShowDeleted}
                onAddClick={handleAddClick}
                onEditClick={handleEditClick}
                onDeleteConfirm={handleDeleteConfirm}
                onRestore={handleRestore}
                onHardDeleteConfirm={handleHardDeleteDepConfirm}
                onOpenStatusMenu={handleOpenStatusMenu}
                pg={pg}
                rpp={rpp}
                onChangePage={handleChangePage}
                onChangeRowsPerPage={handleChangeRowsPerPage}
              />
            )}
          </div>

          <div role="tabpanel" hidden={tabValue !== medicalTabIndex}>
            {tabValue === medicalTabIndex && <MemberMedicalHistoryTab history={medicalHistory} onNavigate={navigate} />}
          </div>
        </Box>
      </MainCard>

      <Menu anchorEl={statusMenuAnchor} open={Boolean(statusMenuAnchor)} onClose={() => setStatusMenuAnchor(null)}>
        {MEMBER_STATUS_OPTIONS.filter((opt) => opt.value !== statusMenuCurrentStatus).map((opt) => (
          <MenuItem key={opt.value} onClick={() => handleSelectStatus(opt.value)}>
            {opt.label}
          </MenuItem>
        ))}
      </Menu>

      <DependentModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        principalId={member?.id}
        dependent={selectedDependent}
        existingDependents={dependents}
        principalGender={member?.gender}
        onSave={handleModalSave}
      />

      <MemberViewDialogs
        member={member}
        photoDialogOpen={photoDialogOpen}
        onClosePhoto={() => setPhotoDialogOpen(false)}
        statusChangeDialog={statusChangeDialog}
        onCloseStatusChange={() => setStatusChangeDialog({ open: false, targetId: null, targetStatus: null, reason: '' })}
        onChangeStatusReason={(reason) => setStatusChangeDialog((prev) => ({ ...prev, reason }))}
        onConfirmStatusChange={() =>
          applyStatusChange(statusChangeDialog.targetId, statusChangeDialog.targetStatus, statusChangeDialog.reason)
        }
        statusChangeLoading={statusChangeLoading}
        hardDeleteDepDialogOpen={hardDeleteDepDialogOpen}
        hardDeletingDep={hardDeletingDep}
        onCloseHardDeleteDep={() => setHardDeleteDepDialogOpen(false)}
        onConfirmHardDeleteDep={handleHardDeleteDepExecute}
        deleteDialogOpen={deleteDialogOpen}
        deletingMember={deletingMember}
        onCloseDelete={() => setDeleteDialogOpen(false)}
        onConfirmDelete={handleDeleteExecute}
      />
    </>
  );
};

export default UnifiedMemberView;
