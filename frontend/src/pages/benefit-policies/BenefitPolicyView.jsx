import { useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  Grid,
  Stack,
  Tab,
  Tabs,
  Typography
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  Edit as EditIcon,
  CheckCircle as ActivateIcon,
  PauseCircle as SuspendIcon,
  Cancel as CancelIcon,
  Delete as DeleteIcon,
  Policy as PolicyIcon,
  Business as BusinessIcon,
  CalendarToday as CalendarIcon,
  AttachMoney as MoneyIcon,
  Percent as PercentIcon,
  Code as CodeIcon,
  Info as InfoIcon,
  Rule as RuleIcon
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useSnackbar } from 'notistack';

import {
  getBenefitPolicyById,
  activateBenefitPolicy,
  suspendBenefitPolicy,
  cancelBenefitPolicy,
  deleteBenefitPolicy
} from 'services/api/benefit-policies.service';

import BenefitPolicyRulesTab from './BenefitPolicyRulesTab';

// ═══════════════════════════════════════════════════════════════════════════
// STATUS CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

const STATUS_CONFIG = {
  DRAFT: {
    label: 'مسودة',
    color: 'default',
    labelEn: 'Draft',
    canActivate: true,
    canSuspend: false,
    canCancel: true,
    canDelete: true
  },
  ACTIVE: {
    label: 'نشط',
    color: 'success',
    labelEn: 'Active',
    canActivate: false,
    canSuspend: true,
    canCancel: true,
    canDelete: false
  },
  SUSPENDED: {
    label: 'موقوف',
    color: 'warning',
    labelEn: 'Suspended',
    canActivate: true,
    canSuspend: false,
    canCancel: true,
    canDelete: false
  },
  EXPIRED: {
    label: 'منتهي',
    color: 'error',
    labelEn: 'Expired',
    canActivate: false,
    canSuspend: false,
    canCancel: false,
    canDelete: true
  },
  CANCELLED: {
    label: 'ملغي',
    color: 'error',
    labelEn: 'Cancelled',
    canActivate: false,
    canSuspend: false,
    canCancel: false,
    canDelete: true
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Detail row component for displaying label-value pairs
 */
const DetailRow = ({ label, value, icon: Icon }) => (
  <Grid container spacing={2} sx={{ mb: '1.0rem' }}>
    <Grid size={{ xs: 12, sm: 4 }}>
      <Stack direction="row" spacing={1} alignItems="center">
        {Icon && <Icon fontSize="small" color="action" />}
        <Typography variant="body2" color="text.secondary" fontWeight={500}>
          {label}
        </Typography>
      </Stack>
    </Grid>
    <Grid size={{ xs: 12, sm: 8 }}>
      <Typography variant="body1">{value || 'غير متوفر'}</Typography>
    </Grid>
  </Grid>
);

/**
 * Status chip with proper colors
 */
const StatusChip = ({ status }) => {
  const config = STATUS_CONFIG[status] || { label: status, color: 'default' };
  return <Chip label={config.label} color={config.color} size="medium" sx={{ fontWeight: 600, fontSize: '0.875rem' }} />;
};

/**
 * Confirmation dialog for actions
 */
const ConfirmDialog = ({ open, title, message, onConfirm, onCancel, loading, confirmColor = 'primary' }) => (
  <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
    <DialogTitle>{title}</DialogTitle>
    <DialogContent>
      <DialogContentText>{message}</DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={onCancel} disabled={loading}>
        إلغاء
      </Button>
      <Button
        onClick={onConfirm}
        color={confirmColor}
        variant="contained"
        disabled={loading}
        startIcon={loading && <CircularProgress size={16} color="inherit" />}
      >
        تأكيد
      </Button>
    </DialogActions>
  </Dialog>
);

// ═══════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Benefit Policy View/Management Page
 *
 * Features:
 * - Display policy header details (Odoo-like design)
 * - Tabs: Overview | Rules
 * - Lifecycle actions: Activate, Suspend, Cancel, Delete
 * - RBAC permission checks
 *
 * Route: /benefit-policies/:id
 */
const BenefitPolicyView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // Tab state
  const [activeTab, setActiveTab] = useState(0);

  // Dialog states
  const [dialogState, setDialogState] = useState({
    open: false,
    action: null,
    title: '',
    message: ''
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ═══════════════════════════════════════════════════════════════════════════

  const {
    data: policy,
    isLoading,
    error,
    refetch
  } = useQuery({
    queryKey: ['benefit-policy', id],
    queryFn: () => getBenefitPolicyById(id),
    enabled: !!id
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // MUTATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  const activateMutation = useMutation({
    mutationFn: () => activateBenefitPolicy(id),
    onSuccess: () => {
      enqueueSnackbar('تم تفعيل الوثيقة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries(['benefit-policy', id]);
      refetch();
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل تفعيل الوثيقة', { variant: 'error' });
    }
  });

  const suspendMutation = useMutation({
    mutationFn: () => suspendBenefitPolicy(id),
    onSuccess: () => {
      enqueueSnackbar('تم إيقاف الوثيقة مؤقتاً', { variant: 'warning' });
      queryClient.invalidateQueries(['benefit-policy', id]);
      refetch();
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل إيقاف الوثيقة', { variant: 'error' });
    }
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelBenefitPolicy(id),
    onSuccess: () => {
      enqueueSnackbar('تم إلغاء الوثيقة', { variant: 'info' });
      queryClient.invalidateQueries(['benefit-policy', id]);
      refetch();
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل إلغاء الوثيقة', { variant: 'error' });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteBenefitPolicy(id),
    onSuccess: () => {
      enqueueSnackbar('تم حذف الوثيقة', { variant: 'success' });
      navigate('/benefit-policies');
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل حذف الوثيقة', { variant: 'error' });
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // ACTION HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════

  const openConfirmDialog = useCallback((action, title, message) => {
    setDialogState({ open: true, action, title, message });
  }, []);

  const closeDialog = useCallback(() => {
    setDialogState({ open: false, action: null, title: '', message: '' });
  }, []);

  const handleConfirmAction = useCallback(() => {
    switch (dialogState.action) {
      case 'activate':
        activateMutation.mutate();
        break;
      case 'suspend':
        suspendMutation.mutate();
        break;
      case 'cancel':
        cancelMutation.mutate();
        break;
      case 'delete':
        deleteMutation.mutate();
        break;
      default:
        break;
    }
    closeDialog();
  }, [dialogState.action, activateMutation, suspendMutation, cancelMutation, deleteMutation, closeDialog]);

  const handleActivate = () => {
    openConfirmDialog('activate', 'تفعيل الوثيقة', 'هل أنت متأكد من تفعيل هذه الوثيقة؟ سيتم إلغاء تفعيل أي وثيقة أخرى نشطة لنفس الشريك.');
  };

  const handleSuspend = () => {
    openConfirmDialog(
      'suspend',
      'إيقاف الوثيقة مؤقتاً',
      'هل أنت متأكد من إيقاف هذه الوثيقة مؤقتاً؟ لن يتمكن الأعضاء من تقديم مطالبات جديدة.'
    );
  };

  const handleCancel = () => {
    openConfirmDialog('cancel', 'إلغاء الوثيقة', 'هل أنت متأكد من إلغاء هذه الوثيقة نهائياً؟ هذا الإجراء لا يمكن التراجع عنه.');
  };

  const handleDelete = () => {
    openConfirmDialog('delete', 'حذف الوثيقة', 'هل أنت متأكد من حذف هذه الوثيقة؟ سيتم حذفها بشكل نهائي.');
  };

  // Tab change handler
  const handleTabChange = useCallback((event, newValue) => {
    setActiveTab(newValue);
  }, []);

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER STATES
  // ═══════════════════════════════════════════════════════════════════════════

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <>
        <ModernPageHeader
          title="وثيقة المنافع"
          subtitle="عرض تفاصيل الوثيقة"
          icon={PolicyIcon}
          breadcrumbs={[
            { label: 'وثائق المنافع', path: '/benefit-policies' },
            { label: 'عرض', path: `/benefit-policies/${id}` }
          ]}
        />
        <MainCard>
          <Alert severity="error">{error.response?.data?.message || error.message || 'فشل تحميل بيانات الوثيقة'}</Alert>
          <Box sx={{ mt: '1.0rem' }}>
            <Button variant="outlined" onClick={() => navigate('/benefit-policies')}>
              العودة للقائمة
            </Button>
          </Box>
        </MainCard>
      </>
    );
  }

  const statusConfig = STATUS_CONFIG[policy?.status] || STATUS_CONFIG.DRAFT;
  const isLoading_Action = activateMutation.isPending || suspendMutation.isPending || cancelMutation.isPending || deleteMutation.isPending;

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════════════════════════════════════════

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title={policy?.name || 'وثيقة المنافع'}
        subtitle={`رقم الوثيقة: ${policy?.policyCode || 'N/A'}`}
        icon={PolicyIcon}
        breadcrumbs={[
          { label: 'وثائق المنافع', path: '/benefit-policies' },
          { label: policy?.name || 'عرض', path: `/benefit-policies/${id}` }
        ]}
        actions={
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {/* Back Button */}
            <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/benefit-policies')} variant="outlined" size="small">
              رجوع
            </Button>

            {/* Edit Button */}

            <Button
              startIcon={<EditIcon />}
              onClick={() => navigate(`/benefit-policies/edit/${id}`)}
              variant="outlined"
              color="primary"
              size="small"
              disabled={policy?.status === 'CANCELLED'}
            >
              تعديل
            </Button>


            {/* Activate Button */}
            {statusConfig.canActivate && (

              <Button
                startIcon={<ActivateIcon />}
                onClick={handleActivate}
                variant="contained"
                color="success"
                size="small"
                disabled={isLoading_Action}
              >
                تفعيل
              </Button>

            )}

            {/* Suspend Button */}
            {statusConfig.canSuspend && (

              <Button
                startIcon={<SuspendIcon />}
                onClick={handleSuspend}
                variant="contained"
                color="warning"
                size="small"
                disabled={isLoading_Action}
              >
                إيقاف مؤقت
              </Button>

            )}

            {/* Cancel Button */}
            {statusConfig.canCancel && (

              <Button
                startIcon={<CancelIcon />}
                onClick={handleCancel}
                variant="outlined"
                color="error"
                size="small"
                disabled={isLoading_Action}
              >
                إلغاء
              </Button>

            )}

            {/* Delete Button */}
            {statusConfig.canDelete && (

              <Button
                startIcon={<DeleteIcon />}
                onClick={handleDelete}
                variant="outlined"
                color="error"
                size="small"
                disabled={isLoading_Action}
              >
                حذف
              </Button>

            )}
          </Stack>
        }
      />

      {/* Tabs Navigation */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: '1.5rem' }}>
        <Tabs value={activeTab} onChange={handleTabChange} textColor="primary" indicatorColor="primary">
          <Tab icon={<InfoIcon />} iconPosition="start" label="نظرة عامة" />
          <Tab icon={<RuleIcon />} iconPosition="start" label={`قواعد التغطية (${policy?.rulesCount || 0})`} />
        </Tabs>
      </Box>

      {/* Tab Content */}
      {activeTab === 0 && (
        /* Overview Tab - Policy Header Card - Odoo Style */
        <Grid container spacing={3}>
          {/* Basic Information */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MainCard title="معلومات أساسية" secondary={<StatusChip status={policy?.status} />}>
              <DetailRow label="اسم الوثيقة" value={policy?.name} icon={PolicyIcon} />
              <DetailRow label="رمز الوثيقة" value={policy?.policyCode} icon={CodeIcon} />
              <DetailRow label="الشريك" value={policy?.employerName} icon={BusinessIcon} />
              <Divider sx={{ my: '1.0rem' }} />
              <DetailRow
                label="تاريخ البدء"
                value={policy?.startDate ? new Date(policy.startDate).toLocaleDateString('en-US') : null}
                icon={CalendarIcon}
              />
              <DetailRow
                label="تاريخ الانتهاء"
                value={policy?.endDate ? new Date(policy.endDate).toLocaleDateString('en-US') : null}
                icon={CalendarIcon}
              />
            </MainCard>
          </Grid>

          {/* Coverage Information */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MainCard title="معلومات التغطية">
              <DetailRow
                label="الحد السنوي"
                value={policy?.annualLimit ? `${Number(policy.annualLimit).toLocaleString('en-US')} د.ل` : 'غير محدد'}
                icon={MoneyIcon}
              />
              <DetailRow
                label="نسبة التغطية الافتراضية"
                value={
                  policy?.defaultCoveragePercent !== null && policy?.defaultCoveragePercent !== undefined
                    ? `${policy.defaultCoveragePercent}%`
                    : 'غير محدد'
                }
                icon={PercentIcon}
              />
              <Divider sx={{ my: '1.0rem' }} />
              <DetailRow label="عدد القواعد" value={policy?.rulesCount || 0} />
              <DetailRow label="عدد الأعضاء المرتبطين" value={policy?.membersCount || 0} />
            </MainCard>
          </Grid>

          {/* Description / Notes */}
          {policy?.description && (
            <Grid size={12}>
              <MainCard title="الوصف">
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                  {policy.description}
                </Typography>
              </MainCard>
            </Grid>
          )}

          {/* Audit Information */}
          <Grid size={12}>
            <MainCard title="معلومات النظام">
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <DetailRow
                    label="تاريخ الإنشاء"
                    value={policy?.createdAt ? new Date(policy.createdAt).toLocaleString('en-US') : null}
                    icon={CalendarIcon}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <DetailRow
                    label="آخر تحديث"
                    value={policy?.updatedAt ? new Date(policy.updatedAt).toLocaleString('en-US') : null}
                    icon={CalendarIcon}
                  />
                </Grid>
              </Grid>
            </MainCard>
          </Grid>
        </Grid>
      )}

      {/* Rules Tab */}
      {activeTab === 1 && <BenefitPolicyRulesTab policyId={id} policyStatus={policy?.status} policyDefaultCoveragePercent={policy?.defaultCoveragePercent} />}

      {/* Confirmation Dialog */}
      <ConfirmDialog
        open={dialogState.open}
        title={dialogState.title}
        message={dialogState.message}
        onConfirm={handleConfirmAction}
        onCancel={closeDialog}
        loading={isLoading_Action}
        confirmColor={dialogState.action === 'delete' || dialogState.action === 'cancel' ? 'error' : 'primary'}
      />
    </>
  );
};

export default BenefitPolicyView;

