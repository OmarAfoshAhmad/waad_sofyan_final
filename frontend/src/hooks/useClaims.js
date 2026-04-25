import { useState, useEffect, useCallback } from 'react';
import { claimsService } from 'services/api';

export const useClaimsList = (initialParams = { page: 0, size: 10 }) => {
  const [data, setData] = useState({ content: [], totalElements: 0, page: 0, size: 10 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [params, setParams] = useState(initialParams);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await claimsService.getAll(params);
      // Defensive: ensure result has expected shape
      setData({
        content: Array.isArray(result?.content) ? result.content : Array.isArray(result) ? result : [],
        totalElements: result?.totalElements ?? result?.total ?? 0,
        page: result?.page ?? params.page,
        size: result?.size ?? params.size
      });
    } catch (err) {
      setError(err.message || 'فشل تحميل المطالبات');
      // Set safe default on error
      setData({ content: [], totalElements: 0, page: params.page, size: params.size });
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    data,
    loading,
    error,
    params,
    setParams,
    refresh: fetchData
  };
};

export const useClaimDetails = (id) => {
  const [claim, setClaim] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchClaim = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const result = await claimsService.getById(id);
      setClaim(result);
    } catch (err) {
      setError(err.message || 'فشل تحميل تفاصيل المطالبة');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchClaim();
  }, [fetchClaim]);

  return {
    claim,
    loading,
    error,
    refresh: fetchClaim
  };
};

export const useCreateClaim = () => {
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

  const create = async (data) => {
    setCreating(true);
    setError(null);
    try {
      const result = await claimsService.create(data);
      return { success: true, data: result };
    } catch (err) {
      const errorMsg = err.message || 'فشل إنشاء المطالبة';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setCreating(false);
    }
  };

  return { create, creating, error };
};

export const useUpdateClaim = () => {
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(null);

  const update = async (id, data) => {
    setUpdating(true);
    setError(null);
    try {
      const result = await claimsService.update(id, data);
      return { success: true, data: result };
    } catch (err) {
      const errorMsg = err.message || 'فشل تحديث المطالبة';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setUpdating(false);
    }
  };

  return { update, updating, error };
};

export const useDeleteClaim = () => {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);

  const remove = async (id, reason) => {
    setDeleting(true);
    setError(null);
    try {
      await claimsService.remove(id, reason);
      return { success: true };
    } catch (err) {
      const errorMsg = err.message || 'فشل حذف المطالبة';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setDeleting(false);
    }
  };

  return { remove, deleting, error };
};
