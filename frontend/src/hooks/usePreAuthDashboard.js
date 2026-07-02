import { useState, useEffect, useCallback } from 'react';
import preAuthDashboardService from 'services/api/preauth-dashboard.service';

/**
 * Hook for PreAuthorization Dashboard
 * Auto-refreshes every 2 minutes
 */
export const usePreAuthDashboard = (trendDays = 30, topProviders = 10, autoRefresh = true) => {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchDashboard = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preAuthDashboardService.getDashboard(trendDays, topProviders);
      setDashboard(response || null);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل لوحة التحكم');
      console.error('Error fetching dashboard:', err);
    } finally {
      setLoading(false);
    }
  }, [trendDays, topProviders]);

  useEffect(() => {
    fetchDashboard();

    // Auto-refresh every 2 minutes
    if (autoRefresh) {
      const interval = setInterval(fetchDashboard, 120000);
      return () => clearInterval(interval);
    }
  }, [fetchDashboard, autoRefresh]);

  return {
    dashboard,
    loading,
    error,
    refresh: fetchDashboard
  };
};

/**
 * Hook for overall statistics
 */
export const usePreAuthStats = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preAuthDashboardService.getStats();
      setStats(response || {});
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل الإحصائيات');
      console.error('Error fetching stats:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  return { stats, loading, error, refresh: fetchStats };
};

/**
 * Hook for high priority queue
 */
export const useHighPriorityQueue = (limit = 10) => {
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchQueue = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preAuthDashboardService.getHighPriorityQueue(limit);
      setQueue(response || []);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل قائمة الأولويات');
      console.error('Error fetching queue:', err);
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchQueue();
  }, [fetchQueue]);

  return { queue, loading, error, refresh: fetchQueue };
};

/**
 * Hook for expiring soon alerts
 */
export const useExpiringSoon = (withinDays = 7, limit = 10) => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchItems = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preAuthDashboardService.getExpiringSoon(withinDays, limit);
      setItems(response || []);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل التنبيهات');
      console.error('Error fetching expiring items:', err);
    } finally {
      setLoading(false);
    }
  }, [withinDays, limit]);

  useEffect(() => {
    fetchItems();
  }, [fetchItems]);

  return { items, loading, error, refresh: fetchItems };
};
