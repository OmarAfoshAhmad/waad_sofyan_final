import { useState, useEffect, useCallback, useMemo } from 'react';
import axiosClient from 'utils/axios';

/**
 * Pre-Authorization Status Constants
 */
export const PREAUTH_STATUS = {
  PENDING: 'PENDING',
  UNDER_REVIEW: 'UNDER_REVIEW',
  APPROVAL_IN_PROGRESS: 'APPROVAL_IN_PROGRESS',
  APPROVED: 'APPROVED',
  ACKNOWLEDGED: 'ACKNOWLEDGED',
  REJECTED: 'REJECTED',
  NEEDS_CORRECTION: 'NEEDS_CORRECTION',
  EXPIRED: 'EXPIRED',
  CANCELLED: 'CANCELLED',
  USED: 'USED'
};

/**
 * All pre-auth statuses for filter dropdown
 */
export const ALL_PREAUTH_STATUSES = Object.values(PREAUTH_STATUS);

/**
 * Arabic labels for pre-auth statuses
 */
export const PREAUTH_STATUS_LABELS = {
  [PREAUTH_STATUS.PENDING]: 'معلقة',
  [PREAUTH_STATUS.UNDER_REVIEW]: 'قيد المراجعة',
  [PREAUTH_STATUS.APPROVAL_IN_PROGRESS]: 'جاري معالجة الموافقة',
  [PREAUTH_STATUS.APPROVED]: 'موافق عليها',
  [PREAUTH_STATUS.ACKNOWLEDGED]: 'تم الاطلاع',
  [PREAUTH_STATUS.REJECTED]: 'مرفوضة',
  [PREAUTH_STATUS.NEEDS_CORRECTION]: 'تحتاج تصحيح',
  [PREAUTH_STATUS.EXPIRED]: 'منتهية الصلاحية',
  [PREAUTH_STATUS.CANCELLED]: 'ملغاة',
  [PREAUTH_STATUS.USED]: 'مستخدمة'
};

/**
 * Helper to unwrap API response
 */
const unwrap = (response) => response.data?.data ?? response.data;

/**
 * Default filter state
 */
export const DEFAULT_FILTERS = {
  statuses: [], // Empty = all statuses
  memberSearch: '', // Text search on member name
  dateFrom: null, // Start date filter
  dateTo: null // End date filter
};

/**
 * usePreApprovalsReport Hook
 *
 * Fetches pre-approvals for operational reporting with client-side filtering.
 *
 * @param {Object} options
 * @param {number|null} options.employerId - Employer ID for filtering
 * @param {number|null} options.providerId - Provider ID for filtering
 * @param {Object} options.filters - Filter criteria
 * @returns {Object} Pre-approvals data, loading states, error, and utilities
 */
export const usePreApprovalsReport = ({ employerId, providerId, filters = DEFAULT_FILTERS, page = 0, size = 25 } = {}) => {
  const [preApprovals, setPreApprovals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [pagination, setPagination] = useState({
    page: 0,
    size: 100,
    totalElements: 0,
    totalPages: 0
  });

  /**
   * Fetch pre-approvals from API
   */
  const fetchPreApprovals = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        page,
        size,
        sortBy: 'createdAt',
        sortDirection: 'DESC'
      };

      if (employerId) {
        params.employerId = employerId;
      }

      if (providerId) {
        params.providerId = providerId;
      }

      if (filters.statuses?.length === 1) {
        params.status = filters.statuses[0];
      }

      if (filters.dateFrom) {
        params.dateFrom = filters.dateFrom;
      }

      if (filters.dateTo) {
        params.dateTo = filters.dateTo;
      }

      // Member/free-text filtering remains client-side for the current server page
      // until indexed joins are finalized. Provider scoping is enforced server-side.
      // ⚠️ FIXED: Use /v1/pre-authorizations to match Backend API
      const response = await axiosClient.get('/pre-authorizations', { params });
      const data = unwrap(response);

      // Handle different response formats
      const preApprovalsList = data?.items ?? data?.content ?? data ?? [];

      if (!Array.isArray(preApprovalsList)) {
        throw new Error('Invalid pre-approvals data format');
      }

      // Map pre-approvals to UI-safe model
      const mappedPreApprovals = preApprovalsList.map((pa) => ({
        id: pa.id,
        referenceNumber: pa.referenceNumber || `PA-${pa.id}`,
        memberName: pa.member?.fullName ?? pa.memberName ?? '—',
        employerName: pa.member?.employerOrganization?.name ?? pa.employerName ?? '—',
        providerName: pa.provider?.name ?? pa.providerName ?? '—',
        serviceName: pa.serviceName ?? pa.medicalService?.name ?? '—',
        status: pa.status,
        requestedAmount: parseFloat(pa.requestedAmount) || 0,
        approvedAmount: pa.approvedAmount != null ? parseFloat(pa.approvedAmount) : null,
        requestDate: pa.requestDate,
        validUntil: pa.validUntil,
        updatedAt: pa.updatedAt,
        // Keep raw for potential drill-down
        _raw: pa
      }));

      setPreApprovals(mappedPreApprovals);
      setPagination({
        page: data?.page ?? 0,
        size: data?.size ?? mappedPreApprovals.length,
        totalElements: data?.total ?? data?.totalElements ?? mappedPreApprovals.length,
        totalPages: data?.totalPages ?? 1
      });
    } catch (err) {
      console.error('❌ Failed to fetch pre-approvals:', err);
      setError(err.message || 'فشل في تحميل الموافقات المسبقة');
      setPreApprovals([]);
    } finally {
      setLoading(false);
    }
  }, [employerId, filters.dateFrom, filters.dateTo, filters.statuses, page, size]);

  /**
   * Initial fetch and refetch on employerId change
   */
  useEffect(() => {
    fetchPreApprovals();
  }, [fetchPreApprovals]);

  /**
   * Apply client-side filters
   */
  const filteredPreApprovals = useMemo(() => {
    let result = [...preApprovals];

    // Filter by provider (client-side)
    if (providerId) {
      result = result.filter((pa) => {
        const paProviderId = pa._raw?.provider?.id ?? pa._raw?.providerId;
        return paProviderId === providerId;
      });
    }

    // Filter by status (multi-select). A single status is already filtered by DB;
    // keep this for multi-status and defensive consistency.
    if (filters.statuses && filters.statuses.length > 0) {
      result = result.filter((pa) => filters.statuses.includes(pa.status));
    }

    // Filter by member name (text search)
    if (filters.memberSearch && filters.memberSearch.trim()) {
      const search = filters.memberSearch.trim().toLowerCase();
      result = result.filter((pa) => pa.memberName.toLowerCase().includes(search));
    }

    // Date range is applied by DB; keep local guard for current page consistency.
    if (filters.dateFrom) {
      const fromDate = new Date(filters.dateFrom);
      fromDate.setHours(0, 0, 0, 0);
      result = result.filter((pa) => {
        const paDate = new Date(pa.requestDate || pa.updatedAt);
        return paDate >= fromDate;
      });
    }

    // Filter by date range (to)
    if (filters.dateTo) {
      const toDate = new Date(filters.dateTo);
      toDate.setHours(23, 59, 59, 999);
      result = result.filter((pa) => {
        const paDate = new Date(pa.requestDate || pa.updatedAt);
        return paDate <= toDate;
      });
    }

    return result;
  }, [preApprovals, filters, providerId]);

  return {
    // Data
    preApprovals: filteredPreApprovals,
    allPreApprovals: preApprovals,
    totalCount: pagination.totalElements || filteredPreApprovals.length,
    totalFetched: preApprovals.length,

    // State
    loading,
    error,
    isEmpty: !loading && filteredPreApprovals.length === 0,

    // Pagination info (from API)
    pagination,

    // Actions
    refetch: fetchPreApprovals
  };
};

export default usePreApprovalsReport;
