/**
 * System Monitoring API Service
 * Telegram alerts, automatic health checks and the external-monitor heartbeat.
 *
 * API Base: /api/v1/system/monitoring  (SUPER_ADMIN only, except the public heartbeat)
 */

import axiosClient from 'utils/axios';

const BASE_URL = '/system/monitoring';

const unwrap = (response) => response?.data?.data ?? response?.data;

const systemMonitoringService = {
  getHealth: async () => {
    const response = await axiosClient.get(`${BASE_URL}/health`);
    return unwrap(response);
  },

  getSettings: async () => {
    const response = await axiosClient.get(`${BASE_URL}/settings`);
    return unwrap(response);
  },

  updateSettings: async (settings) => {
    const response = await axiosClient.put(`${BASE_URL}/settings`, settings);
    return unwrap(response);
  },

  testTelegram: async () => {
    const response = await axiosClient.post(`${BASE_URL}/telegram/test`);
    return unwrap(response);
  }
};

export default systemMonitoringService;
