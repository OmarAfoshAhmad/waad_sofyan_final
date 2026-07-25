import { useState, useEffect, useCallback } from 'react';
import { getDashboardSummary } from 'services/api/dashboard.service';
import { useEmployerFilter } from 'contexts/EmployerFilterContext';

/**
 * Hook for fetching dashboard summary statistics
 *
 * Uses dedicated dashboard endpoint: GET /api/dashboard/summary
 * All calculations done server-side using JPQL aggregations.
 * Supports employer filter.
 *
 * @returns {Object} { summary, loading, error, refresh }
 */
import useAuth from 'hooks/useAuth';

export const useDashboardStats = (options = {}) => {
  const { user } = useAuth();
  const isAuthenticated = !!user;
  const enabled = options.enabled !== false && isAuthenticated;
  const { silentOnForbidden = true } = options;
  const { selectedEmployerId } = useEmployerFilter();
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);

  const fetchSummary = useCallback(async () => {
    if (!enabled) {
      setSummary(null);
      setError(null);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const data = await getDashboardSummary(selectedEmployerId);
      setSummary(data);
    } catch (err) {
      if (silentOnForbidden && err?.response?.status === 403) {
        setSummary(null);
        setError(null);
        return;
      }

      const errorMessage = err.response?.data?.message || err.message || 'فشل تحميل إحصائيات لوحة التحكم';
      setError(errorMessage);
      console.error('Error fetching dashboard summary:', err);
    } finally {
      setLoading(false);
    }
  }, [enabled, selectedEmployerId, silentOnForbidden]);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  return {
    summary,
    loading,
    error,
    refresh: fetchSummary
  };
};
