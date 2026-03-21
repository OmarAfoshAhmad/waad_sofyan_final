/**
 * Session Authentication Service (Web)
 * Uses HttpOnly cookie session (JSESSIONID) only.
 */

import axiosClient from 'utils/axios';
import { clearToken } from 'utils/token-storage';

/**
 * Login with username/password
 * Uses session endpoint. Backend sets HttpOnly cookie.
 */
export const login = async (credentials) => {
  // Clear any legacy token artifacts before creating session.
  clearToken();

  const response = await axiosClient.post('/auth/session/login', credentials);
  const data = response.data;

  // Session login returns user info directly.
  return {
    status: data.status,
    data: data.data,
    message: data.message
  };
};

/**
 * Get current authenticated user
 * Session-only endpoint.
 */
export const me = async () => {
  try {
    const response = await axiosClient.get('/auth/session/me');
    return response.data;
  } catch (error) {
    if (error.response?.status === 401) {
      return { status: 'unauthenticated', data: null };
    }
    throw error;
  }
};

/**
 * Logout - invalidates both JWT and HTTP session
 */
export const logout = async () => {
  // Clear local token
  clearToken();

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
