import { useState, useEffect, useCallback, useMemo } from 'react';
import axiosClient from 'utils/axios';

/**
 * Claim Status Constants
 * Must match backend ClaimStatus enum exactly
 */
export const CLAIM_STATUS = {
  DRAFT: 'DRAFT',
  SUBMITTED: 'SUBMITTED',
  UNDER_REVIEW: 'UNDER_REVIEW',
  APPROVAL_IN_PROGRESS: 'APPROVAL_IN_PROGRESS',
  APPROVED: 'APPROVED',
  BATCHED: 'BATCHED',
  REJECTED: 'REJECTED',
  NEEDS_CORRECTION: 'NEEDS_CORRECTION',
  SETTLED: 'SETTLED'
};

/**
 * Operational claims report must show finalized outcomes only.
 */
export const FINAL_CLAIM_STATUSES = [CLAIM_STATUS.APPROVED, CLAIM_STATUS.BATCHED, CLAIM_STATUS.REJECTED, CLAIM_STATUS.SETTLED];

/**
 * All claim statuses for filter dropdown
 */
export const ALL_CLAIM_STATUSES = FINAL_CLAIM_STATUSES;

/**
 * Arabic labels for claim statuses
 */
export const CLAIM_STATUS_LABELS = {
  [CLAIM_STATUS.DRAFT]: 'مسودة',
  [CLAIM_STATUS.SUBMITTED]: 'مقدمة',
  [CLAIM_STATUS.UNDER_REVIEW]: 'قيد المراجعة',
  [CLAIM_STATUS.APPROVAL_IN_PROGRESS]: 'جاري معالجة الموافقة',
  [CLAIM_STATUS.APPROVED]: 'موافق عليها',
  [CLAIM_STATUS.BATCHED]: 'ضمن دفعة تسوية',
  [CLAIM_STATUS.REJECTED]: 'مرفوضة',
  [CLAIM_STATUS.NEEDS_CORRECTION]: 'تحتاج تصحيح',
  [CLAIM_STATUS.SETTLED]: 'تمت التسوية'
};

/**
 * Helper to unwrap API response
 */
const unwrap = (response) => response.data?.data ?? response.data;

/**
 * Default filter state
 */
export const DEFAULT_FILTERS = {
  statuses: FINAL_CLAIM_STATUSES, // Default = finalized statuses only
  memberSearch: '', // Text search on member name
  dateFrom: null, // Start date filter
  dateTo: null // End date filter
};

/**
 * useClaimsReport Hook
 *
 * Fetches claims for operational reporting with client-side filtering.
 *
 * @param {Object} options
 * @param {number|null} options.employerId - Employer ID for filtering
 * @param {Object} options.filters - Filter criteria
 * @returns {Object} Claims data, loading states, error, and utilities
 *
 * Architecture: Employer → Member → Claim
 * Data Source: GET /api/claims?employerId={id}&providerId={id}
 */
export const useClaimsReport = ({ employerId, providerId, filters = DEFAULT_FILTERS } = {}) => {
  const [claims, setClaims] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [pagination, setPagination] = useState({
    page: 0,
    size: 100,
    totalElements: 0,
    totalPages: 0
  });

  /**
   * Fetch claims from API
   */
  const fetchClaims = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        size: 9999 // Fetch all for client-side filtering
      };

      if (employerId) {
        params.employerId = employerId;
      }

      // Provider filtering is done client-side for better compatibility
      // ⚠️ FIXED: Use /v1/claims to match Backend API
      const response = await axiosClient.get('/claims', { params });
      const data = unwrap(response);

      // Handle different response formats
      const claimsList = data?.items ?? data?.content ?? data ?? [];

      if (!Array.isArray(claimsList)) {
        throw new Error('Invalid claims data format');
      }

      // Map claims to UI-safe model
      const mappedClaims = claimsList.map((claim) => ({
        id: claim.id,
        memberName: claim.member?.fullName ?? claim.memberName ?? '—',
        employerName: claim.member?.employerOrganization?.name ?? claim.employerName ?? '—',
        providerName: claim.provider?.name ?? claim.providerName ?? '—',
        status: claim.status,
        requestedAmount: parseFloat(claim.requestedAmount) || 0,
        approvedAmount: claim.approvedAmount != null ? parseFloat(claim.approvedAmount) : null,
        visitDate: claim.visitDate,
        updatedAt: claim.updatedAt,
        // Keep raw for potential drill-down
        _raw: claim
      }));

      setClaims(mappedClaims);
      setPagination({
        page: data?.page ?? 0,
        size: data?.size ?? mappedClaims.length,
        totalElements: data?.total ?? data?.totalElements ?? mappedClaims.length,
        totalPages: data?.totalPages ?? 1
      });
    } catch (err) {
      console.error('❌ Failed to fetch claims:', err);
      setError(err.message || 'فشل في تحميل المطالبات');
      setClaims([]);
    } finally {
      setLoading(false);
    }
  }, [employerId]);

  /**
   * Initial fetch and refetch on employerId/providerId change
   */
  useEffect(() => {
    fetchClaims();
  }, [fetchClaims]);

  /**
   * Apply client-side filters
   */
  const filteredClaims = useMemo(() => {
    let result = [...claims];

    // Filter by provider (client-side)
    if (providerId) {
      result = result.filter((claim) => {
        const claimProviderId = claim._raw?.provider?.id ?? claim._raw?.providerId;
        return claimProviderId === providerId;
      });
    }

    // ENFORCED: Operational report excludes non-final workflow statuses
    result = result.filter((claim) => FINAL_CLAIM_STATUSES.includes(claim.status));

    // Filter by status (multi-select)
    if (filters.statuses && filters.statuses.length > 0) {
      result = result.filter((claim) => filters.statuses.includes(claim.status));
    }

    // Filter by member name (text search)
    if (filters.memberSearch && filters.memberSearch.trim()) {
      const search = filters.memberSearch.trim().toLowerCase();
      result = result.filter((claim) => claim.memberName.toLowerCase().includes(search));
    }

    // Filter by date range (from)
    if (filters.dateFrom) {
      const fromDate = new Date(filters.dateFrom);
      fromDate.setHours(0, 0, 0, 0);
      result = result.filter((claim) => {
        const claimDate = new Date(claim.visitDate || claim.updatedAt);
        return claimDate >= fromDate;
      });
    }

    // Filter by date range (to)
    if (filters.dateTo) {
      const toDate = new Date(filters.dateTo);
      toDate.setHours(23, 59, 59, 999);
      result = result.filter((claim) => {
        const claimDate = new Date(claim.visitDate || claim.updatedAt);
        return claimDate <= toDate;
      });
    }

    return result;
  }, [claims, filters, providerId]);

  return {
    // Data
    claims: filteredClaims,
    allClaims: claims,
    totalCount: filteredClaims.length,
    totalFetched: claims.length,

    // State
    loading,
    error,
    isEmpty: !loading && filteredClaims.length === 0,

    // Pagination info (from API)
    pagination,

    // Actions
    refetch: fetchClaims
  };
};

export default useClaimsReport;
