import PropTypes from 'prop-types';

// material-ui
import { Box, Button, Stack, Typography, Alert } from '@mui/material';
import ReloadOutlined from '@ant-design/icons/ReloadOutlined';
import WarningOutlined from '@ant-design/icons/WarningOutlined';

/**
 * Error Fallback Component
 * Displays error message with retry option
 */
export default function ErrorFallback({ error, onRetry, fullHeight = false }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: fullHeight ? 400 : 200,
        p: '1.5rem'
      }}
    >
      <Stack spacing={2} alignItems="center" maxWidth={500}>
        <WarningOutlined style={{ fontSize: '3.0rem', color: '#ff4d4f' }} />

        <Typography variant="h5" color="error" textAlign="center">
          Something went wrong
        </Typography>

        {error && (
          <Alert severity="error" sx={{ width: '100%' }}>
            {typeof error === 'string' ? error : error.message || 'عذراً، حدث خطأ غير متوقع ولن نتمكن من عرض هذا القسم'}
          </Alert>
        )}

        {onRetry && (
          <Button variant="contained" startIcon={<ReloadOutlined />} onClick={onRetry} sx={{ mt: '1.0rem' }}>
            Try Again
          </Button>
        )}
      </Stack>
    </Box>
  );
}

ErrorFallback.propTypes = {
  error: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
  onRetry: PropTypes.func,
  fullHeight: PropTypes.bool
};

/**
 * Empty State Component
 * Displays when no data is available
 */
export function EmptyState({ title = 'No data available', description, action, actionLabel = 'Add New', icon }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '18.75rem',
        p: '1.5rem'
      }}
    >
      <Stack spacing={2} alignItems="center" maxWidth={400}>
        {icon || (
          <Box
            sx={{
              width: '5.0rem',
              height: '5.0rem',
              borderRadius: '50%',
              bgcolor: 'grey.100',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Typography variant="h2" color="text.secondary">
              📭
            </Typography>
          </Box>
        )}

        <Typography variant="h5" color="text.primary" textAlign="center">
          {title}
        </Typography>

        {description && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            {description}
          </Typography>
        )}

        {action && (
          <Button variant="contained" onClick={action} sx={{ mt: '1.0rem' }}>
            {actionLabel}
          </Button>
        )}
      </Stack>
    </Box>
  );
}

EmptyState.propTypes = {
  title: PropTypes.string,
  description: PropTypes.string,
  action: PropTypes.func,
  actionLabel: PropTypes.string,
  icon: PropTypes.node
};
