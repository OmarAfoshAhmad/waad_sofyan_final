/**
 * ProviderPortalGuard
 *
 * Wraps provider portal routes (via React Router v6 layout route).
 * When PROVIDER_PORTAL_ENABLED feature flag is disabled, displays a
 * friendly "not available" page instead of rendering child routes.
 *
 * Internal staff (SUPER_ADMIN, ADMIN, DATA_ENTRY) always bypass this guard.
 *
 * Usage in routes:
 *   { path: 'provider', element: <ProviderPortalGuard><Outlet /></ProviderPortalGuard>, children: [...] }
 */

import { Box, Typography, Alert } from '@mui/material';
import { LocalHospital as LocalHospitalIcon } from '@mui/icons-material';
import useSystemConfig from 'hooks/useSystemConfig';
import useAuth from 'hooks/useAuth';

const STAFF_ROLES = ['SUPER_ADMIN', 'ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'ACCOUNTANT'];

const ProviderPortalGuard = ({ children }) => {
  const { flags, loading } = useSystemConfig();
  const { user } = useAuth();

  // Internal staff always bypass
  const isStaff = user?.roles?.some((r) => STAFF_ROLES.includes(r));
  if (isStaff) return children;

  // While fetching config, render children optimistically (defaults are safe — flag defaults to false)
  if (loading) return null;

  if (!flags.PROVIDER_PORTAL_ENABLED) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          gap: 3,
          p: 4
        }}
      >
        <LocalHospitalIcon sx={{ fontSize: 72, color: 'text.disabled' }} />

        <Typography variant="h4" fontWeight={700} color="text.secondary" align="center">
          بوابة مقدم الخدمة غير متاحة حالياً
        </Typography>

        <Alert severity="info" sx={{ maxWidth: 520 }}>
          تعتمد الشركة حالياً على نظام <strong>الدفعات الشهرية</strong> كمسار رئيسي لإدخال المطالبات.
          سيتم إعلامكم عند تفعيل البوابة المباشرة.
        </Alert>

        <Typography variant="body2" color="text.disabled" align="center">
          للاستفسار، تواصل مع مسؤول النظام.
        </Typography>
      </Box>
    );
  }

  return children;
};

export default ProviderPortalGuard;
