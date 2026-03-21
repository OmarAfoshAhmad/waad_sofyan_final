import axios from 'axios';
import { logError, getUserFriendlyMessage, ErrorType } from 'services/errorLogger';

// ==============================|| AXIOS CLIENT - CLEAN DOCKER VERSION ||============================== //

/**
 * Architecture:
 * Docker: VITE_API_URL=/api/v1
 * Nginx proxies /api → backend:8080
 * All requests must be relative (no host hardcoding)
 */

// 🔥 Simple & Correct — no normalization logic
const baseURL = import.meta.env.VITE_API_URL || '/api/v1';

const axiosServices = axios.create({
  baseURL: baseURL.replace(/\/+$/, ''), // remove trailing slash if any
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  },
  withCredentials: true // for session-based auth (JSESSIONID)
});

// ==============================|| REQUEST INTERCEPTOR ||============================== //

axiosServices.interceptors.request.use(
  (config) => {

    // 🔒 Ensure no duplicated /api/v1 prefix
    if (config.url) {
      if (config.url.startsWith('/api/v1/')) {
        config.url = config.url.replace(/^\/api\/v1\//, '/');
      }
    }

    // JWT fallback (if token exists)
    const token = localStorage.getItem('serviceToken');
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// ==============================|| RESPONSE INTERCEPTOR ||============================== //

axiosServices.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url;
    const errorData = error.response?.data;

    const classification = logError(error, {
      isAuthenticated: false,
      operation: error.config?.method?.toUpperCase(),
      component: 'axios-interceptor'
    });

    // ==========================================
    // 401 - Unauthorized
    // ==========================================
    if (status === 401) {
      const isLoginRequest =
        url?.includes('/auth/login') ||
        url?.includes('/auth/session/login');

      const isSessionCheck = url?.includes('/auth/session/me');

      if (isLoginRequest) {
        const backendMessage = errorData?.message || errorData?.error;
        error.userMessage = backendMessage || 'بيانات الدخول غير صحيحة';
        error.errorType = ErrorType.AUTHENTICATION;
        return Promise.reject(error);
      }

      if (!isSessionCheck) {
        console.warn('401 Unauthorized - Session expired');
      }

      localStorage.removeItem('serviceToken');
      sessionStorage.clear();

      window.dispatchEvent(new CustomEvent('auth:session-expired'));

      error.userMessage = getUserFriendlyMessage(error);
      error.errorType = classification.type;
    }

    // ==========================================
    // 400 - Validation / Bad Request
    // ==========================================
    if (status === 400) {
      // Prefer Arabic message from backend, fall back to English
      const backendMsg = errorData?.messageAr || errorData?.message || errorData?.error;
      error.userMessage = backendMsg || 'البيانات المدخلة غير صحيحة. الرجاء التحقق والمحاولة مرة أخرى.';
      error.errorType = classification.type;
    }

    // ==========================================
    // 403 - Forbidden
    // ==========================================
    if (status === 403) {
      const backendMessage =
        errorData?.message || errorData?.error || 'Access denied';

      window.dispatchEvent(
        new CustomEvent('api:forbidden', {
          detail: {
            url,
            method: error.config?.method?.toUpperCase(),
            message: backendMessage
          }
        })
      );

      error.userMessage = getUserFriendlyMessage(error);
      error.technicalMessage = backendMessage;
      error.errorType = ErrorType.PERMISSION_DENIED;
    }

    // ==========================================
    // 500+
    // ==========================================
    if (status >= 500) {
      error.userMessage = getUserFriendlyMessage(error);
      error.errorType = classification.type;
    }

    return Promise.reject(error);
  }
);

// ==============================|| FETCHERS ||============================== //

export const fetcher = async (args) => {
  const [url, config] = Array.isArray(args) ? args : [args];
  const res = await axiosServices.get(url, { ...config });
  return res.data;
};

export const fetcherPost = async (args) => {
  const [url, config] = Array.isArray(args) ? args : [args];
  const res = await axiosServices.post(url, { ...config });
  return res.data;
};

export default axiosServices;