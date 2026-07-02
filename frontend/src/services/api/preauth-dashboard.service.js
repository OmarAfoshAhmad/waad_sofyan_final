import axiosClient from 'utils/axios';

/**
 * PreAuthorization Analytics Dashboard Service
 */
const preAuthDashboardService = {
  /**
   * Get complete dashboard data
   */
  getDashboard: async (trendDays = 30, topProviders = 10) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard', {
      params: { trendDays, topProviders }
    });
    return response.data?.data || response.data;
  },

  /**
   * Get overall statistics
   */
  getStats: async () => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/stats');
    return response.data?.data || response.data;
  },

  /**
   * Get status distribution
   */
  getStatusDistribution: async () => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/status-distribution');
    return response.data?.data || response.data;
  },

  /**
   * Get high priority queue (EMERGENCY, URGENT)
   */
  getHighPriorityQueue: async (limit = 10) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/high-priority', {
      params: { limit }
    });
    return response.data?.data || response.data;
  },

  /**
   * Get expiring soon alerts
   */
  getExpiringSoon: async (withinDays = 7, limit = 10) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/expiring-soon', {
      params: { withinDays, limit }
    });
    return response.data?.data || response.data;
  },

  /**
   * Get trends (daily counts)
   */
  getTrends: async (days = 30) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/trends', {
      params: { days }
    });
    return response.data?.data || response.data;
  },

  /**
   * Get top providers by volume
   */
  getTopProviders: async (limit = 10) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/top-providers', {
      params: { limit }
    });
    return response.data?.data || response.data;
  },

  /**
   * Get recent activity
   */
  getRecentActivity: async (limit = 10) => {
    const response = await axiosClient.get('/pre-authorizations/dashboard/recent-activity', {
      params: { limit }
    });
    return response.data?.data || response.data;
  }
};

export default preAuthDashboardService;
