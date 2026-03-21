import { lazy } from 'react';

// project imports
import AuthLayout from 'layout/Auth';
import Loadable from 'components/Loadable';

// jwt auth pages
const JwtAuthLogin = Loadable(lazy(() => import('pages/auth/jwt/login')));
const JwtAuthRegister = Loadable(lazy(() => import('pages/auth/jwt/register')));
const JwtAuthForgotPassword = Loadable(lazy(() => import('pages/auth/jwt/forgot-password')));
const JwtAuthResetPassword = Loadable(lazy(() => import('pages/auth/jwt/reset-password')));
const JwtAuthCodeVerification = Loadable(lazy(() => import('pages/auth/jwt/code-verification')));
const JwtAuthCheckMail = Loadable(lazy(() => import('pages/auth/jwt/check-mail')));

// ==============================|| AUTH ROUTING - FIXED ||============================== //

/**
 * CRITICAL FIX:
 * - Remove nested conditional path structure
 * - Make /login a DIRECT public route (no nesting under JWT check)
 * - Ensure login is ALWAYS accessible at /login
 */
const LoginRoutes = {
  path: '/',
  element: <AuthLayout />,
  children: [
    // PUBLIC AUTH ROUTES (NO RouteGuard)
    {
      index: true,
      element: <JwtAuthLogin />
    },
    {
      path: 'login',
      element: <JwtAuthLogin />
    },
    {
      path: 'register',
      element: <JwtAuthRegister />
    },
    {
      path: 'forgot-password',
      element: <JwtAuthForgotPassword />
    },
    {
      path: 'reset-password',
      element: <JwtAuthResetPassword />
    },
    {
      path: 'code-verification',
      element: <JwtAuthCodeVerification />
    },
    {
      path: 'check-mail',
      element: <JwtAuthCheckMail />
    }
  ]
};

export default LoginRoutes;
