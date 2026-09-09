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
export const ALL_CLAIM_STATUSES = Object.values(CLAIM_STATUS);

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

const normalizeArabicSearch = (value) =>
  String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/[\u064B-\u065F\u0670]/g, '')
    .replace(/ـ/g, '');

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
 * Fetches claims for operational reporting with backend filtering where the API
 * supports it. The remaining client checks are compatibility guards.
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
        page: 1,
        size: 500,
        sortBy: 'serviceDate',
        sortDir: 'desc'
      };

      if (employerId) {
        params.employerId = employerId;
      }
      if (providerId) params.providerId = providerId;
      if (filters.memberSearch?.trim()) params.search = filters.memberSearch.trim();
      if (filters.dateFrom) params.dateFrom = filters.dateFrom;
      if (filters.dateTo) params.dateTo = filters.dateTo;

      const selectedStatuses = filters.statuses?.length ? filters.statuses : FINAL_CLAIM_STATUSES;
      const responses = await Promise.all(
        selectedStatuses.map((status) =>
          axiosClient.get('/claims', {
            params: { ...params, status }
          })
        )
      );
      const pages = responses.map(unwrap);
      const claimsList = pages.flatMap((data) => data?.items ?? data?.content ?? data ?? []);

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
        claimNumber: claim.paperReference || claim.claimNumber || claim.id,
        paperReference: claim.paperReference,
        serviceDate: claim.serviceDate,
        visitDate: claim.serviceDate,
        updatedAt: claim.updatedAt,
        // Keep raw for potential drill-down
        _raw: claim
      }));

      setClaims(mappedClaims);
      const firstPage = pages[0] || {};
      setPagination({
        page: firstPage?.page ?? 0,
        size: firstPage?.size ?? mappedClaims.length,
        totalElements: pages.reduce((sum, data) => sum + (data?.total ?? data?.totalElements ?? 0), 0) || mappedClaims.length,
        totalPages: Math.max(...pages.map((data) => data?.totalPages ?? 1), 1)
      });
    } catch (err) {
      console.error('❌ Failed to fetch claims:', err);
      setError(err.message || 'فشل في تحميل المطالبات');
      setClaims([]);
    } finally {
      setLoading(false);
    }
  }, [employerId, providerId, filters.memberSearch, filters.dateFrom, filters.dateTo, filters.statuses]);

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

    const selectedStatuses = filters.statuses?.length ? filters.statuses : FINAL_CLAIM_STATUSES;
    result = result.filter((claim) => selectedStatuses.includes(claim.status));

    // Filter by member name (text search)
    if (filters.memberSearch && filters.memberSearch.trim()) {
      const search = normalizeArabicSearch(filters.memberSearch);
      result = result.filter((claim) =>
        [
          claim.memberName,
          claim.claimNumber,
          claim.paperReference,
          claim._raw?.claimBatchCode,
          claim._raw?.memberNationalNumber,
          claim._raw?.memberCardNumber,
          claim._raw?.employeeNumber
        ]
          .map(normalizeArabicSearch)
          .some((value) => value.includes(search))
      );
    }

    // Filter by date range (from)
    if (filters.dateFrom) {
      const fromDate = new Date(filters.dateFrom);
      fromDate.setHours(0, 0, 0, 0);
      result = result.filter((claim) => {
        if (!claim.serviceDate) return false;
        const claimDate = new Date(claim.serviceDate);
        return claimDate >= fromDate;
      });
    }

    // Filter by date range (to)
    if (filters.dateTo) {
      const toDate = new Date(filters.dateTo);
      toDate.setHours(23, 59, 59, 999);
      result = result.filter((claim) => {
        if (!claim.serviceDate) return false;
        const claimDate = new Date(claim.serviceDate);
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
