/**
 * Auth Service - Token Refresh Enhancement
 *
 * إضافة دالة لتحديث JWT Token بدون الحاجة لتسجيل خروج/دخول
 *
 * USE CASE:
 * - عند تحديد صلاحيات جديدة للمستخدم من لوحة الإدارة
 * - يمكن استدعاء refreshToken() لتحديث الصلاحيات فوراً
 * - لا حاجة للخروج والدخول مرة أخرى
 */

import axios from 'axios';
import { useState } from 'react';

const API_URL = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '/api/v1';

/**
 * تحديث JWT Token مع الصلاحيات الجديدة
 *
 * @returns {Promise<Object>} البيانات الجديدة للمستخدم مع Token محدث
 * @throws {Error} إذا فشل التحديث
 */
export const refreshToken = async () => {
  try {
    // Get current token from localStorage
    const currentToken = localStorage.getItem('token');

    if (!currentToken) {
      throw new Error('No token found. Please login first.');
    }

    // Call refresh-token endpoint
    const response = await axios.post(
      `${API_URL}/auth/refresh-token`,
      {},
      {
        headers: {
          Authorization: `Bearer ${currentToken}`
        }
      }
    );

    const { token, user } = response.data.data;

    // Update localStorage with new token and user data
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));

    return { token, user };
  } catch (error) {

    if (error.response?.status === 401) {
      // Token expired or invalid - redirect to login
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    throw error;
  }
};

/**
 * مثال على استخدام refreshToken في React Component
 */
export const useTokenRefresh = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const refresh = async () => {
    setLoading(true);
    setError(null);

    try {
      const { user } = await refreshToken();

      // Update app state with new user data
      // This depends on your state management (Redux, Context, etc.)
      // Example: dispatch(setUser(user));

      return user;
    } catch (err) {
      setError(err.message);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return { refresh, loading, error };
};

/**
 * INTEGRATION EXAMPLES REMOVED FOR BUILD SAFETY
 * The following examples contained JSX which is not allowed in .js files by Vite.
 * Please refer to project documentation for usage examples.
 */

// ProviderEditIntegration example removed

// AutoTokenRefresh example removed

/**
 * TRIGGER EXAMPLE: كيفية إطلاق حدث تغيير الصلاحيات
 */
export const triggerPermissionsChanged = () => {
  const event = new CustomEvent('permissions-changed', {
    detail: {
      timestamp: new Date().toISOString(),
      source: 'admin-panel'
    }
  });

  window.dispatchEvent(event);
};

// RefreshTokenButton example removed

export default {
  refreshToken,
  useTokenRefresh,
  triggerPermissionsChanged
};
