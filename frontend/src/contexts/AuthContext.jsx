/**
 * AuthContext - Simplified Session-Based Authentication
 * Enterprise Mode - VPN-based Internal System
 *
 * SIMPLIFIED APPROACH:
 * - State: { user: null | User }
 * - Init: Call /session/me once, set user, done
 * - NO redirects
 * - NO complex state machines
 * - Router handles navigation
 *
 * PRODUCTION STABILIZATION (2026-01-13):
 * - Added AUTH_STATUS enum for guards
 * - Added authStatus to context for proper lifecycle handling
 */

import PropTypes from 'prop-types';
import { createContext, useEffect, useState, useContext } from 'react';

// Project imports
import authService from 'services/api/auth.service';
import { useRBACStore } from 'api/rbac';
import { openSnackbar } from 'api/snackbar';
import { getToken, clearToken } from 'utils/token-storage';

// ==============================|| AUTH STATUS ENUM ||============================== //

/**
 * Authentication status states
 * Used by AuthGuard and GuestGuard for proper lifecycle handling
 */
export const AUTH_STATUS = {
  INITIALIZING: 'INITIALIZING',
  AUTHENTICATED: 'AUTHENTICATED',
  UNAUTHENTICATED: 'UNAUTHENTICATED'
};

// Simple state - just user data
const initialState = {
  user: null
};

const SET_USER = 'SET_USER';
const CLEAR_USER = 'CLEAR_USER';

// Context
const AuthContext = createContext(null);

// Provider
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [authStatus, setAuthStatus] = useState(AUTH_STATUS.INITIALIZING);

  // ============================================================================
  // SESSION LOGIC (Inactivity Timer & 401 Handling)
  // ============================================================================

  const [lastActivity, setLastActivity] = useState(Date.now());
  const TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

  // 1. Activity Listener (throttled)
  useEffect(() => {
    // Only track if authenticated
    if (authStatus !== AUTH_STATUS.AUTHENTICATED) return;

    let lastUpdate = Date.now();

    const handleActivity = () => {
      const now = Date.now();
      // Update max once every 5 seconds to reduce state updates
      if (now - lastUpdate > 5000) {
        setLastActivity(now);
        lastUpdate = now;
      }
    };

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach((event) => window.addEventListener(event, handleActivity));

    return () => {
      events.forEach((event) => window.removeEventListener(event, handleActivity));
    };
  }, [authStatus]);

  // 2. Inactivity Check Interval
  useEffect(() => {
    if (authStatus !== AUTH_STATUS.AUTHENTICATED) return;

    const intervalId = setInterval(() => {
      if (Date.now() - lastActivity > TIMEOUT_MS) {
        console.warn('⚠️ Session timeout due to inactivity');
        openSnackbar({
          message: 'انتهت الجلسة بسبب عدم النشاط',
          alert: { color: 'warning' }
        });
        logout(); // Logout user
      }
    }, 60000); // Check every minute

    return () => clearInterval(intervalId);
  }, [authStatus, lastActivity]); // Dependencies ensure fresh state access

  // 3. Handle 401 Unauthorized from Axios
  useEffect(() => {
    const handleUnauthorized = () => {
      // Only if we think we are logged in
      if (authStatus === AUTH_STATUS.AUTHENTICATED) {
        console.warn('⚠️ Session expired (401) - Force Logout');
        openSnackbar({
          message: 'انتهت الجلسة، يرجى تسجيل الدخول مرة أخرى',
          alert: { color: 'error' }
        });

        // Force clean local state without calling backend (backend already said 401)
        setUser(null);
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
        useRBACStore.getState().clear();

        // 🔒 CRITICAL: Redirect to login after cleanup
        setTimeout(() => {
          window.location.href = '/login';
        }, 1000); // Small delay to show snackbar message
      }
    };

    window.addEventListener('auth:session-expired', handleUnauthorized);
    return () => window.removeEventListener('auth:session-expired', handleUnauthorized);
  }, [authStatus]);

  // 4. JWT Token Expiry Monitor (if JWT is used)
  useEffect(() => {
    if (authStatus !== AUTH_STATUS.AUTHENTICATED || !user) return;

    // Try to get JWT expiry from user object or check for serviceToken
    const token = getToken();
    if (!token) return; // Session-based auth, skip JWT monitoring

    try {
      // Decode JWT to get expiry
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      const decoded = JSON.parse(jsonPayload);

      if (decoded.exp) {
        const expiryTime = decoded.exp * 1000; // Convert to milliseconds
        const timeUntilExpiry = expiryTime - Date.now();

        if (timeUntilExpiry <= 0) {
          // Already expired
          console.warn('⚠️ JWT already expired');
          logout();
          return;
        }

        // Set timer to auto-logout when token expires
        const timerId = setTimeout(() => {
          console.warn('⚠️ JWT expired - Auto logout');
          openSnackbar({
            message: 'انتهت صلاحية الجلسة، يرجى تسجيل الدخول مرة أخرى',
            alert: { color: 'warning' }
          });
          logout();
        }, timeUntilExpiry);

        return () => clearTimeout(timerId);
      }
    } catch (error) {
      console.error('Error decoding JWT:', error);
    }
  }, [authStatus, user]);

  /**
   * Multi-tab logout synchronization
   */
  useEffect(() => {
    const channel = new BroadcastChannel('tba-auth-channel');

    channel.onmessage = (event) => {
      if (event.data?.type === 'LOGOUT') {
        console.info('🔄 Logout detected in another tab');
        setUser(null);
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
        useRBACStore.getState().clear();
        window.location.href = '/login';
      }
    };

    return () => {
      channel.close();
    };
  }, []);

  /**
   * Initialize auth state on app startup
   * SIMPLIFIED: Call /session/me once, set user, done
   */
  useEffect(() => {
    const init = async () => {
      // PRODUCTION STABILIZATION: Silent init - no console noise
      try {
        // Stop 401 spam: only try to fetch user if we have a token or session hint
        const token = getToken();
        if (!token) {
          setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
          return;
        }

        const response = await authService.me();

        if (response.status === 'success' && response.data) {
          setUser(response.data);
          setAuthStatus(AUTH_STATUS.AUTHENTICATED);
          useRBACStore.getState().initialize(response.data);
          console.info('✅ Session restored:', response.data.username);
        } else {
          // Expected: no session means user needs to login
          setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
        }
      } catch (error) {
        // Expected: 401 or network issue means user needs to login
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
      }
    };

    init();
  }, []);

  /**
   * Login
   */
  const login = async (credentials) => {
    const response = await authService.login(credentials);

    if (response.status === 'success' && response.data) {
      setUser(response.data);
      setAuthStatus(AUTH_STATUS.AUTHENTICATED);
      useRBACStore.getState().initialize(response.data);
      return response.data;
    } else {
      throw new Error('Login failed');
    }
  };

  /**
   * Logout
   */
  const logout = async () => {
    try {
      await authService.logout();
    } catch (error) {
      console.warn('Logout API failed (likely already expired)', error);
    }

    // 🔒 CRITICAL: Clean ALL auth data
    setUser(null);
    setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
    useRBACStore.getState().clear();

    // Clear JWT token if exists (for hybrid auth)
    clearToken();
    sessionStorage.clear();

    // Notify other tabs
    const channel = new BroadcastChannel('tba-auth-channel');
    channel.postMessage({ type: 'LOGOUT' });
    channel.close();

    // 🔒 CRITICAL: Redirect to login
    window.location.href = '/login';
  };

  /**
   * Refresh user data
   */
  const refreshUser = async () => {
    try {
      const response = await authService.me();

      if (response.status === 'success' && response.data) {
        setUser(response.data);
        setAuthStatus(AUTH_STATUS.AUTHENTICATED);
        useRBACStore.getState().initialize(response.data);
      } else {
        setUser(null);
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
        useRBACStore.getState().clear();
      }
    } catch (error) {
      setUser(null);
      setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
      useRBACStore.getState().clear();
    }
  };

  // NO LOADER - always render immediately
  return (
    <AuthContext.Provider
      value={{
        user,
        authStatus,
        login,
        logout,
        refreshUser
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

AuthProvider.propTypes = {
  children: PropTypes.node
};

export default AuthContext;
export { AuthContext };

// ==============================|| HOOK ||============================== //

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
