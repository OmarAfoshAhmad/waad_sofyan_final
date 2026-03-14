/**
 * SafeDataWrapper - Safe Data Rendering
 * ======================================
 *
 * PRODUCTION STABILIZATION (2026-02-03):
 * - Prevents crashes from null/undefined data
 * - Shows appropriate loading/empty/error states
 * - Wraps data fetching components safely
 *
 * USAGE:
 * <SafeDataWrapper
 *   data={data}
 *   loading={isLoading}
 *   error={error}
 *   onRetry={refetch}
 * >
 *   <DataTable data={data} />
 * </SafeDataWrapper>
 */

import PropTypes from 'prop-types';
import { Box, CircularProgress, Typography, Button, Stack } from '@mui/material';
import InboxIcon from '@mui/icons-material/Inbox';
import RefreshIcon from '@mui/icons-material/Refresh';
import ApiErrorHandler from './ApiErrorHandler';

// ==============================|| LOADING STATE ||============================== //

const LoadingState = ({ message = 'جاري التحميل...' }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '12.5rem',
      py: '2.0rem'
    }}
  >
    <CircularProgress size={40} thickness={4} />
    <Typography variant="body2" color="text.secondary" sx={{ mt: '1.0rem' }}>
      {message}
    </Typography>
  </Box>
);

LoadingState.propTypes = {
  message: PropTypes.string
};

// ==============================|| EMPTY STATE ||============================== //

const EmptyState = ({ title = 'لا توجد بيانات', message = 'لا توجد عناصر لعرضها', icon: Icon = InboxIcon, onRetry }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '12.5rem',
      py: '2.0rem',
      textAlign: 'center'
    }}
  >
    <Icon sx={{ fontSize: '4.0rem', color: 'text.disabled', mb: '1.0rem' }} />
    <Typography variant="h6" color="text.secondary" gutterBottom>
      {title}
    </Typography>
    <Typography variant="body2" color="text.disabled" sx={{ mb: '1.0rem' }}>
      {message}
    </Typography>
    {onRetry && (
      <Button variant="outlined" size="small" startIcon={<RefreshIcon />} onClick={onRetry}>
        تحديث
      </Button>
    )}
  </Box>
);

EmptyState.propTypes = {
  title: PropTypes.string,
  message: PropTypes.string,
  icon: PropTypes.elementType,
  onRetry: PropTypes.func
};

// ==============================|| SAFE DATA WRAPPER ||============================== //

const SafeDataWrapper = ({
  data,
  loading = false,
  error = null,
  children,
  onRetry,
  loadingMessage,
  emptyTitle,
  emptyMessage,
  emptyIcon,
  showEmptyState = true,
  minItems = 0,
  compact = false,
  customEmptyState = null,
  customLoadingState = null
}) => {
  // 1. Show loading state
  if (loading) {
    if (customLoadingState) return customLoadingState;
    return <LoadingState message={loadingMessage} />;
  }

  // 2. Show error state (handled by ApiErrorHandler)
  if (error) {
    return <ApiErrorHandler error={error} onRetry={onRetry} compact={compact} />;
  }

  // 3. Check for empty data
  const isEmpty =
    !data ||
    (Array.isArray(data) && data.length <= minItems) ||
    (typeof data === 'object' && !Array.isArray(data) && Object.keys(data).length === 0);

  if (isEmpty && showEmptyState) {
    if (customEmptyState) return customEmptyState;
    return <EmptyState title={emptyTitle} message={emptyMessage} icon={emptyIcon} onRetry={onRetry} />;
  }

  // 4. Render children safely
  // If children is a function, call it with data
  if (typeof children === 'function') {
    try {
      return children(data);
    } catch (err) {
      console.error('SafeDataWrapper: Error rendering children function', err);
      return <ApiErrorHandler error={{ status: 500, message: err.message }} onRetry={onRetry} compact={compact} />;
    }
  }

  return children;
};

SafeDataWrapper.propTypes = {
  data: PropTypes.any,
  loading: PropTypes.bool,
  error: PropTypes.object,
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.func]),
  onRetry: PropTypes.func,
  loadingMessage: PropTypes.string,
  emptyTitle: PropTypes.string,
  emptyMessage: PropTypes.string,
  emptyIcon: PropTypes.elementType,
  showEmptyState: PropTypes.bool,
  minItems: PropTypes.number,
  compact: PropTypes.bool,
  customEmptyState: PropTypes.node,
  customLoadingState: PropTypes.node
};

// ==============================|| EXPORTS ||============================== //

export { SafeDataWrapper, LoadingState, EmptyState };
export default SafeDataWrapper;
