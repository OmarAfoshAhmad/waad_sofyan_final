import { createContext, useEffect, useReducer, useCallback, useContext } from 'react';
/**
 * ⚠️ DEPRECATED - DO NOT USE IN NEW CODE
 *
 * JWTContext - Legacy JWT Authentication (Phase A/B)
 *
 * This file is kept for reference only. All new code should use:
 *   - AuthContext.jsx for session-based authentication
 *
 * AUDIT FIX (TASK B): Web frontend now uses session-only auth.
 * JWT support remains in backend for future mobile app integration.
 *
 * Migration Notes:
 * - Replace: import JWTContext from 'contexts/JWTContext';
 * - With:    import { AuthContext } from 'contexts/AuthContext';
 *
 * See: AUTH_RBAC_COMPLETION_REPORT.md for Phase C migration details
 */

import PropTypes from 'prop-types';
import { jwtDecode } from 'jwt-decode';

// reducer
import { LOGIN, LOGOUT } from 'contexts/auth-reducer/actions';
import authReducer from 'contexts/auth-reducer/auth';

// project imports
import Loader from 'components/Loader';
import axios from 'utils/axios';
import { useRBACStore } from 'api/rbac';
import { getToken, setToken, clearToken } from 'utils/token-storage';

// ==============================|| INITIAL STATE ||============================== //

const initialState = {
  isLoggedIn: false,
  isInitialized: false,
  user: null,
  roles: [],
  permissions: []
};

// ==============================|| TOKEN HELPERS ||============================== //

const verifyToken = (token) => {
  if (!token) return false;
  try {
    const decoded = jwtDecode(token);
    return decoded.exp > Date.now() / 1000;
  } catch (err) {
    console.error('❌ Token verification failed:', err);
    return false;
  }
};

const setSession = (token) => {
  if (token) {
    setToken(token);
    if (!axios.defaults.headers) axios.defaults.headers = {};
    if (!axios.defaults.headers.common) axios.defaults.headers.common = {};
    axios.defaults.headers.common.Authorization = `Bearer ${token}`;
    console.log('✅ Session token set');
  } else {
    clearToken();
    if (axios.defaults.headers?.common?.Authorization) {
      delete axios.defaults.headers.common.Authorization;
    }
    console.log('🗑️ Session token cleared');
  }
};

// ==============================|| CONTEXT ||============================== //

const JWTContext = createContext(null);

export const JWTProvider = ({ children }) => {
  const [state, dispatch] = useReducer(authReducer, initialState);

  // ==============================|| INITIALIZATION - FIXED ||============================== //

  useEffect(() => {
    const init = async () => {
      console.log('🔄 JWTContext: Starting initialization...');

      try {
        const token = getToken();

        if (token && verifyToken(token)) {
          console.log('✅ Valid token found, fetching user data...');
          setSession(token);

          const response = await axios.get('/auth/me');
          const userData = response.data.data;

          console.log('✅ User data fetched:', userData);

          // CRITICAL FIX: Initialize RBAC store with userData
          useRBACStore.getState().initialize(userData);
          console.log('✅ RBAC store initialized');

          dispatch({
            type: LOGIN,
            payload: {
              user: userData,
              roles: userData.roles || [],
              permissions: userData.permissions || []
            }
          });

          console.log('✅ JWTContext initialization complete');
        } else {
          console.warn('⚠️ No valid token found or token expired, skipping /auth/me to avoid 401 spam');

          // CRITICAL FIX: Still initialize RBAC with empty state
          useRBACStore.getState().initialize(null);

          dispatch({ type: LOGOUT });
        }
      } catch (err) {
        console.error('❌ JWTContext initialization failed:', err);

        // CRITICAL FIX: On error, clear everything safely
        setSession(null);
        useRBACStore.getState().clear();
        dispatch({ type: LOGOUT });
      }
    };

    init();
  }, []);

  // ==============================|| JWT EXPIRY MONITOR ||============================== //

  useEffect(() => {
    if (!state.isLoggedIn) return;

    const token = getToken();
    if (!token) return;

    try {
      const decoded = jwtDecode(token);
      if (decoded.exp) {
        const expiryTime = decoded.exp * 1000;
        const timeUntilExpiry = expiryTime - Date.now();

        if (timeUntilExpiry <= 0) {
          console.warn('⚠️ JWT already expired - Force logout');
          logout();
          return;
        }

        // Auto-logout when token expires
        const timerId = setTimeout(() => {
          console.warn('⚠️ JWT expired - Auto logout');
          alert('انتهت صلاحية الجلسة، يرجى تسجيل الدخول مرة أخرى');
          logout();
        }, timeUntilExpiry);

        return () => clearTimeout(timerId);
      }
    } catch (error) {
      console.error('Error monitoring JWT expiry:', error);
    }
  }, [state.isLoggedIn]);

  // ==============================|| SESSION EXPIRED EVENT LISTENER ||============================== //

  useEffect(() => {
    const handleSessionExpired = () => {
      if (state.isLoggedIn) {
        console.warn('⚠️ Session expired (401) - Force logout');
        alert('انتهت الجلسة، يرجى تسجيل الدخول مرة أخرى');
        logout();
      }
    };

    window.addEventListener('auth:session-expired', handleSessionExpired);
    return () => window.removeEventListener('auth:session-expired', handleSessionExpired);
  }, [state.isLoggedIn]);

  // ==============================|| LOGIN - FIXED ||============================== //

  const login = async (identifier, password) => {
    console.log('🔄 Login attempt for:', identifier);

    try {
      const response = await axios.post('/auth/login', {
        identifier,
        password
      });

      const { token, user: userData } = response.data.data;

      console.log('✅ Login successful:', userData);

      setSession(token);

      // CRITICAL FIX: Initialize RBAC store with fresh userData
      useRBACStore.getState().initialize(userData);
      console.log('✅ RBAC store initialized after login');

      dispatch({
        type: LOGIN,
        payload: {
          user: userData,
          roles: userData.roles || [],
          permissions: userData.permissions || []
        }
      });

      // Get redirect path based on role
      const redirectPath = getRedirectPath(userData.roles);
      console.log('✅ Login complete, redirecting to:', redirectPath);

      return redirectPath;
    } catch (err) {
      console.error('❌ Login failed:', err);
      throw err;
    }
  };

  // ==============================|| LOGOUT - FIXED ||============================== //

  const logout = () => {
    console.log('🔄 Logout initiated...');

    setSession(null);

    // CRITICAL FIX: Clear RBAC store
    useRBACStore.getState().clear();
    console.log('✅ RBAC store cleared');

    // Clear all storage
    clearToken();
    sessionStorage.clear();

    dispatch({ type: LOGOUT });

    // Hard redirect to login to ensure clean state
    console.log('🔄 Redirecting to /login...');
    window.location.href = '/login';
  };

  // ==============================|| REDIRECT LOGIC - FIXED ||============================== //

  /**
   * Get redirect path based on user role
   * CRITICAL FIX: Updated for new role names (Phase 1.5)
   * @param {string[]} roles - Array of user roles
   * @returns {string} - Redirect path
   */
  const getRedirectPath = useCallback((roles) => {
    if (!roles || roles.length === 0) {
      console.warn('⚠️ No roles found, redirecting to profile');
      return '/profile';
    }

    console.log('🔄 Determining redirect path for roles:', roles);

    // Priority order for roles (Phase 1.5 role names)
    if (roles.includes('ADMIN')) {
      console.log('✅ ADMIN role detected, redirecting to /dashboard');
      return '/dashboard';
    }
    if (roles.includes('INSURANCE_COMPANY')) {
      console.log('✅ INSURANCE_COMPANY role detected, redirecting to /dashboard');
      return '/dashboard';
    }
    if (roles.includes('EMPLOYER')) {
      console.log('✅ EMPLOYER role detected, redirecting to /members');
      return '/members';
    }
    if (roles.includes('MEDICAL_REVIEWER')) {
      console.log('✅ MEDICAL_REVIEWER role detected, redirecting to /approvals/dashboard');
      return '/approvals/dashboard';
    }

    // Default fallback
    console.warn('⚠️ No matching role, redirecting to /profile');
    return '/profile';
  }, []);

  // ==============================|| SIMPLIFIED RBAC HELPERS ||============================== //

  /**
   * Get user's primary role (simplified - each user has ONE role)
   * @returns {string|null}
   */
  const getPrimaryRole = () => {
    if (!state.roles || state.roles.length === 0) return null;
    return state.roles[0];
  };

  /**
   * Check if user's role matches one of the allowed roles
   * @param {string[]} allowedRoles - Array of allowed role names
   * @returns {boolean}
   */
  const hasRole = (allowedRoles) => {
    const primaryRole = getPrimaryRole();
    if (!primaryRole) return false;

    // PERMISSION FIX (2026-02-06): NO role-based bypasses
    // Check if primary role is in allowed list
    return allowedRoles.includes(primaryRole);
  };

  /**
   * Check if user is ADMIN
   * @returns {boolean}
   */
  const isAdmin = () => {
    return getPrimaryRole() === 'ADMIN';
  };

  /**
   * Check if user is SUPER_ADMIN
   * @returns {boolean}
   */
  const isSuperAdmin = () => {
    return getPrimaryRole() === 'SUPER_ADMIN';
  };

  // Show loader during initialization
  if (!state.isInitialized) {
    return <Loader />;
  }

  return (
    <JWTContext.Provider
      value={{
        ...state,
        login,
        logout,
        hasRole,
        getPrimaryRole,
        isAdmin,
        isSuperAdmin,
        getRedirectPath
      }}
    >
      {children}
    </JWTContext.Provider>
  );
};

JWTProvider.propTypes = {
  children: PropTypes.node
};

export default JWTContext;

// ==============================|| HOOK ||============================== //

/**
 * Simplified useAuth hook
 * @returns {Object} Auth context with simplified RBAC
 */
export const useAuth = () => {
  const context = useContext(JWTContext);
  if (!context) {
    throw new Error('useAuth must be used within JWTProvider');
  }
  return context;
};
