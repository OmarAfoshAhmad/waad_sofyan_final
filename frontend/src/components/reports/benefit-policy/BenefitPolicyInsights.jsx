import PropTypes from 'prop-types';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  LinearProgress,
  Chip,
  List,
  ListItem,
  ListItemText,
  Divider,
  Skeleton
} from '@mui/material';
import {
  TrendingUp as TrendingIcon,
  Business as BusinessIcon,
  Warning as WarningIcon,
  PieChart as PieChartIcon
} from '@mui/icons-material';

import { STATUS_CONFIG } from 'hooks/useBenefitPolicyReport';

/**
 * Progress bar with label
 */
const ProgressWithLabel = ({ value, max, label, color = 'primary' }) => {
  const percentage = max > 0 ? (value / max) * 100 : 0;

  return (
    <Box sx={{ mb: '0.75rem' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography variant="body2" noWrap sx={{ maxWidth: '60%' }}>
          {label}
        </Typography>
        <Typography variant="body2" fontWeight={600} color={`${color}.main`}>
          {value.toLocaleString('en-US')}
        </Typography>
      </Box>
      <LinearProgress variant="determinate" value={Math.min(percentage, 100)} color={color} sx={{ height: '0.375rem', borderRadius: '0.1875rem' }} />
    </Box>
  );
};

ProgressWithLabel.propTypes = {
  value: PropTypes.number.isRequired,
  max: PropTypes.number.isRequired,
  label: PropTypes.string.isRequired,
  color: PropTypes.string
};

/**
 * Section Card wrapper
 */
const InsightCard = ({ title, titleEn, icon: Icon, children, loading }) => (
  <Card
    elevation={0}
    sx={{
      height: '100%',
      border: '1px solid',
      borderColor: 'divider',
      borderRadius: '0.125rem'
    }}
  >
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.0rem' }}>
        <Icon fontSize="small" color="primary" />
        <Box>
          <Typography variant="subtitle2" fontWeight={600}>
            {title}
          </Typography>
          <Typography variant="caption" color="text.disabled">
            {titleEn}
          </Typography>
        </Box>
      </Box>

      {loading ? (
        <Box>
          <Skeleton variant="text" width="100%" />
          <Skeleton variant="text" width="80%" />
          <Skeleton variant="text" width="90%" />
        </Box>
      ) : (
        children
      )}
    </CardContent>
  </Card>
);

InsightCard.propTypes = {
  title: PropTypes.string.isRequired,
  titleEn: PropTypes.string.isRequired,
  icon: PropTypes.elementType.isRequired,
  children: PropTypes.node,
  loading: PropTypes.bool
};

/**
 * BenefitPolicy Insights Component
 *
 * Displays strategic insights:
 * 1. Policies by Status - Distribution across statuses
 * 2. Top 5 Policies by Members - Most utilized policies
 * 3. Coverage by Employer - Members per employer
 * 4. Unused Policies - Policies requiring attention
 */
const BenefitPolicyInsights = ({ insights = {}, loading = false }) => {
  const { policiesByStatus = [], topPoliciesByMembers = [], coverageByEmployer = [], unusedPolicies = [] } = insights;

  // Calculate max values for progress bars
  const maxMembers = topPoliciesByMembers.length > 0 ? Math.max(...topPoliciesByMembers.map((p) => p.memberCount)) : 1;

  const maxEmployerMembers = coverageByEmployer.length > 0 ? Math.max(...coverageByEmployer.map((e) => e.members)) : 1;

  return (
    <Grid container spacing={2}>
      {/* Policies by Status Distribution */}
      <Grid size={{ xs: 12, md: 6, lg: 3 }}>
        <InsightCard title="توزيع الوثائق حسب الحالة" titleEn="Policies by Status" icon={PieChartIcon} loading={loading}>
          {policiesByStatus.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              لا توجد بيانات
            </Typography>
          ) : (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {policiesByStatus.map((item) => (
                <Chip key={item.status} label={`${item.label}: ${item.count}`} color={item.color} size="small" sx={{ fontWeight: 500 }} />
              ))}
            </Box>
          )}
        </InsightCard>
      </Grid>

      {/* Top 5 Policies by Members */}
      <Grid size={{ xs: 12, md: 6, lg: 3 }}>
        <InsightCard title="أكثر الوثائق استخداماً" titleEn="Top 5 Policies by Members" icon={TrendingIcon} loading={loading}>
          {topPoliciesByMembers.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              لا توجد بيانات
            </Typography>
          ) : (
            topPoliciesByMembers.map((policy, index) => (
              <ProgressWithLabel key={policy.id || index} label={policy.name} value={policy.memberCount} max={maxMembers} color="primary" />
            ))
          )}
        </InsightCard>
      </Grid>

      {/* Coverage by Employer */}
      <Grid size={{ xs: 12, md: 6, lg: 3 }}>
        <InsightCard title="التغطية حسب الشريك" titleEn="Coverage by Employer" icon={BusinessIcon} loading={loading}>
          {coverageByEmployer.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              لا توجد بيانات
            </Typography>
          ) : (
            coverageByEmployer.map((employer, index) => (
              <ProgressWithLabel
                key={employer.name || index}
                label={`${employer.name} (${employer.policies} وثائق)`}
                value={employer.members}
                max={maxEmployerMembers}
                color="secondary"
              />
            ))
          )}
        </InsightCard>
      </Grid>

      {/* Unused Policies - Warning Section */}
      <Grid size={{ xs: 12, md: 6, lg: 3 }}>
        <InsightCard title="وثائق بدون أعضاء" titleEn="Unused Policies" icon={WarningIcon} loading={loading}>
          {unusedPolicies.length === 0 ? (
            <Typography variant="body2" color="success.main">
              ✓ جميع الوثائق مستخدمة
            </Typography>
          ) : (
            <List dense disablePadding>
              {unusedPolicies.slice(0, 5).map((policy, index) => (
                <Box key={policy.id || index}>
                  {index > 0 && <Divider />}
                  <ListItem disablePadding sx={{ py: 0.5 }}>
                    <ListItemText
                      primary={
                        <Typography variant="body2" noWrap>
                          {policy.name}
                        </Typography>
                      }
                      secondary={
                        <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
                          <Typography variant="caption" color="text.disabled">
                            {policy.code}
                          </Typography>
                          <Chip
                            label={STATUS_CONFIG[policy.status]?.label ?? policy.status}
                            size="small"
                            sx={{ height: '1.0rem', fontSize: '0.75rem' }}
                          />
                        </Box>
                      }
                    />
                  </ListItem>
                </Box>
              ))}
              {unusedPolicies.length > 5 && (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                  +{unusedPolicies.length - 5} وثائق أخرى
                </Typography>
              )}
            </List>
          )}
        </InsightCard>
      </Grid>
    </Grid>
  );
};

BenefitPolicyInsights.propTypes = {
  insights: PropTypes.shape({
    policiesByStatus: PropTypes.arrayOf(
      PropTypes.shape({
        status: PropTypes.string,
        label: PropTypes.string,
        color: PropTypes.string,
        count: PropTypes.number
      })
    ),
    topPoliciesByMembers: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.number,
        name: PropTypes.string,
        code: PropTypes.string,
        memberCount: PropTypes.number
      })
    ),
    coverageByEmployer: PropTypes.arrayOf(
      PropTypes.shape({
        name: PropTypes.string,
        policies: PropTypes.number,
        members: PropTypes.number
      })
    ),
    unusedPolicies: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.number,
        name: PropTypes.string,
        code: PropTypes.string,
        status: PropTypes.string
      })
    )
  }),
  loading: PropTypes.bool
};

export default BenefitPolicyInsights;



