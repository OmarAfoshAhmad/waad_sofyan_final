import PropTypes from 'prop-types';
import { Card, CardContent, CardHeader, Typography, Box, Chip, Skeleton, Stack } from '@mui/material';
import { Timeline, TimelineItem, TimelineSeparator, TimelineConnector, TimelineContent, TimelineDot } from '@mui/lab';
import { PersonAdd, Receipt, Assignment, Warning, CheckCircle, Cancel, Info } from '@mui/icons-material';

/**
 * Recent Activity Timeline Component
 * Inspired by Mantis Timeline UI
 */
const RecentActivity = ({ data, loading }) => {
  const getActivityIcon = (type) => {
    const iconProps = { sx: { fontSize: '1.25rem' } };
    switch (type) {
      case 'MEMBER_ADDED':
        return <PersonAdd {...iconProps} />;
      case 'CLAIM_SUBMITTED':
        return <Receipt {...iconProps} />;
      case 'CLAIM_APPROVED':
        return <CheckCircle {...iconProps} />;
      case 'CLAIM_REJECTED':
        return <Cancel {...iconProps} />;
      case 'CONTRACT_UPDATED':
        return <Assignment {...iconProps} />;
      case 'ALERT':
        return <Warning {...iconProps} />;
      default:
        return <Info {...iconProps} />;
    }
  };

  const getActivityColor = (type) => {
    switch (type) {
      case 'MEMBER_ADDED':
        return 'success';
      case 'CLAIM_SUBMITTED':
        return 'info';
      case 'CLAIM_APPROVED':
        return 'success';
      case 'CLAIM_REJECTED':
        return 'error';
      case 'CONTRACT_UPDATED':
        return 'warning';
      case 'ALERT':
        return 'error';
      default:
        return 'primary';
    }
  };

  const getActivityLabel = (type) => {
    const labels = {
      MEMBER_ADDED: 'تمت إضافة عضو',
      CLAIM_SUBMITTED: 'تم تقديم مطالبة',
      CLAIM_APPROVED: 'تم اعتماد مطالبة',
      CLAIM_REJECTED: 'تم رفض مطالبة',
      CONTRACT_UPDATED: 'تم تحديث عقد',
      ALERT: 'تنبيه'
    };
    return labels[type] || type;
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      const now = new Date();
      const diffMs = now - date;
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMs / 3600000);
      const diffDays = Math.floor(diffMs / 86400000);

      if (diffMins < 1) return 'الآن';
      if (diffMins < 60) return `منذ ${diffMins} دقيقة`;
      if (diffHours < 24) return `منذ ${diffHours} ساعة`;
      if (diffDays < 7) return `منذ ${diffDays} يوم`;
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    } catch {
      return '—';
    }
  };

  if (loading) {
    return (
      <Card>
        <CardHeader title="الأنشطة الأخيرة" />
        <CardContent>
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} variant="rectangular" height={60} sx={{ mb: '1.0rem' }} />
          ))}
        </CardContent>
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="الأنشطة الأخيرة" />
        <CardContent>
          <Typography variant="body2" color="text.secondary" align="center">
            لا توجد أنشطة حديثة
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="الأنشطة الأخيرة" subheader="آخر 10 أنشطة" />
      <CardContent>
        <Timeline>
          {data.slice(0, 10).map((activity, index) => (
            <TimelineItem key={`${activity.type || 'activity'}-${activity.id || index}-${index}`}>
              <TimelineSeparator>
                <TimelineDot color={getActivityColor(activity.type)} variant="outlined">
                  {getActivityIcon(activity.type)}
                </TimelineDot>
                {index < data.length - 1 && <TimelineConnector />}
              </TimelineSeparator>
              <TimelineContent>
                <Box sx={{ mb: 1 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                    <Box>
                      <Typography variant="subtitle2" fontWeight={600}>
                        {activity.title || getActivityLabel(activity.type)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {activity.description || activity.message}
                      </Typography>
                    </Box>
                    <Chip label={formatDate(activity.createdAt || activity.date)} size="small" variant="outlined" />
                  </Stack>
                  {activity.entityName && (
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                      {activity.entityName}
                    </Typography>
                  )}
                </Box>
              </TimelineContent>
            </TimelineItem>
          ))}
        </Timeline>
      </CardContent>
    </Card>
  );
};

RecentActivity.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      type: PropTypes.string,
      title: PropTypes.string,
      description: PropTypes.string,
      message: PropTypes.string,
      entityName: PropTypes.string,
      createdAt: PropTypes.string,
      date: PropTypes.string
    })
  ),
  loading: PropTypes.bool
};

export default RecentActivity;
