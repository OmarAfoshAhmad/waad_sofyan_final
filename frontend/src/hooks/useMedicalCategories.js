import { useEffect, useState, useCallback } from 'react';
import { getMedicalCategories, getMedicalCategoryById, getAllMedicalCategories } from 'services/api/medical-categories.service';

/**
 * Hook for fetching paginated medical categories list
 * @param {Object} initialParams - Initial query parameters
 * @returns {Object} { data, loading, error, params, setParams, refresh }
 */
export const useMedicalCategoriesList = (initialParams = {}) => {
  const [params, setParams] = useState({
    page: 1,
    size: 20,
    sortBy: 'createdAt',
    sortDir: 'desc',
    search: '',
    ...initialParams
  });

  const [data, setData] = useState({
    items: [],
    total: 0,
    page: 1,
    size: 20
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getMedicalCategories(params);

      // Defensive: handle multiple response shapes
      const paginationData = response?.items ? response : response;
      const items = Array.isArray(paginationData?.items) ? paginationData.items : Array.isArray(paginationData) ? paginationData : [];

      setData({
        items,
        total: paginationData?.total ?? paginationData?.totalElements ?? 0,
        page: paginationData?.page ?? params.page,
        size: paginationData?.size ?? params.size
      });
    } catch (err) {
      console.error('[useMedicalCategories] Failed to load categories list:', err);
      setError(err);
      setData({ items: [], total: 0, page: params.page, size: params.size });
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    params,
    setParams,
    refresh
  };
};

/**
 * Hook for fetching single medical category details
 * @param {number} id - Category ID
 * @returns {Object} { data, loading, error, refresh }
 */
export const useMedicalCategoryDetails = (id) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(!!id); // start loading immediately if id provided
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (!id) return;

    try {
      setLoading(true);
      setError(null);
      const response = await getMedicalCategoryById(id);
      setData(response);
    } catch (err) {
      console.error('[useMedicalCategories] Failed to load category details:', err);
      setError(err);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    refresh
  };
};

/**
 * Hook for fetching all medical categories (for dropdowns)
 * @returns {Object} { data, loading, error, refresh }
 */
export const useAllMedicalCategories = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getAllMedicalCategories();
      setData(Array.isArray(response) ? response : []);
    } catch (err) {
      console.error('[useMedicalCategories] Failed to load all categories:', err);
      setError(err);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    refresh
  };
};
