/**
 * AuditTimeline Component
 * Displays audit trail events in a vertical timeline format
 *
 * @component
 * @example
 * <AuditTimeline
 *   audits={auditData}
 *   loading={false}
 *   onLoadMore={() => {}}
 *   hasMore={true}
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, Paper, Chip, Avatar, Divider, Button, CircularProgress, useTheme, alpha } from '@mui/material';
import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent
} from '@mui/lab';
import {
  Add as CreateIcon,
  Edit as UpdateIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Block as CancelIcon,
  Delete as DeleteIcon,
  SwapHoriz as StatusChangeIcon,
  Person as PersonIcon
} from '@mui/icons-material';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/ar';

dayjs.extend(relativeTime);
dayjs.locale('ar');

// Action type configuration
const ACTION_CONFIG = {
  CREATE: {
    icon: CreateIcon,
    label: 'إنشاء',
    color: 'info',
    bgColor: 'info.lighter'
  },
  UPDATE: {
    icon: UpdateIcon,
    label: 'تحديث',
    color: 'warning',
    bgColor: 'warning.lighter'
  },
  APPROVE: {
    icon: ApproveIcon,
    label: 'موافقة',
    color: 'success',
    bgColor: 'success.lighter'
  },
  REJECT: {
    icon: RejectIcon,
    label: 'رفض',
    color: 'error',
    bgColor: 'error.lighter'
  },
  CANCEL: {
    icon: CancelIcon,
    label: 'إلغاء',
    color: 'secondary',
    bgColor: 'secondary.lighter'
  },
  DELETE: {
    icon: DeleteIcon,
    label: 'حذف',
    color: 'error',
    bgColor: 'error.lighter'
  },
  STATUS_CHANGE: {
    icon: StatusChangeIcon,
    label: 'تغيير الحالة',
    color: 'primary',
    bgColor: 'primary.lighter'
  }
};

// Get action configuration
const getActionConfig = (action) => {
  return ACTION_CONFIG[action] || ACTION_CONFIG.UPDATE;
};

// Format field name to Arabic
const formatFieldName = (fieldName) => {
  const fieldMap = {
    status: 'الحالة',
    requestedAmount: 'المبلغ المطلوب',
    approvedAmount: 'المبلغ الموافق عليه',
    notes: 'الملاحظات',
    diagnosis: 'التشخيص',
    treatmentPlan: 'خطة العلاج',
    priority: 'الأولوية',
    expiryDate: 'تاريخ الانتهاء',
    reviewerComment: 'تعليق المراجع'
  };
  return fieldMap[fieldName] || fieldName;
};

// Format change value
const formatValue = (value) => {
  if (value === null || value === undefined || value === '') return '-';

  // Handle dates
  if (value.match && value.match(/^\d{4}-\d{2}-\d{2}/)) {
    return dayjs(value).format('YYYY-MM-DD');
  }

  // Handle numbers
  if (!isNaN(value) && value.toString().includes('.')) {
    return parseFloat(value).toFixed(2);
  }

  return value;
};

// Single Audit Item Component
const AuditItem = ({ audit, isLast }) => {
  const theme = useTheme();
  const config = getActionConfig(audit.action);
  const ActionIcon = config.icon;

  const hasChanges = audit.fieldName && (audit.oldValue || audit.newValue);

  return (
    <TimelineItem>
      {/* Time (Opposite Content) */}
      <TimelineOppositeContent
        sx={{
          flex: 0.3,
          pt: '1.0rem',
          pr: '1.0rem'
        }}
      >
        <Typography variant="caption" color="text.secondary">
          {dayjs(audit.changeDate).format('HH:mm')}
        </Typography>
        <Typography variant="caption" display="block" color="text.secondary">
          {dayjs(audit.changeDate).format('MMM DD')}
        </Typography>
        <Typography variant="caption" display="block" color="primary.main" sx={{ mt: 0.5 }}>
          {dayjs(audit.changeDate).fromNow()}
        </Typography>
      </TimelineOppositeContent>

      {/* Separator with Dot */}
      <TimelineSeparator>
        <TimelineDot
          sx={{
            bgcolor: config.bgColor,
            borderColor: `${config.color}.main`,
            borderWidth: 2,
            width: '2.5rem',
            height: '2.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          <ActionIcon sx={{ fontSize: '1.25rem', color: `${config.color}.dark` }} />
        </TimelineDot>
        {!isLast && (
          <TimelineConnector
            sx={{
              bgcolor: theme.palette.grey[300],
              width: '0.125rem'
            }}
          />
        )}
      </TimelineSeparator>

      {/* Content */}
      <TimelineContent sx={{ pb: '2.0rem' }}>
        <Paper
          elevation={0}
          sx={{
            p: '1.0rem',
            bgcolor: alpha(theme.palette[config.color].lighter, 0.3),
            border: `1px solid ${theme.palette[config.color].light}`,
            borderRadius: '0.25rem'
          }}
        >
          {/* Header */}
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
            <Chip label={config.label} size="small" color={config.color} sx={{ fontWeight: 600 }} />
            <Box sx={{ flex: 1 }} />
            <Stack direction="row" alignItems="center" spacing={1}>
              <Avatar
                sx={{
                  width: '1.5rem',
                  height: '1.5rem',
                  bgcolor: `${config.color}.main`,
                  fontSize: '0.75rem'
                }}
              >
                <PersonIcon sx={{ fontSize: '0.875rem' }} />
              </Avatar>
              <Typography variant="caption" fontWeight={500}>
                {audit.changedBy}
              </Typography>
            </Stack>
          </Stack>

          {/* Reference Number */}
          {audit.referenceNumber && (
            <Typography variant="body2" fontWeight={600} sx={{ mb: 1 }}>
              {audit.referenceNumber}
            </Typography>
          )}

          {/* Changes (Old → New) */}
          {hasChanges && (
            <Box
              sx={{
                mt: '0.75rem',
                p: '0.75rem',
                bgcolor: theme.palette.background.paper,
                borderRadius: 1,
                border: `1px solid ${theme.palette.divider}`
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                {formatFieldName(audit.fieldName)}
              </Typography>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Chip
                  label={formatValue(audit.oldValue)}
                  size="small"
                  variant="outlined"
                  sx={{
                    bgcolor: theme.palette.error.lighter,
                    borderColor: theme.palette.error.light,
                    textDecoration: 'line-through'
                  }}
                />
                <Typography variant="caption" color="text.secondary">
                  →
                </Typography>
                <Chip
                  label={formatValue(audit.newValue)}
                  size="small"
                  sx={{
                    bgcolor: theme.palette.success.lighter,
                    borderColor: theme.palette.success.light,
                    fontWeight: 600
                  }}
                />
              </Stack>
            </Box>
          )}

          {/* Notes */}
          {audit.notes && (
            <Box sx={{ mt: '0.75rem' }}>
              <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                الملاحظات:
              </Typography>
              <Typography variant="body2" sx={{ fontStyle: 'italic' }}>
                {audit.notes}
              </Typography>
            </Box>
          )}

          {/* IP Address */}
          {audit.ipAddress && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              IP: {audit.ipAddress}
            </Typography>
          )}
        </Paper>
      </TimelineContent>
    </TimelineItem>
  );
};

AuditItem.propTypes = {
  audit: PropTypes.shape({
    id: PropTypes.number,
    preAuthorizationId: PropTypes.number,
    referenceNumber: PropTypes.string,
    changedBy: PropTypes.string.isRequired,
    changeDate: PropTypes.string.isRequired,
    action: PropTypes.oneOf(['CREATE', 'UPDATE', 'APPROVE', 'REJECT', 'CANCEL', 'DELETE', 'STATUS_CHANGE']).isRequired,
    fieldName: PropTypes.string,
    oldValue: PropTypes.string,
    newValue: PropTypes.string,
    notes: PropTypes.string,
    ipAddress: PropTypes.string
  }).isRequired,
  isLast: PropTypes.bool
};

// Main Timeline Component
const AuditTimeline = ({ audits = [], loading = false, onLoadMore, hasMore = false }) => {
  const theme = useTheme();

  if (loading && audits.length === 0) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
        <CircularProgress />
      </Box>
    );
  }

  if (!loading && audits.length === 0) {
    return (
      <Paper
        sx={{
          p: '3.0rem',
          textAlign: 'center',
          bgcolor: theme.palette.grey[50]
        }}
      >
        <Typography variant="h6" color="text.secondary">
          لا توجد سجلات تدقيق
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          لم يتم تسجيل أي إجراءات حتى الآن
        </Typography>
      </Paper>
    );
  }

  return (
    <Box>
      <Timeline position="right">
        {audits.map((audit, index) => (
          <AuditItem key={audit.id || index} audit={audit} isLast={index === audits.length - 1 && !hasMore} />
        ))}
      </Timeline>

      {/* Load More Button */}
      {hasMore && (
        <Box sx={{ textAlign: 'center', mt: '1.5rem' }}>
          <Button variant="outlined" onClick={onLoadMore} disabled={loading} startIcon={loading ? <CircularProgress size={20} /> : null}>
            {loading ? 'جارِ التحميل...' : 'تحميل المزيد'}
          </Button>
        </Box>
      )}
    </Box>
  );
};

AuditTimeline.propTypes = {
  audits: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.number,
      preAuthorizationId: PropTypes.number,
      referenceNumber: PropTypes.string,
      changedBy: PropTypes.string.isRequired,
      changeDate: PropTypes.string.isRequired,
      action: PropTypes.oneOf(['CREATE', 'UPDATE', 'APPROVE', 'REJECT', 'CANCEL', 'DELETE', 'STATUS_CHANGE']).isRequired,
      fieldName: PropTypes.string,
      oldValue: PropTypes.string,
      newValue: PropTypes.string,
      notes: PropTypes.string,
      ipAddress: PropTypes.string
    })
  ),
  loading: PropTypes.bool,
  onLoadMore: PropTypes.func,
  hasMore: PropTypes.bool
};

export default AuditTimeline;


