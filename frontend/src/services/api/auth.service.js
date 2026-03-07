/**
 * Hybrid Authentication Service
 * Supports both JWT and Session-based authentication
 *
 * JWT Mode: Stores token in localStorage, sends Authorization header
 * Session Mode: Uses HttpOnly cookies (JSESSIONID)
 *
 * CODESPACES FIX: Using JWT mode for proper port forwarding support
 */

import axiosClient from 'utils/axios';

/**
 * Login with username/password
 * Uses JWT endpoint for token-based auth (works better with port forwarding)
 */
export const login = async (credentials) => {
  const response = await axiosClient.post('/auth/login', credentials);
  // Backend returns ApiResponse<LoginResponse> with token
  const data = response.data;

  // Store JWT token for subsequent requests
  if (data?.data?.token) {
    localStorage.setItem('serviceToken', data.data.token);
    console.log('✅ JWT token stored');
  }

  // Return user info (adapt from LoginResponse format)
  return {
    status: data.status,
    data: data.data?.user || data.data,
    message: data.message
  };
};

/**
 * Get current authenticated user
 * Tries JWT /me first, falls back to session /me
 */
export const me = async () => {
  try {
    // Try JWT endpoint first
    const response = await axiosClient.get('/auth/me');
    return response.data;
  } catch (error) {
    // If 401, try session endpoint as fallback
    if (error.response?.status === 401) {
      try {
        const sessionResponse = await axiosClient.get('/auth/session/me');
        return sessionResponse.data;
      } catch (sessionError) {
        return { status: 'unauthenticated', data: null };
      }
    }
    // Re-throw other errors (network, 500, etc.)
    throw error;
  }
};

/**
 * Logout - invalidates both JWT and HTTP session
 */
export const logout = async () => {
  // Clear local token
  localStorage.removeItem('serviceToken');

  // Clear backend session
  try {
    const response = await axiosClient.post('/auth/session/logout');
    return response.data;
  } catch (error) {
    // Ignore logout errors - token is already cleared
    return { status: 'success', message: 'Logged out' };
  }
};

/**
 * Check if user is authenticated
 * Tries to fetch current user - if succeeds, session is valid
 */
export const isAuthenticated = async () => {
  try {
    const response = await me();
    return response.status === 'success';
  } catch (error) {
    return false;
  }
};

/**
 * Get public password reset config.
 * Returns: { method: 'TOKEN' | 'OTP', tokenExpiryMinutes, otpExpiryMinutes, otpLength }
 */
export const getPasswordResetConfig = async () => {
  const response = await axiosClient.get('/auth/password-reset-config');
  return response.data?.data || { method: 'TOKEN', tokenExpiryMinutes: 60, otpExpiryMinutes: 10, otpLength: 6 };
};

/**
 * Request password reset link (token flow).
 */
export const requestPasswordResetToken = async (email) => {
  const response = await axiosClient.post('/auth/token/forgot-password', { email });
  return response.data;
};

/**
 * Reset password using secure token flow.
 */
export const resetPasswordWithToken = async (token, newPassword, confirmPassword) => {
  const response = await axiosClient.post('/auth/token/reset-password', {
    token,
    newPassword,
    confirmPassword
  });
  return response.data;
};

/**
 * Request OTP for password reset (legacy flow).
 */
export const requestPasswordResetOtp = async (email) => {
  const response = await axiosClient.post('/auth/forgot-password', { email });
  return response.data;
};

/**
 * Reset password using OTP flow.
 */
export const resetPasswordWithOtp = async (email, otp, newPassword) => {
  const response = await axiosClient.post('/auth/reset-password', {
    email,
    otp,
    newPassword
  });
  return response.data;
};

// Export as default for backward compatibility
export default {
  login,
  me,
  logout,
  isAuthenticated,
  getPasswordResetConfig,
  requestPasswordResetToken,
  resetPasswordWithToken,
  requestPasswordResetOtp,
  resetPasswordWithOtp
};
