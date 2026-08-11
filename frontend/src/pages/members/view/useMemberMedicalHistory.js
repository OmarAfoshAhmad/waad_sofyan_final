import { useEffect, useMemo, useState } from 'react';
import api from 'utils/axios';
import { toArray } from './memberView.helpers';

/**
 * Fetches and merges a member's visits/claims/pre-authorizations into one
 * chronological "medical history" event list, plus the search/type/status
 * filter state for the medical history tab. Extracted out of
 * UnifiedMemberView so the fetch-once-per-tab-visit lifecycle and the
 * filtering logic aren't tangled with the rest of the page's state.
 *
 * @param {string|number} memberId
 * @param {boolean} enabled - only fetches once this becomes true (tab visited)
 */
export function useMemberMedicalHistory(memberId, enabled) {
  const [medicalHistory, setMedicalHistory] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [type, setType] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  useEffect(() => {
    if (memberId && enabled && !medicalHistory && !loading) {
      fetchMedicalHistory();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberId, enabled, medicalHistory, loading]);

  const fetchMedicalHistory = async () => {
    setLoading(true);
    setError(null);

    const sources = await Promise.allSettled([
      api.get(`/visits/member/${memberId}`),
      api.get(`/claims/member/${memberId}`),
      api.get(`/pre-authorizations/member/${memberId}`, { params: { page: 0, size: 100, sortBy: 'createdAt', sortDirection: 'DESC' } })
    ]);

    const [visitsResult, claimsResult, preAuthsResult] = sources;
    const visits = visitsResult.status === 'fulfilled' ? toArray(visitsResult.value) : [];
    const claims = claimsResult.status === 'fulfilled' ? toArray(claimsResult.value) : [];
    const preAuths = preAuthsResult.status === 'fulfilled' ? toArray(preAuthsResult.value) : [];

    const failures = sources.filter((item) => item.status === 'rejected');
    if (failures.length > 0) {
      setError('تعذر تحميل بعض مصادر السجل الطبي، وتم عرض البيانات المتاحة فقط.');
      console.warn('Partial medical history load failure:', failures);
    }

    const events = [
      ...visits.map((visit) => ({
        id: `visit-${visit.id}`,
        originalId: visit.id,
        type: 'visit',
        typeLabel: 'زيارة',
        iconType: 'visit',
        date: visit.visitDate || visit.createdAt,
        reference: visit.visitNumber || visit.id,
        provider: visit.providerName || visit.provider?.name || '-',
        description: visit.diagnosisDescription || visit.reason || visit.notes || 'زيارة طبية',
        status: visit.status,
        amount: null,
        path: `/visits/${visit.id}`
      })),
      ...claims.map((claim) => ({
        id: `claim-${claim.id}`,
        originalId: claim.id,
        type: 'claim',
        typeLabel: 'مطالبة',
        iconType: 'claim',
        date: claim.serviceDate || claim.claimDate || claim.createdAt,
        reference: claim.claimNumber || claim.referenceNumber || claim.id,
        provider: claim.providerName || claim.provider?.name || '-',
        description: claim.diagnosisDescription || claim.diagnosis || 'مطالبة طبية',
        status: claim.status,
        amount: claim.totalAmount ?? claim.claimedAmount ?? claim.approvedAmount,
        path: `/claims/${claim.id}/medical-review`
      })),
      ...preAuths.map((preAuth) => ({
        id: `preauth-${preAuth.id}`,
        originalId: preAuth.id,
        type: 'preauth',
        typeLabel: 'موافقة',
        iconType: 'preauth',
        date: preAuth.requestDate || preAuth.createdAt,
        reference: preAuth.preAuthNumber || preAuth.referenceNumber || preAuth.id,
        provider: preAuth.providerName || preAuth.provider?.name || '-',
        description: preAuth.serviceName || preAuth.diagnosisDescription || 'موافقة مسبقة',
        status: preAuth.status,
        amount: preAuth.requestedAmount ?? preAuth.approvedAmount,
        path: `/pre-approvals/${preAuth.id}`
      }))
    ].sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0));

    setMedicalHistory({ visits, claims, preAuths, events });
    setLoading(false);
  };

  const filteredEvents = useMemo(() => {
    const events = medicalHistory?.events || [];
    const query = search.trim().toLowerCase();

    return events.filter((event) => {
      const matchesType = type === 'ALL' || event.type === type;
      const matchesStatus = status === 'ALL' || event.status === status;
      const haystack = [event.reference, event.description, event.provider, event.status, event.typeLabel]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      const matchesSearch = !query || haystack.includes(query);
      return matchesType && matchesStatus && matchesSearch;
    });
  }, [medicalHistory?.events, search, status, type]);

  const statusOptions = useMemo(() => {
    const statuses = new Set((medicalHistory?.events || []).map((event) => event.status).filter(Boolean));
    return Array.from(statuses);
  }, [medicalHistory?.events]);

  const paginatedEvents = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredEvents.slice(start, start + rowsPerPage);
  }, [filteredEvents, page, rowsPerPage]);

  useEffect(() => {
    setPage(0);
  }, [search, status, type]);

  return {
    medicalHistory,
    loading,
    error,
    search,
    setSearch,
    type,
    setType,
    status,
    setStatus,
    statusOptions,
    filteredEvents,
    paginatedEvents,
    page,
    rowsPerPage,
    onPageChange: (event, newPage) => setPage(newPage),
    onRowsPerPageChange: (event) => {
      setRowsPerPage(parseInt(event.target.value, 10));
      setPage(0);
    }
  };
}
