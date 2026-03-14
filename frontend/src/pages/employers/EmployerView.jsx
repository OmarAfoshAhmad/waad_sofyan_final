import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Typography,
  Alert,
  Card,
  CardContent,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  Edit as EditIcon,
  Business as BusinessIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { getEmployerById } from 'services/api/employers.service';
import { refreshBenefitPoliciesForEmployer } from 'services/api/members.service';
import { openSnackbar } from 'api/snackbar';

// Static Arabic labels
const LABELS = {
  list: 'الشركاء',
  view: 'عرض شريك',
  viewSubtitle: 'عرض معلومات الشريك',
  back: 'رجوع',
  backToList: 'رجوع إلى القائمة',
  edit: 'تعديل',
  employerCode: 'الرمز',
  code: 'الرمز',
  name: 'اسم الشريك',
  status: 'الحالة',
  active: 'نشط',
  inactive: 'غير نشط',
  basicInfo: 'المعلومات الأساسية',
  additionalInfo: 'معلومات إضافية',
  createdAt: 'تاريخ الإنشاء',
  updatedAt: 'تاريخ التحديث',
  statistics: 'الإحصائيات',
  totalMembers: 'إجمالي المنتفعين',
  activePolicies: 'الوثائق النشطة',
  totalClaims: 'إجمالي المطالبات',
  loadError: 'فشل في تحميل الشريك',
  refreshPolicies: 'تعيين وثيقة المنافع للمنتفعين',
  refreshPoliciesTitle: 'تعيين وثيقة المنافع',
  refreshPoliciesConfirm: 'هل أنت متأكد من تعيين وثيقة المنافع النشطة لجميع منتفعي هذا الشريك؟',
  refreshPoliciesSuccess: 'تم تحديث وثيقة المنافع لـ {count} منتفع',
  refreshPoliciesError: 'فشل في تعيين وثيقة المنافع',
  confirm: 'تأكيد',
  cancel: 'إلغاء'
};

/**
 * Employer View Page (Read-Only)
 * Displays detailed information about an employer
 */
const EmployerView = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [employer, setEmployer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshDialogOpen, setRefreshDialogOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadEmployer();
  }, [id]);

  const loadEmployer = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getEmployerById(id);
      setEmployer(data);
    } catch (err) {
      console.error('[EmployerView] Failed to load employer:', err);
      setError(err.response?.data?.message || LABELS.loadError);
    } finally {
      setLoading(false);
    }
  };

  const handleRefreshPolicies = async () => {
    try {
      setRefreshing(true);
      // Use the employer's organization ID (organizationId) for the refresh
      const orgId = employer?.organizationId || employer?.id;
      const count = await refreshBenefitPoliciesForEmployer(orgId);
      openSnackbar({
        message: LABELS.refreshPoliciesSuccess.replace('{count}', count),
        variant: 'success'
      });
      setRefreshDialogOpen(false);
    } catch (err) {
      console.error('[EmployerView] Failed to refresh policies:', err);
      openSnackbar({
        message: err.response?.data?.message || LABELS.refreshPoliciesError,
        variant: 'error'
      });
    } finally {
      setRefreshing(false);
    }
  };

  if (loading) {
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
          title={LABELS.view}
          subtitle={LABELS.viewSubtitle}
          icon={BusinessIcon}
          breadcrumbs={[
            { label: LABELS.list, path: '/employers' },
            { label: LABELS.view, path: `/employers/${id}` }
          ]}
        />
        <MainCard>
          <Alert severity="error">{error}</Alert>
          <Box sx={{ mt: '1.0rem' }}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/employers')}>
              {LABELS.backToList}
            </Button>
          </Box>
        </MainCard>
      </>
    );
  }

  if (!employer) {
    return null;
  }

  const InfoRow = ({ label, value, fullWidth = false }) => (
    <Grid container spacing={2} sx={{ py: '0.75rem' }}>
      <Grid size={{ xs: 12, sm: fullWidth ? 12 : 4 }}>
        <Typography variant="body2" color="text.secondary" fontWeight={600}>
          {label}
        </Typography>
      </Grid>
      <Grid size={{ xs: 12, sm: fullWidth ? 12 : 8 }}>
        <Typography variant="body2">{value || '-'}</Typography>
      </Grid>
    </Grid>
  );

  const SectionCard = ({ title, children }) => (
    <Card sx={{ mb: '1.5rem' }}>
      <CardContent>
        <Typography variant="h5" sx={{ mb: '1.0rem', fontWeight: 600 }}>
          {title}
        </Typography>
        <Divider sx={{ mb: '1.0rem' }} />
        {children}
      </CardContent>
    </Card>
  );

  return (
    <>
      <ModernPageHeader
        title={employer.name || LABELS.view}
        subtitle={`${LABELS.code}: ${employer.code || 'N/A'}`}
        icon={BusinessIcon}
        breadcrumbs={[
          { label: LABELS.list, path: '/employers' },
          { label: LABELS.view, path: `/employers/${id}` }
        ]}
        actions={
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/employers')}>
              {LABELS.back}
            </Button>
            
              <Button variant="contained" startIcon={<EditIcon />} onClick={() => navigate(`/employers/edit/${id}`)}>
                {LABELS.edit}
              </Button>
              
          </Stack>
        }
      />

      <MainCard>
        {/* Status Badge */}
        <Box sx={{ mb: '1.5rem' }}>
          <Chip
            icon={employer.active ? <CheckCircleIcon /> : <CancelIcon />}
            label={employer.active ? LABELS.active : LABELS.inactive}
            color={employer.active ? 'success' : 'default'}
            size="medium"
          />
        </Box>

        {/* Basic Information Section */}
        <SectionCard title={LABELS.basicInfo}>
          <InfoRow label={LABELS.code} value={employer.code} />
          <InfoRow label={LABELS.name} value={employer.name} />
          <InfoRow
            label={LABELS.status}
            value={
              <Chip
                icon={employer.active ? <CheckCircleIcon /> : <CancelIcon />}
                label={employer.active ? LABELS.active : LABELS.inactive}
                color={employer.active ? 'success' : 'default'}
                size="small"
              />
            }
          />
        </SectionCard>

        {/* Additional Information Section - Timestamps Only */}
        {(employer.createdAt || employer.updatedAt) && (
          <SectionCard title={LABELS.additionalInfo}>
            {employer.createdAt && <InfoRow label={LABELS.createdAt} value={new Date(employer.createdAt).toLocaleString()} />}
            {employer.updatedAt && <InfoRow label={LABELS.updatedAt} value={new Date(employer.updatedAt).toLocaleString()} />}
          </SectionCard>
        )}

        {/* Statistics Section (if available) */}
        {(employer.totalMembers !== undefined || employer.activePolicies !== undefined) && (
          <SectionCard title={LABELS.statistics}>
            <Grid container spacing={3}>
              {employer.totalMembers !== undefined && (
                <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                  <Card variant="outlined">
                    <CardContent sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="primary.main">
                        {employer.totalMembers}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {LABELS.totalMembers}
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              )}
              {employer.activePolicies !== undefined && (
                <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                  <Card variant="outlined">
                    <CardContent sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="success.main">
                        {employer.activePolicies}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {LABELS.activePolicies}
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              )}
              {employer.totalClaims !== undefined && (
                <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                  <Card variant="outlined">
                    <CardContent sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="info.main">
                        {employer.totalClaims}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {LABELS.totalClaims}
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              )}
            </Grid>
          </SectionCard>
        )}

        {/* Action Buttons at Bottom */}
        <Divider sx={{ my: '1.5rem' }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end">
          {/* Bulk Refresh Benefit Policies Button */}
          
            <Button variant="outlined" color="secondary" startIcon={<RefreshIcon />} onClick={() => setRefreshDialogOpen(true)}>
              {LABELS.refreshPolicies}
            </Button>
            
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/employers')}>
            {LABELS.back}
          </Button>
          <Button variant="contained" startIcon={<EditIcon />} onClick={() => navigate(`/employers/edit/${id}`)}>
            {LABELS.edit}
          </Button>
        </Stack>
      </MainCard>

      {/* Refresh Policies Confirmation Dialog */}
      <Dialog open={refreshDialogOpen} onClose={() => !refreshing && setRefreshDialogOpen(false)}>
        <DialogTitle>{LABELS.refreshPoliciesTitle}</DialogTitle>
        <DialogContent>
          <DialogContentText>{LABELS.refreshPoliciesConfirm}</DialogContentText>
          <Alert severity="info" sx={{ mt: '1.0rem' }}>
            سيتم تعيين الوثيقة النشطة الحالية لجميع أعضاء الشريك هذا.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRefreshDialogOpen(false)} disabled={refreshing}>
            {LABELS.cancel}
          </Button>
          <Button
            onClick={handleRefreshPolicies}
            variant="contained"
            color="primary"
            disabled={refreshing}
            startIcon={refreshing ? <CircularProgress size={16} /> : <RefreshIcon />}
          >
            {LABELS.confirm}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default EmployerView;



