/**
 * Safe Empty States - Dashboard Production Mode
 * ==============================================
 *
 * PRODUCTION READINESS (2026-01-13):
 * - Graceful handling of empty data
 * - No permission error states
 * - API skipped states
 * - Clean UI when no production data exists
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, alpha, Skeleton } from '@mui/material';
import InboxIcon from '@mui/icons-material/Inbox';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import CloudOffIcon from '@mui/icons-material/CloudOff';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';

// ==============================|| EMPTY STATE TYPES ||============================== //

export const EmptyStateType = {
  NO_DATA: 'NO_DATA',
  NO_PERMISSION: 'NO_PERMISSION',
  API_SKIPPED: 'API_SKIPPED',
  LOADING: 'LOADING',
  ERROR: 'ERROR'
};

// ==============================|| EMPTY STATE CONFIGS ||============================== //

const stateConfigs = {
  [EmptyStateType.NO_DATA]: {
    icon: InboxIcon,
    title: 'لا توجد بيانات',
    subtitle: 'لم يتم العثور على بيانات لعرضها',
    color: 'text.secondary'
  },
  [EmptyStateType.NO_PERMISSION]: {
    icon: LockOutlinedIcon,
    title: 'غير متاح',
    subtitle: 'ليس لديك صلاحية لعرض هذه البيانات',
    color: 'warning.main'
  },
  [EmptyStateType.API_SKIPPED]: {
    icon: CloudOffIcon,
    title: 'البيانات غير متوفرة',
    subtitle: 'سيتم تحميل البيانات عند توفر الصلاحيات',
    color: 'info.main'
  },
  [EmptyStateType.LOADING]: {
    icon: HourglassEmptyIcon,
    title: 'جاري التحميل',
    subtitle: 'يرجى الانتظار...',
    color: 'primary.main'
  },
  [EmptyStateType.ERROR]: {
    icon: CloudOffIcon,
    title: 'تعذر تحميل البيانات',
    subtitle: 'حدث خطأ أثناء تحميل البيانات',
    color: 'error.main'
  }
};

// ==============================|| SAFE EMPTY STATE COMPONENT ||============================== //

/**
 * SafeEmptyState - Universal empty state component
 *
 * @param {string} type - Type of empty state (NO_DATA, NO_PERMISSION, etc.)
 * @param {string} title - Optional custom title
 * @param {string} subtitle - Optional custom subtitle
 * @param {number} minHeight - Minimum height
 * @param {boolean} compact - Use compact layout
 */
export const SafeEmptyState = ({ type = EmptyStateType.NO_DATA, title, subtitle, minHeight = 200, compact = false, children }) => {
  const config = stateConfigs[type] || stateConfigs[EmptyStateType.NO_DATA];
  const Icon = config.icon;

  return (
    <Box
      sx={{
        minHeight,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: (theme) => alpha(theme.palette.grey[500], 0.04),
        borderRadius: '0.125rem',
        p: compact ? 2 : 3
      }}
    >
      <Stack alignItems="center" spacing={compact ? 1 : 2}>
        <Icon
          sx={{
            fontSize: compact ? 32 : 48,
            color: config.color,
            opacity: 0.7
          }}
        />
        <Typography variant={compact ? 'body2' : 'h6'} color="text.secondary" textAlign="center">
          {title || config.title}
        </Typography>
        {!compact && (
          <Typography variant="body2" color="text.disabled" textAlign="center">
            {subtitle || config.subtitle}
          </Typography>
        )}
        {children}
      </Stack>
    </Box>
  );
};

SafeEmptyState.propTypes = {
  type: PropTypes.oneOf(Object.values(EmptyStateType)),
  title: PropTypes.string,
  subtitle: PropTypes.string,
  minHeight: PropTypes.number,
  compact: PropTypes.bool,
  children: PropTypes.node
};

// ==============================|| SAFE DATA WRAPPER ||============================== //

/**
 * SafeDataWrapper - Wraps data-dependent components with safe states
 *
 * @param {boolean} loading - Is data loading
 * @param {boolean} hasPermission - Does user have permission (default true)
 * @param {boolean} skipped - Was API call skipped
 * @param {any} data - The data to check
 * @param {boolean} isEmpty - Force empty state check
 * @param {number} minHeight - Minimum height for empty state
 */
export const SafeDataWrapper = ({
  loading,
  hasPermission = true,
  skipped = false,
  data,
  isEmpty,
  minHeight = 200,
  compact = false,
  children,
  loadingComponent
}) => {
  // Loading state
  if (loading) {
    if (loadingComponent) {
      return loadingComponent;
    }
    return (
      <Box sx={{ minHeight, p: '1.0rem' }}>
        <Skeleton variant="rectangular" height={minHeight - 32} sx={{ borderRadius: 1 }} />
      </Box>
    );
  }

  // No permission
  if (!hasPermission) {
    return <SafeEmptyState type={EmptyStateType.NO_PERMISSION} minHeight={minHeight} compact={compact} />;
  }

  // API was skipped (permission-aware skip)
  if (skipped) {
    return <SafeEmptyState type={EmptyStateType.API_SKIPPED} minHeight={minHeight} compact={compact} />;
  }

  // Check if data is empty
  const dataIsEmpty = isEmpty !== undefined ? isEmpty : !data || (Array.isArray(data) && data.length === 0);

  if (dataIsEmpty) {
    return <SafeEmptyState type={EmptyStateType.NO_DATA} minHeight={minHeight} compact={compact} />;
  }

  // Render children with data
  return children;
};

SafeDataWrapper.propTypes = {
  loading: PropTypes.bool,
  hasPermission: PropTypes.bool,
  skipped: PropTypes.bool,
  data: PropTypes.any,
  isEmpty: PropTypes.bool,
  minHeight: PropTypes.number,
  compact: PropTypes.bool,
  children: PropTypes.node.isRequired,
  loadingComponent: PropTypes.node
};

// ==============================|| SAFE CHART WRAPPER ||============================== //

/**
 * SafeChartWrapper - Specifically for chart components
 */
export const SafeChartWrapper = ({ loading, hasPermission = true, data, height = 300, children }) => {
  return (
    <SafeDataWrapper
      loading={loading}
      hasPermission={hasPermission}
      data={data}
      isEmpty={!data || (Array.isArray(data) && data.length === 0)}
      minHeight={height}
      loadingComponent={
        <Box sx={{ height, p: '1.0rem' }}>
          <Skeleton variant="rectangular" height={height - 32} sx={{ borderRadius: 1 }} />
        </Box>
      }
    >
      {children}
    </SafeDataWrapper>
  );
};

SafeChartWrapper.propTypes = {
  loading: PropTypes.bool,
  hasPermission: PropTypes.bool,
  data: PropTypes.any,
  height: PropTypes.number,
  children: PropTypes.node.isRequired
};

// ==============================|| SAFE TABLE WRAPPER ||============================== //

/**
 * SafeTableWrapper - Specifically for table components
 */
export const SafeTableWrapper = ({ loading, hasPermission = true, data, minRows = 5, rowHeight = 53, children }) => {
  const minHeight = minRows * rowHeight + 56; // +56 for header

  return (
    <SafeDataWrapper
      loading={loading}
      hasPermission={hasPermission}
      data={data}
      isEmpty={!data || (Array.isArray(data) && data.length === 0)}
      minHeight={minHeight}
      loadingComponent={
        <Box sx={{ p: '1.0rem' }}>
          <Skeleton variant="rectangular" height={56} sx={{ mb: 1, borderRadius: 1 }} />
          {Array.from({ length: minRows }).map((_, i) => (
            <Skeleton key={i} variant="rectangular" height={rowHeight - 8} sx={{ mb: 1, borderRadius: 1 }} />
          ))}
        </Box>
      }
    >
      {children}
    </SafeDataWrapper>
  );
};

SafeTableWrapper.propTypes = {
  loading: PropTypes.bool,
  hasPermission: PropTypes.bool,
  data: PropTypes.any,
  minRows: PropTypes.number,
  rowHeight: PropTypes.number,
  children: PropTypes.node.isRequired
};

// ==============================|| EXPORTS ||============================== //

export default {
  SafeEmptyState,
  SafeDataWrapper,
  SafeChartWrapper,
  SafeTableWrapper,
  EmptyStateType
};
