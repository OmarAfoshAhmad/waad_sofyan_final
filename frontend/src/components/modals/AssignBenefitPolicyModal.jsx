/**
 * Assign Benefit Policy Modal
 *
 * Modal for assigning a benefit policy to all members of a selected employer.
 * Performs bulk update operation.
 *
 * Features:
 * - Select employer
 * - Select benefit policy
 * - Preview number of members affected
 * - Bulk assign with confirmation
 *
 * Usage:
 * <AssignBenefitPolicyModal
 *   open={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   onSuccess={() => refreshData()}
 * />
 */

import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Alert,
  IconButton,
  Divider,
  Stack,
  Autocomplete,
  TextField,
  CircularProgress,
  Chip
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AssignmentIcon from '@mui/icons-material/Assignment';
import PeopleIcon from '@mui/icons-material/People';
import DescriptionIcon from '@mui/icons-material/Description';

// Services
import { getEmployerSelectors } from 'services/api/employers.service';
import { getBenefitPolicies } from 'services/api/benefit-policies.service';
import { assignBenefitPolicyToEmployer, getMembersCount } from 'services/api/members.service';

// Snackbar
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const AssignBenefitPolicyModal = ({ open, onClose, onSuccess }) => {
  // State
  const [selectedEmployer, setSelectedEmployer] = useState(null);
  const [selectedPolicy, setSelectedPolicy] = useState(null);
  const [employers, setEmployers] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [memberCount, setMemberCount] = useState(0);

  // Loading states
  const [loadingEmployers, setLoadingEmployers] = useState(false);
  const [loadingPolicies, setLoadingPolicies] = useState(false);
  const [loadingCount, setLoadingCount] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  /**
   * Load employers on mount
   */
  useEffect(() => {
    if (open) {
      loadEmployers();
      loadPolicies();
    }
  }, [open]);

  /**
   * Load member count when employer is selected
   */
  useEffect(() => {
    if (selectedEmployer) {
      loadMemberCount(selectedEmployer.id);
    } else {
      setMemberCount(0);
    }
  }, [selectedEmployer]);

  /**
   * Load employers
   */
  const loadEmployers = async () => {
    try {
      setLoadingEmployers(true);
      const response = await getEmployerSelectors();
      setEmployers(Array.isArray(response) ? response : []);
    } catch (error) {
      console.error('[AssignBenefitPolicy] Load employers error:', error);
      openSnackbar({
        message: 'فشل تحميل قائمة الشركاء',
        variant: 'error'
      });
    } finally {
      setLoadingEmployers(false);
    }
  };

  /**
   * Load benefit policies
   */
  const loadPolicies = async () => {
    try {
      setLoadingPolicies(true);
      const response = await getBenefitPolicies({ size: 1000 });

      // Extract content if paginated
      const items = response?.content || response || [];

      // Filter only active policies
      const activePolicies = items.filter((p) => p.status === 'ACTIVE');

      setPolicies(activePolicies);
    } catch (error) {
      console.error('[AssignBenefitPolicy] Load policies error:', error);
      openSnackbar({
        message: 'فشل تحميل قائمة وثائق المنافع',
        variant: 'error'
      });
    } finally {
      setLoadingPolicies(false);
    }
  };

  /**
   * Load member count for selected employer
   */
  const loadMemberCount = async (employerId) => {
    try {
      setLoadingCount(true);
      // Use the getMembersCount service with employerId parameter
      const count = await getMembersCount(employerId);
      setMemberCount(typeof count === 'number' ? count : 0);
    } catch (error) {
      console.error('[AssignBenefitPolicy] Load count error:', error);
      setMemberCount(0);
    } finally {
      setLoadingCount(false);
    }
  };

  /**
   * Handle form submit
   */
  const handleSubmit = async () => {
    // Validation
    if (!selectedEmployer) {
      openSnackbar({
        message: 'الرجاء اختيار الشريك',
        variant: 'warning'
      });
      return;
    }

    if (!selectedPolicy) {
      openSnackbar({
        message: 'الرجاء اختيار وثيقة المنافع',
        variant: 'warning'
      });
      return;
    }

    if (memberCount === 0) {
      openSnackbar({
        message: 'لا يوجد أعضاء لهذا الشريك',
        variant: 'warning'
      });
      return;
    }

    // Confirmation
    const confirmed = window.confirm(
      `هل أنت متأكد من ربط ${memberCount} عضو من "${selectedEmployer.label}" بوثيقة "${selectedPolicy.name || selectedPolicy.policyCode}"؟\n\nهذا الإجراء سيقوم بتحديث جميع الأعضاء دفعة واحدة.`
    );

    if (!confirmed) return;

    try {
      setSubmitting(true);

      await assignBenefitPolicyToEmployer(selectedEmployer.id, selectedPolicy.id);

      openSnackbar({
        message: `تم ربط ${memberCount} عضو بنجاح بوثيقة المنافع`,
        variant: 'success'
      });

      // Reset and close
      handleClose();

      // Notify parent
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      console.error('[AssignBenefitPolicy] Submit error:', error);
      openSnackbar({
        message: error.response?.data?.message || 'فشل ربط الأعضاء بوثيقة المنافع',
        variant: 'error'
      });
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Handle close
   */
  const handleClose = () => {
    setSelectedEmployer(null);
    setSelectedPolicy(null);
    setMemberCount(0);
    onClose();
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      disableEnforceFocus
      PaperProps={{
        sx: { direction: 'rtl' }
      }}
    >
      {/* Dialog Header */}
      <DialogTitle>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Stack direction="row" spacing={1} alignItems="center">
            <AssignmentIcon color="primary" />
            <Typography variant="h6" fontWeight="bold">
              تعيين وثيقة منافع
            </Typography>
          </Stack>
          <IconButton onClick={handleClose} edge="end" disabled={submitting}>
            <CloseIcon />
          </IconButton>
        </Stack>
      </DialogTitle>

      <Divider />

      {/* Dialog Content */}
      <DialogContent>
        <Stack spacing={3} sx={{ mt: '1.0rem' }}>
          {/* Info Alert */}
          <Alert severity="info" icon={<AssignmentIcon />}>
            سيتم ربط جميع أعضاء الشريك المحدد بوثيقة المنافع المختارة دفعة واحدة
          </Alert>

          {/* Employer Selection */}
          <Box>
            <Typography variant="subtitle2" fontWeight="medium" mb={1}>
              <PeopleIcon sx={{ fontSize: '1.0rem', verticalAlign: 'middle', mr: 0.5 }} />
              الشريك
            </Typography>
            <Autocomplete
              value={selectedEmployer}
              onChange={(e, value) => setSelectedEmployer(value)}
              options={employers}
              getOptionLabel={(option) => option?.label || ''}
              loading={loadingEmployers}
              disabled={submitting}
              renderInput={(params) => (
                <TextField
                  {...params}
                  placeholder="اختر الشريك..."
                  required
                  InputProps={{
                    ...params.InputProps,
                    endAdornment: (
                      <>
                        {loadingEmployers ? <CircularProgress size={20} /> : null}
                        {params.InputProps.endAdornment}
                      </>
                    )
                  }}
                />
              )}
            />
          </Box>

          {/* Member Count */}
          {selectedEmployer && (
            <Box
              sx={{
                p: '1.0rem',
                bgcolor: 'primary.lighter',
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'primary.light'
              }}
            >
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  عدد الأعضاء:
                </Typography>
                {loadingCount ? (
                  <CircularProgress size={20} />
                ) : (
                  <Chip label={memberCount} color="primary" size="small" sx={{ fontWeight: 'bold' }} />
                )}
              </Stack>
            </Box>
          )}

          {/* Benefit Policy Selection */}
          <Box>
            <Typography variant="subtitle2" fontWeight="medium" mb={1}>
              <DescriptionIcon sx={{ fontSize: '1.0rem', verticalAlign: 'middle', mr: 0.5 }} />
              وثيقة المنافع
            </Typography>
            <Autocomplete
              value={selectedPolicy}
              onChange={(e, value) => setSelectedPolicy(value)}
              options={policies}
              getOptionLabel={(option) => option?.name || option?.policyCode || ''}
              loading={loadingPolicies}
              disabled={submitting || !selectedEmployer}
              renderInput={(params) => (
                <TextField
                  {...params}
                  placeholder="اختر وثيقة المنافع..."
                  required
                  InputProps={{
                    ...params.InputProps,
                    endAdornment: (
                      <>
                        {loadingPolicies ? <CircularProgress size={20} /> : null}
                        {params.InputProps.endAdornment}
                      </>
                    )
                  }}
                />
              )}
              renderOption={(props, option) => (
                <Box component="li" {...props} key={option.id}>
                  <Stack>
                    <Typography variant="body2" fontWeight="medium">
                      {option.name || option.policyCode}
                    </Typography>
                    {option.policyCode && option.name && (
                      <Typography variant="caption" color="text.secondary">
                        رمز: {option.policyCode}
                      </Typography>
                    )}
                  </Stack>
                </Box>
              )}
            />
          </Box>

          {/* Warning */}
          {selectedEmployer && selectedPolicy && memberCount > 0 && (
            <Alert severity="warning">
              سيتم تحديث <strong>{memberCount}</strong> عضو. تأكد من صحة البيانات قبل المتابعة.
            </Alert>
          )}
        </Stack>
      </DialogContent>

      <Divider />

      {/* Dialog Actions */}
      <DialogActions sx={{ p: '1.0rem' }}>
        <Button onClick={handleClose} variant="outlined" disabled={submitting}>
          إلغاء
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={!selectedEmployer || !selectedPolicy || memberCount === 0 || submitting}
          startIcon={submitting ? <CircularProgress size={16} /> : <AssignmentIcon />}
        >
          {submitting ? 'جاري الربط...' : 'ربط'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ============================================================================
// PROP TYPES
// ============================================================================

AssignBenefitPolicyModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func
};

export default AssignBenefitPolicyModal;
