/**
 * ApiErrorHandler - Centralized API Error Handling
 * =================================================
 *
 * PRODUCTION STABILIZATION (2026-02-03):
 * - Catches and displays user-friendly API errors
 * - Handles 401, 403, 404, 500 errors gracefully
 * - Prevents page crash from null/undefined data
 * - Provides recovery actions
 *
 * USAGE:
 * <ApiErrorHandler error={error} onRetry={refetch}>
 *   <YourComponent data={data} />
 * </ApiErrorHandler>
 */

import PropTypes from 'prop-types';
import { Box, Button, Card, CardContent, Typography, Stack, alpha } from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import LockIcon from '@mui/icons-material/Lock';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import CloudOffIcon from '@mui/icons-material/CloudOff';
import RefreshIcon from '@mui/icons-material/Refresh';
import HomeIcon from '@mui/icons-material/Home';
import LoginIcon from '@mui/icons-material/Login';

// ==============================|| ERROR CONFIGURATIONS ||============================== //

const ERROR_CONFIGS = {
  401: {
    icon: LockIcon,
    title: 'انتهت الجلسة',
    titleEn: 'Session Expired',
    message: 'يرجى تسجيل الدخول مرة أخرى للمتابعة',
    messageEn: 'Please log in again to continue',
    color: 'warning',
    showLogin: true,
    showRetry: false
  },
  403: {
    icon: LockIcon,
    title: 'غير مصرح',
    titleEn: 'Access Denied',
    message: 'ليس لديك صلاحية للوصول إلى هذا المحتوى',
    messageEn: 'You do not have permission to access this content',
    color: 'error',
    showLogin: false,
    showRetry: false
  },
  404: {
    icon: SearchOffIcon,
    title: 'غير موجود',
    titleEn: 'Not Found',
    message: 'المحتوى المطلوب غير موجود',
    messageEn: 'The requested content was not found',
    color: 'info',
    showLogin: false,
    showRetry: true
  },
  500: {
    icon: CloudOffIcon,
    title: 'خطأ في الخادم',
    titleEn: 'Server Error',
    message: 'حدث خطأ تقني. يرجى المحاولة مرة أخرى',
    messageEn: 'A technical error occurred. Please try again',
    color: 'error',
    showLogin: false,
    showRetry: true
  },
  default: {
    icon: ErrorOutlineIcon,
    title: 'حدث خطأ',
    titleEn: 'Error Occurred',
    message: 'عذراً، واجه النظام مشكلة أثناء معالجة طلبك، يرجى إعادة المحاولة.',
    messageEn: 'Sorry, the system encountered an issue while processing your request. Please try again.',
    color: 'error',
    showLogin: false,
    showRetry: true
  }
};

// ==============================|| GET ERROR CONFIG ||============================== //

const getErrorConfig = (error) => {
  if (!error) return null;

  // Extract status code from error
  const status = error?.response?.status || error?.status || error?.statusCode;

  // Get config based on status
  if (status && ERROR_CONFIGS[status]) {
    return { ...ERROR_CONFIGS[status], status };
  }

  // Check for network errors
  if (error?.message?.includes('Network Error') || error?.code === 'ERR_NETWORK') {
    return {
      ...ERROR_CONFIGS[500],
      title: 'خطأ في الاتصال',
      titleEn: 'Connection Error',
      message: 'لا يمكن الاتصال بالخادم. تحقق من اتصال الإنترنت',
      messageEn: 'Cannot connect to server. Check your internet connection',
      status: 'NETWORK'
    };
  }

  return { ...ERROR_CONFIGS.default, status: 'UNKNOWN' };
};

// ==============================|| API ERROR DISPLAY ||============================== //

const ApiErrorDisplay = ({ config, onRetry, onGoHome, onLogin, compact = false }) => {
  const IconComponent = config.icon;

  if (compact) {
    return (
      <Box
        sx={{
          p: '1.5rem',
          textAlign: 'center',
          bgcolor: (theme) => alpha(theme.palette[config.color].light, 0.1),
          borderRadius: '0.125rem'
        }}
      >
        <IconComponent sx={{ fontSize: '3.0rem', color: `${config.color}.main`, mb: 1 }} />
        <Typography variant="h6" color={`${config.color}.main`} gutterBottom>
          {config.title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
          {config.message}
        </Typography>
        <Stack direction="row" spacing={1} justifyContent="center">
          {config.showRetry && onRetry && (
            <Button variant="contained" color={config.color} size="small" startIcon={<RefreshIcon />} onClick={onRetry}>
              إعادة المحاولة
            </Button>
          )}
          {config.showLogin && onLogin && (
            <Button variant="contained" color="primary" size="small" startIcon={<LoginIcon />} onClick={onLogin}>
              تسجيل الدخول
            </Button>
          )}
        </Stack>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '25.0rem',
        p: '1.5rem'
      }}
    >
      <Card
        sx={{
          maxWidth: '28.125rem',
          width: '100%',
          textAlign: 'center',
          boxShadow: (theme) => `0 4px 20px ${alpha(theme.palette[config.color].main, 0.15)}`
        }}
      >
        <CardContent sx={{ p: '2.0rem' }}>
          <IconComponent
            sx={{
              fontSize: '4.5rem',
              color: `${config.color}.main`,
              mb: '1.0rem'
            }}
          />

          <Typography variant="h4" color={`${config.color}.main`} gutterBottom fontWeight={600}>
            {config.title}
          </Typography>

          <Typography variant="body1" color="text.secondary" sx={{ mb: '1.5rem' }}>
            {config.message}
          </Typography>

          <Stack direction="row" spacing={2} justifyContent="center">
            {config.showRetry && onRetry && (
              <Button variant="contained" color={config.color} startIcon={<RefreshIcon />} onClick={onRetry}>
                إعادة المحاولة
              </Button>
            )}

            {config.showLogin && onLogin && (
              <Button variant="contained" color="primary" startIcon={<LoginIcon />} onClick={onLogin}>
                تسجيل الدخول
              </Button>
            )}

            <Button variant="outlined" color="primary" startIcon={<HomeIcon />} onClick={onGoHome}>
              الرئيسية
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

ApiErrorDisplay.propTypes = {
  config: PropTypes.object.isRequired,
  onRetry: PropTypes.func,
  onGoHome: PropTypes.func.isRequired,
  onLogin: PropTypes.func,
  compact: PropTypes.bool
};

// ==============================|| API ERROR HANDLER ||============================== //

const ApiErrorHandler = ({ error, loading = false, children, onRetry, compact = false, fallback = null }) => {
  const handleGoHome = () => {
    window.location.href = '/dashboard';
  };

  const handleLogin = () => {
    window.location.href = '/login';
  };

  // If loading, don't show error
  if (loading) {
    return children;
  }

  // If no error, render children
  if (!error) {
    return children;
  }

  // Get error configuration
  const config = getErrorConfig(error);

  // If custom fallback provided, use it
  if (fallback) {
    return fallback;
  }

  return <ApiErrorDisplay config={config} onRetry={onRetry} onGoHome={handleGoHome} onLogin={handleLogin} compact={compact} />;
};

ApiErrorHandler.propTypes = {
  error: PropTypes.object,
  loading: PropTypes.bool,
  children: PropTypes.node,
  onRetry: PropTypes.func,
  compact: PropTypes.bool,
  fallback: PropTypes.node
};

// ==============================|| EXPORTS ||============================== //

export { ApiErrorHandler, ApiErrorDisplay, getErrorConfig, ERROR_CONFIGS };
export default ApiErrorHandler;
