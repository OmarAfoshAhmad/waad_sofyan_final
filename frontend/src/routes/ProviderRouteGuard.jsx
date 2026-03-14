import { Navigate, useLocation } from 'react-router-dom';
import { useMemo } from 'react';
import PropTypes from 'prop-types';

// project imports
import useAuth from 'hooks/useAuth';
import { AUTH_STATUS } from 'contexts/AuthContext';
import { isProviderRole } from 'constants/rbac';

// Inlined from deleted constants/providerSecurity
const PROVIDER_DEFAULT_ROUTE = '/provider/eligibility-check';
const isProviderUser = (user) => isProviderRole(user?.roles?.[0]);
const hasValidProviderBinding = (user) => !!user?.providerId;
const isRouteAllowedForProvider = (pathname) => pathname.startsWith('/provider');

// MUI imports for loader and error display
import { Box, CircularProgress, Typography, Paper, Alert, Button } from '@mui/material';
import { ErrorOutline as ErrorIcon } from '@mui/icons-material';

// ==============================|| LOADING COMPONENT ||============================== //

const ProviderLoader = () => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      gap: '1.0rem'
    }}
  >
    <CircularProgress size={48} color="primary" />
    <Typography variant="body1" color="text.secondary">
      جاري التحقق من صلاحيات مقدم الخدمة...
    </Typography>
  </Box>
);

// ==============================|| PROVIDER NOT LINKED ERROR ||============================== //

const ProviderNotLinkedPage = () => {
  const handleLogout = () => {
    window.location.href = '/login';
  };

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        bgcolor: 'grey.100',
        p: '1.0rem'
      }}
    >
      <Paper
        elevation={3}
        sx={{
          p: '2.0rem',
          maxWidth: '28.125rem',
          textAlign: 'center'
        }}
      >
        <ErrorIcon sx={{ fontSize: '4.0rem', color: 'error.main', mb: '1.0rem' }} />

        <Typography variant="h5" gutterBottom color="error">
          حساب مقدم الخدمة غير مكتمل
        </Typography>

        <Alert severity="error" sx={{ mb: '1.0rem', textAlign: 'right' }}>
          لم يتم ربط حسابك بمقدم خدمة. يرجى التواصل مع مدير النظام لإكمال إعداد الحساب.
        </Alert>

        <Typography variant="body2" color="text.secondary" sx={{ mb: '1.5rem' }}>
          Provider Account Incomplete: Your account is not linked to a provider entity.
        </Typography>

        <Button variant="contained" color="primary" onClick={handleLogout} fullWidth>
          العودة لتسجيل الدخول
        </Button>
      </Paper>
    </Box>
  );
};

// ==============================|| PROVIDER ROUTE GUARD - STABLE ||============================== //

/**
 * ProviderRouteGuard - Strict security guard for Provider Portal (Stable State)
 *
 * CANONICAL FIX (2026-01-16): Prevents Infinite Render Loop
 *
 * SECURITY RULES:
 * 1. PROVIDER users MUST have providerId (validated at login, but double-check here)
 * 2. PROVIDER users can ONLY access /provider/* routes
 * 3. Non-provider users are allowed through (handled by other guards)
 * 4. SUPER_ADMIN bypasses all checks
 *
 * THREE STATES (mutually exclusive):
 * 1. LOADING (authStatus === INITIALIZING) → Show loader
 * 2. FORBIDDEN (provider not linked) → Show error page (NO redirect loop!)
 * 3. ALLOWED → Render children
 *
 * @param {React.ReactNode} children - Component to render
 */
const ProviderRouteGuard = ({ children }) => {
  const { user, authStatus } = useAuth();
  const location = useLocation();

  // ═══════════════════════════════════════════════════════════════════════════
  // Compute guard state using useMemo (stable, no re-render triggers)
  // ═══════════════════════════════════════════════════════════════════════════
  const guardState = useMemo(() => {
    // STATE 1: Still loading - MUST wait
    if (authStatus === AUTH_STATUS.INITIALIZING) {
      return { status: 'loading' };
    }

    // STATE 2: Not authenticated
    if (authStatus === AUTH_STATUS.UNAUTHENTICATED || !user) {
      return { status: 'redirect', target: '/login' };
    }

    // Get user's primary role
    const userRole = user.roles?.[0] || null;

    // SUPER_ADMIN has unrestricted access
    if (userRole === 'SUPER_ADMIN') {
      return { status: 'allowed' };
    }

    // For PROVIDER users, enforce strict security
    if (isProviderUser(user)) {
      // Check provider binding - SHOW PAGE, NOT REDIRECT
      if (!hasValidProviderBinding(user)) {
        console.error('❌ [ProviderRouteGuard] PROVIDER user without providerId:', user.username);
        return { status: 'provider_not_linked' };
      }

      // Check if route is allowed for providers
      if (!isRouteAllowedForProvider(location.pathname)) {
        console.warn(`⚠️ [ProviderRouteGuard] Blocked access to: ${location.pathname}`);
        return { status: 'redirect', target: PROVIDER_DEFAULT_ROUTE };
      }
    }

    // Non-provider users trying to access provider routes
    // This is allowed (admins can access provider portal)
    return { status: 'allowed' };
  }, [authStatus, user, location.pathname]);

  // ═══════════════════════════════════════════════════════════════════════════
  // Render based on computed state (NO side effects in render)
  // ═══════════════════════════════════════════════════════════════════════════

  // STATE: Loading - show loader, DO NOT redirect
  if (guardState.status === 'loading') {
    return <ProviderLoader />;
  }

  // STATE: Provider not linked - show error page (NOT redirect - prevents loop!)
  if (guardState.status === 'provider_not_linked') {
    return <ProviderNotLinkedPage />;
  }

  // STATE: Redirect - safe to use Navigate (state is stable)
  if (guardState.status === 'redirect') {
    return <Navigate to={guardState.target} replace />;
  }

  // STATE: Allowed - render children
  return children;
};

ProviderRouteGuard.propTypes = {
  children: PropTypes.node
};

export default ProviderRouteGuard;
