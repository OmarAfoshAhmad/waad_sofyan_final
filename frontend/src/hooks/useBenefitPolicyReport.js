import { useState, useEffect, useCallback, useMemo } from 'react';
import axiosClient from 'utils/axios';

/**
 * Helper to unwrap API response
 */
const unwrap = (response) => response.data?.data ?? response.data;

/**
 * Default filter state
 */
export const DEFAULT_FILTERS = {
  policySearch: '', // Text search on policy name/code
  status: '', // Filter by status (ACTIVE, DRAFT, etc.)
  employerSearch: '', // Text search on employer name
  dateFrom: '', // Coverage start date range
  dateTo: '' // Coverage end date range
};

/**
 * Status configuration for BenefitPolicy
 */
export const STATUS_CONFIG = {
  DRAFT: { label: 'مسودة', labelEn: 'Draft', color: 'default' },
  ACTIVE: { label: 'نشط', labelEn: 'Active', color: 'success' },
  SUSPENDED: { label: 'موقوف', labelEn: 'Suspended', color: 'warning' },
  EXPIRED: { label: 'منتهي', labelEn: 'Expired', color: 'error' },
  CANCELLED: { label: 'ملغي', labelEn: 'Cancelled', color: 'error' }
};

/**
 * useBenefitPolicyReport Hook
 *
 * Strategic Management Report for BenefitPolicy Coverage & Utilization.
 *
 * Purpose:
 * - هل BenefitPolicy مصمم بشكل صحيح؟
 * - أين يتم الاستهلاك الحقيقي؟
 * - أي BenefitPolicy يسبب أكبر ضغط مالي؟
 *
 * @param {Object} options
 * @param {number|null} options.employerId - Employer ID for filtering
 * @param {Object} options.filters - Filter criteria
 * @returns {Object} Policies data, KPIs, insights, loading states
 *
 * Data Sources:
 * - GET /api/benefit-policies (or /benefit-policies/employer/{id} for employer-specific)
 * - GET /api/unified-members (with organizationId filter)
 * - GET /api/claims (with employerId filter)
 */
export const useBenefitPolicyReport = ({ employerId, filters = DEFAULT_FILTERS } = {}) => {
  const [policies, setPolicies] = useState([]);
  const [members, setMembers] = useState([]);
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
   * Fetch all data from APIs
   */
  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      // Build params
      const policyParams = { size: 9999 };
      const memberParams = { size: 9999 };
      const claimsParams = { size: 9999 };

      if (employerId) {
        // For policies, use employer endpoint if available
        // For members, filter by employer using organizationId param
        memberParams.organizationId = employerId;
        claimsParams.employerId = employerId;
      }

      // Fetch policies, members, and claims in parallel
      // ⚠️ FIXED: Use /v1/claims to match Backend API
      const [policiesResponse, membersResponse, claimsResponse] = await Promise.all([
        employerId
          ? axiosClient.get(`/benefit-policies/employer/${employerId}`)
          : axiosClient.get('/benefit-policies', { params: policyParams }),
        axiosClient.get('/unified-members', { params: memberParams }),
        axiosClient.get('/claims', { params: claimsParams })
      ]);

      // Unwrap policies
      const policiesData = unwrap(policiesResponse);
      let policiesList = [];

      if (Array.isArray(policiesData)) {
        policiesList = policiesData;
      } else if (policiesData?.content) {
        policiesList = policiesData.content;
      } else if (policiesData?.items) {
        policiesList = policiesData.items;
      }

      // Unwrap members
      const membersData = unwrap(membersResponse);
      let membersList = [];

      if (Array.isArray(membersData)) {
        membersList = membersData;
      } else if (membersData?.content) {
        membersList = membersData.content;
      } else if (membersData?.items) {
        membersList = membersData.items;
      }

      // Map policies to UI-safe model
      const mappedPolicies = policiesList.map((policy) => ({
        id: policy.id,
        policyCode: policy.policyCode ?? policy.code ?? '—',
        name: policy.name ?? '—',
        name: policy.name ?? policy.name ?? '—',
        status: policy.status ?? 'DRAFT',
        employerId: policy.employerOrgId ?? policy.employerId ?? null,
        employerName: policy.employerName ?? policy.employer?.name ?? '—',
        startDate: policy.startDate ?? null,
        endDate: policy.endDate ?? null,
        defaultCoveragePercent: policy.defaultCoveragePercent ?? 0,
        maxClaimAmount: policy.maxClaimAmount ?? 0,
        description: policy.description ?? '',
        createdAt: policy.createdAt ?? null,
        updatedAt: policy.updatedAt ?? null,
        // Keep raw for drill-down
        _raw: policy
      }));

      // Map members with their policy association
      const mappedMembers = membersList.map((member) => ({
        id: member.id,
        name: member.fullName ?? member.fullName ?? '—',
        employerId: member.employerOrgId ?? member.employerId ?? member.employerOrganization?.id ?? null,
        policyId: member.benefitPolicyId ?? member.policyId ?? null,
        policyCode: member.benefitPolicyCode ?? member.policyCode ?? null,
        status: member.status ?? 'ACTIVE',
        _raw: member
      }));

      // Unwrap claims
      const claimsData = unwrap(claimsResponse);
      let claimsList = [];

      if (Array.isArray(claimsData)) {
        claimsList = claimsData;
      } else if (claimsData?.content) {
        claimsList = claimsData.content;
      } else if (claimsData?.items) {
        claimsList = claimsData.items;
      }

      // Map claims to UI-safe model
      const mappedClaims = claimsList.map((claim) => ({
        id: claim.id,
        memberId: claim.member?.id ?? claim.memberId ?? null,
        memberName: claim.member?.fullName ?? claim.memberName ?? '—',
        employerId: claim.member?.employerOrgId ?? claim.employerId ?? null,
        policyId: claim.member?.benefitPolicyId ?? claim.benefitPolicyId ?? null,
        policyName: claim.member?.benefitPolicyName ?? claim.benefitPolicyName ?? null,
        status: claim.status ?? 'DRAFT',
        requestedAmount: parseFloat(claim.requestedAmount) || 0,
        approvedAmount: claim.approvedAmount != null ? parseFloat(claim.approvedAmount) : null,
        visitDate: claim.visitDate ?? null,
        // Rejection-related fields
        rejectionReason: claim.reviewerComment ?? claim.rejectionReason ?? null,
        serviceCategoryId: claim.serviceCategoryId ?? claim.lines?.[0]?.serviceCategoryId ?? null,
        serviceCategoryName: claim.serviceCategoryName ?? claim.lines?.[0]?.serviceCategoryName ?? null,
        _raw: claim
      }));

      setPolicies(mappedPolicies);
      setMembers(mappedMembers);
      setClaims(mappedClaims);
      setPagination({
        page: 0,
        size: mappedPolicies.length,
        totalElements: mappedPolicies.length,
        totalPages: 1
      });
    } catch (err) {
      console.error('❌ Failed to fetch benefit policy report data:', err);
      setError(err.message || 'فشل في تحميل بيانات التقرير');
      setPolicies([]);
      setMembers([]);
      setClaims([]);
    } finally {
      setLoading(false);
    }
  }, [employerId]);

  /**
   * Initial fetch and refetch on employerId change
   */
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  /**
   * Apply client-side filters to policies
   */
  const filteredPolicies = useMemo(() => {
    let result = [...policies];

    // Filter by policy name/code search
    if (filters.policySearch && filters.policySearch.trim()) {
      const search = filters.policySearch.trim().toLowerCase();
      result = result.filter((policy) => policy.name.toLowerCase().includes(search) || policy.policyCode.toLowerCase().includes(search));
    }

    // Filter by status
    if (filters.status) {
      result = result.filter((policy) => policy.status === filters.status);
    }

    // Filter by employer name search
    if (filters.employerSearch && filters.employerSearch.trim()) {
      const search = filters.employerSearch.trim().toLowerCase();
      result = result.filter((policy) => policy.employerName.toLowerCase().includes(search));
    }

    // Filter by date range (startDate)
    if (filters.dateFrom) {
      result = result.filter((policy) => policy.startDate && policy.startDate >= filters.dateFrom);
    }
    if (filters.dateTo) {
      result = result.filter((policy) => policy.startDate && policy.startDate <= filters.dateTo);
    }

    return result;
  }, [policies, filters]);

  /**
   * Build mapping: policyId -> member count
   */
  const policyMemberCounts = useMemo(() => {
    const counts = {};

    // Count by policyId
    members.forEach((member) => {
      if (member.policyId) {
        counts[member.policyId] = (counts[member.policyId] || 0) + 1;
      }
    });

    // Also try to match by policyCode for policies that might have different ID references
    const policyCodeToId = {};
    policies.forEach((p) => {
      if (p.policyCode && p.policyCode !== '—') {
        policyCodeToId[p.policyCode] = p.id;
      }
    });

    members.forEach((member) => {
      if (member.policyCode && policyCodeToId[member.policyCode]) {
        const policyId = policyCodeToId[member.policyCode];
        // Don't double count - only add if not already counted by policyId
        if (!member.policyId || member.policyId !== policyId) {
          counts[policyId] = (counts[policyId] || 0) + 1;
        }
      }
    });

    return counts;
  }, [policies, members]);

  /**
   * Compute KPIs from filtered data
   *
   * KPIs:
   * 1. Total Benefit Policies - عدد الخطط
   * 2. Active Policies - الخطط الفعالة
   * 3. Members Covered - عدد الأعضاء المرتبطين
   * 4. Avg Members / Policy - متوسط التغطية
   * 5. Policies With No Usage - خطط لم تُستخدم
   */
  const kpis = useMemo(() => {
    const totalPolicies = filteredPolicies.length;

    // Active policies count
    const activePolicies = filteredPolicies.filter((p) => p.status === 'ACTIVE').length;

    // Total members covered (members linked to any of the filtered policies)
    const filteredPolicyIds = new Set(filteredPolicies.map((p) => p.id));
    const membersCovered = members.filter((m) => m.policyId && filteredPolicyIds.has(m.policyId)).length;

    // Average members per policy
    const avgMembersPerPolicy = totalPolicies > 0 ? (membersCovered / totalPolicies).toFixed(1) : 0;

    // Policies with no usage (no members assigned)
    const policiesWithNoUsage = filteredPolicies.filter((policy) => {
      const memberCount = policyMemberCounts[policy.id] || 0;
      return memberCount === 0;
    }).length;

    return {
      totalPolicies,
      activePolicies,
      membersCovered,
      avgMembersPerPolicy: parseFloat(avgMembersPerPolicy),
      policiesWithNoUsage
    };
  }, [filteredPolicies, members, policyMemberCounts]);

  /**
   * Compute Utilization KPIs (Section 2)
   *
   * KPIs:
   * 1. Total Claims Amount - SUM(requestedAmount)
   * 2. Approved Amount - SUM(approvedAmount)
   * 3. Utilization % - Approved / Annual Limit (تقريبي)
   * 4. Avg Utilization / Policy - AVG
   *
   * ⚠️ Utilization تقريبي (Client-side)
   */
  const utilizationKpis = useMemo(() => {
    // Calculate total requested amount
    const totalClaimsAmount = claims.reduce((sum, claim) => sum + claim.requestedAmount, 0);

    // Calculate total approved amount (only for claims with approvedAmount)
    const approvedAmount = claims.reduce((sum, claim) => {
      if (claim.approvedAmount != null) {
        return sum + claim.approvedAmount;
      }
      return sum;
    }, 0);

    // Calculate total annual limit from active policies
    // Using maxClaimAmount as annual limit approximation
    const activePoliciesList = filteredPolicies.filter((p) => p.status === 'ACTIVE');
    const totalAnnualLimit = activePoliciesList.reduce((sum, policy) => {
      const memberCount = policyMemberCounts[policy.id] || 0;
      const policyLimit = policy.maxClaimAmount || 0;
      // Annual limit = policy limit * number of members covered
      return sum + policyLimit * memberCount;
    }, 0);

    // Utilization percentage (Approved / Annual Limit)
    const utilizationPercent = totalAnnualLimit > 0 ? ((approvedAmount / totalAnnualLimit) * 100).toFixed(1) : 0;

    // Average utilization per policy
    const policiesWithClaims = new Set(
      claims
        .filter((c) => c.memberId)
        .map((c) => {
          const member = members.find((m) => m.id === c.memberId);
          return member?.policyId;
        })
        .filter(Boolean)
    );

    const avgUtilizationPerPolicy = policiesWithClaims.size > 0 ? (approvedAmount / policiesWithClaims.size).toFixed(0) : 0;

    return {
      totalClaimsAmount,
      approvedAmount,
      utilizationPercent: parseFloat(utilizationPercent),
      avgUtilizationPerPolicy: parseFloat(avgUtilizationPerPolicy),
      totalAnnualLimit,
      claimsCount: claims.length
    };
  }, [claims, filteredPolicies, policyMemberCounts, members]);

  /**
   * Section 3: Limits Pressure Analysis (ضغط الحدود)
   *
   * Table: Policy Limits Stress
   * - BenefitPolicy
   * - Annual Limit
   * - Used Amount
   * - Remaining
   * - Utilization %
   * - Status (Healthy / Warning / Critical)
   *
   * Status Logic:
   * < 60% → Healthy (صحي)
   * 60%–85% → Warning (تحذير)
   * > 85% → Critical (حرج)
   */
  const limitsStressData = useMemo(() => {
    // Build policy claims usage map
    // First, map members to policies
    const memberPolicyMap = {};
    members.forEach((m) => {
      if (m.id) {
        memberPolicyMap[m.id] = m.policyId;
      }
    });

    // Aggregate approved amounts by policy
    const policyUsage = {};
    claims.forEach((claim) => {
      // Try to get policy from claim directly or via member
      let policyId = claim.policyId;
      if (!policyId && claim.memberId) {
        policyId = memberPolicyMap[claim.memberId];
      }

      if (policyId && claim.approvedAmount != null) {
        if (!policyUsage[policyId]) {
          policyUsage[policyId] = 0;
        }
        policyUsage[policyId] += claim.approvedAmount;
      }
    });

    // Build stress data for each filtered policy
    const stressData = filteredPolicies.map((policy) => {
      const memberCount = policyMemberCounts[policy.id] || 0;
      const annualLimit = (policy.maxClaimAmount || 0) * memberCount;
      const usedAmount = policyUsage[policy.id] || 0;
      // Signed. A policy whose members have consumed more than the
      // aggregate showed 0 remaining, which reads as "fully used" rather than
      // "over" -- and this figure drives the stress status below it.
      const remaining = annualLimit - usedAmount;
      const utilizationPercent = annualLimit > 0 ? (usedAmount / annualLimit) * 100 : 0;

      // Determine status
      let status = 'healthy';
      let statusLabel = 'صحي';
      let statusColor = 'success';

      if (utilizationPercent > 85) {
        status = 'critical';
        statusLabel = 'حرج';
        statusColor = 'error';
      } else if (utilizationPercent >= 60) {
        status = 'warning';
        statusLabel = 'تحذير';
        statusColor = 'warning';
      }

      return {
        id: policy.id,
        policyName: policy.name,
        policyCode: policy.policyCode,
        employerName: policy.employerName,
        memberCount,
        annualLimit,
        usedAmount,
        remaining,
        utilizationPercent: parseFloat(utilizationPercent.toFixed(1)),
        status,
        statusLabel,
        statusColor
      };
    });

    // Sort by utilization (highest first for attention)
    return stressData.sort((a, b) => b.utilizationPercent - a.utilizationPercent);
  }, [filteredPolicies, claims, members, policyMemberCounts]);

  /**
   * Section 4: Rejections Analysis (تحليل الرفض)
   *
   * KPIs:
   * - Total Rejected Claims
   * - Rejected Amount
   * - Rejection Rate %
   *
   * Breakdown:
   * - By BenefitPolicy
   * - By Reason (from rejection comment)
   * - By Service Category (if available)
   *
   * ⚠️ If reason not available → "غير محدد"
   */
  const rejectionsAnalysis = useMemo(() => {
    // Filter rejected claims only
    const rejectedClaims = claims.filter((c) => c.status === 'REJECTED');

    // KPIs
    const totalRejectedClaims = rejectedClaims.length;
    const rejectedAmount = rejectedClaims.reduce((sum, c) => sum + c.requestedAmount, 0);
    const rejectionRate = claims.length > 0 ? (totalRejectedClaims / claims.length) * 100 : 0;

    // Map members to policies for lookup
    const memberPolicyMap = {};
    members.forEach((m) => {
      if (m.id) {
        memberPolicyMap[m.id] = {
          policyId: m.policyId,
          policyCode: m.policyCode
        };
      }
    });

    // Breakdown by BenefitPolicy
    const byPolicy = {};
    rejectedClaims.forEach((claim) => {
      let policyName = claim.policyName;
      let policyId = claim.policyId;

      // Try to find policy via member
      if (!policyId && claim.memberId && memberPolicyMap[claim.memberId]) {
        policyId = memberPolicyMap[claim.memberId].policyId;
      }

      // Find policy name from policies list
      if (policyId) {
        const policy = filteredPolicies.find((p) => p.id === policyId);
        if (policy) {
          policyName = policy.name;
        }
      }

      const key = policyName || 'غير محدد';
      if (!byPolicy[key]) {
        byPolicy[key] = { count: 0, amount: 0 };
      }
      byPolicy[key].count += 1;
      byPolicy[key].amount += claim.requestedAmount;
    });

    const byPolicyList = Object.entries(byPolicy)
      .map(([name, data]) => ({
        name,
        count: data.count,
        amount: data.amount,
        percentage: totalRejectedClaims > 0 ? (data.count / totalRejectedClaims) * 100 : 0
      }))
      .sort((a, b) => b.count - a.count);

    // Breakdown by Reason
    const byReason = {};
    rejectedClaims.forEach((claim) => {
      // Use reviewerComment or default to "غير محدد"
      const reason = claim.rejectionReason?.trim() || 'غير محدد';
      if (!byReason[reason]) {
        byReason[reason] = { count: 0, amount: 0 };
      }
      byReason[reason].count += 1;
      byReason[reason].amount += claim.requestedAmount;
    });

    const byReasonList = Object.entries(byReason)
      .map(([reason, data]) => ({
        reason,
        count: data.count,
        amount: data.amount,
        percentage: totalRejectedClaims > 0 ? (data.count / totalRejectedClaims) * 100 : 0
      }))
      .sort((a, b) => b.count - a.count);

    // Breakdown by Service Category
    const byCategory = {};
    rejectedClaims.forEach((claim) => {
      const category = claim.serviceCategoryName || 'غير محدد';
      if (!byCategory[category]) {
        byCategory[category] = { count: 0, amount: 0 };
      }
      byCategory[category].count += 1;
      byCategory[category].amount += claim.requestedAmount;
    });

    const byCategoryList = Object.entries(byCategory)
      .map(([category, data]) => ({
        category,
        count: data.count,
        amount: data.amount,
        percentage: totalRejectedClaims > 0 ? (data.count / totalRejectedClaims) * 100 : 0
      }))
      .sort((a, b) => b.count - a.count);

    return {
      kpis: {
        totalRejectedClaims,
        rejectedAmount,
        rejectionRate: parseFloat(rejectionRate.toFixed(1))
      },
      byPolicy: byPolicyList,
      byReason: byReasonList,
      byCategory: byCategoryList
    };
  }, [claims, members, filteredPolicies]);

  /**
   * Section 5: BenefitPolicy Effectiveness Ranking
   *
   * Table: Policy Performance
   * - Members
   * - Claims Count
   * - Approval Rate
   * - Avg Claim Amount
   * - Utilization %
   * - Rejection %
   *
   * Goal: Identify policies to expand, reduce, or redesign
   */
  const policyEffectivenessRanking = useMemo(() => {
    // Build member-to-policy mapping
    const memberPolicyMap = {};
    members.forEach((m) => {
      if (m.id) {
        memberPolicyMap[m.id] = m.policyId;
      }
    });

    // Aggregate claims data by policy
    const policyClaimsData = {};

    claims.forEach((claim) => {
      let policyId = claim.policyId;
      if (!policyId && claim.memberId) {
        policyId = memberPolicyMap[claim.memberId];
      }

      if (policyId) {
        if (!policyClaimsData[policyId]) {
          policyClaimsData[policyId] = {
            totalClaims: 0,
            approvedClaims: 0,
            rejectedClaims: 0,
            totalRequestedAmount: 0,
            totalApprovedAmount: 0
          };
        }

        policyClaimsData[policyId].totalClaims += 1;
        policyClaimsData[policyId].totalRequestedAmount += claim.requestedAmount;

        if (claim.status === 'APPROVED' || claim.status === 'SETTLED') {
          policyClaimsData[policyId].approvedClaims += 1;
          policyClaimsData[policyId].totalApprovedAmount += claim.approvedAmount || 0;
        } else if (claim.status === 'REJECTED') {
          policyClaimsData[policyId].rejectedClaims += 1;
        }
      }
    });

    // Build ranking data for each policy
    const rankingData = filteredPolicies.map((policy) => {
      const memberCount = policyMemberCounts[policy.id] || 0;
      const claimsData = policyClaimsData[policy.id] || {
        totalClaims: 0,
        approvedClaims: 0,
        rejectedClaims: 0,
        totalRequestedAmount: 0,
        totalApprovedAmount: 0
      };

      // Calculate metrics
      const claimsCount = claimsData.totalClaims;
      const approvalRate = claimsCount > 0 ? (claimsData.approvedClaims / claimsCount) * 100 : 0;
      const rejectionRate = claimsCount > 0 ? (claimsData.rejectedClaims / claimsCount) * 100 : 0;
      const avgClaimAmount = claimsCount > 0 ? claimsData.totalRequestedAmount / claimsCount : 0;

      // Utilization: approved amount / annual limit
      const annualLimit = (policy.maxClaimAmount || 0) * memberCount;
      const utilizationPercent = annualLimit > 0 ? (claimsData.totalApprovedAmount / annualLimit) * 100 : 0;

      // Effectiveness Score (composite)
      // Higher approval rate = better, Lower rejection = better,
      // Moderate utilization (40-70%) = optimal
      let effectivenessScore = 0;
      effectivenessScore += approvalRate * 0.3; // 30% weight
      effectivenessScore += (100 - rejectionRate) * 0.2; // 20% weight
      // Utilization score: optimal at 50-60%, penalty for too low or too high
      const utilizationScore = utilizationPercent <= 60 ? utilizationPercent * 1.5 : Math.max(0, 100 - (utilizationPercent - 60) * 2);
      effectivenessScore += utilizationScore * 0.3; // 30% weight
      // Activity score: having claims is good
      const activityScore = Math.min(100, claimsCount * 5);
      effectivenessScore += activityScore * 0.2; // 20% weight

      // Recommendation based on metrics
      let recommendation = 'مراجعة';
      let recommendationColor = 'default';

      if (memberCount === 0) {
        recommendation = 'غير مستخدم';
        recommendationColor = 'error';
      } else if (rejectionRate > 40) {
        recommendation = 'إعادة تصميم';
        recommendationColor = 'error';
      } else if (utilizationPercent > 85) {
        recommendation = 'توسيع الحدود';
        recommendationColor = 'warning';
      } else if (utilizationPercent < 20 && claimsCount < 5) {
        recommendation = 'تقليص أو دمج';
        recommendationColor = 'info';
      } else if (approvalRate > 80 && utilizationPercent >= 30 && utilizationPercent <= 70) {
        recommendation = 'أداء ممتاز';
        recommendationColor = 'success';
      } else if (approvalRate > 60) {
        recommendation = 'أداء جيد';
        recommendationColor = 'success';
      }

      return {
        id: policy.id,
        policyName: policy.name,
        policyCode: policy.policyCode,
        employerName: policy.employerName,
        status: policy.status,
        memberCount,
        claimsCount,
        approvalRate: parseFloat(approvalRate.toFixed(1)),
        avgClaimAmount: parseFloat(avgClaimAmount.toFixed(0)),
        utilizationPercent: parseFloat(utilizationPercent.toFixed(1)),
        rejectionRate: parseFloat(rejectionRate.toFixed(1)),
        effectivenessScore: parseFloat(effectivenessScore.toFixed(1)),
        recommendation,
        recommendationColor,
        // Raw data for tooltips
        _rawClaimsData: claimsData,
        _annualLimit: annualLimit
      };
    });

    // Sort by effectiveness score (descending)
    return rankingData.sort((a, b) => b.effectivenessScore - a.effectivenessScore);
  }, [filteredPolicies, claims, members, policyMemberCounts]);

  /**
   * Compute Insights from filtered data
   *
   * Insights:
   * 1. Policies by Status - Distribution across statuses
   * 2. Top 5 Policies by Members - Most utilized policies
   * 3. Coverage by Employer - Members per employer
   */
  const insights = useMemo(() => {
    // 1. Policies by Status Distribution
    const statusCounts = {};
    filteredPolicies.forEach((policy) => {
      const status = policy.status;
      statusCounts[status] = (statusCounts[status] || 0) + 1;
    });
    const policiesByStatus = Object.entries(statusCounts)
      .map(([status, count]) => ({
        status,
        label: STATUS_CONFIG[status]?.label ?? status,
        color: STATUS_CONFIG[status]?.color ?? 'default',
        count
      }))
      .sort((a, b) => b.count - a.count);

    // 2. Top 5 Policies by Members
    const topPoliciesByMembers = filteredPolicies
      .map((policy) => ({
        id: policy.id,
        name: policy.name,
        code: policy.policyCode,
        status: policy.status,
        memberCount: policyMemberCounts[policy.id] || 0
      }))
      .sort((a, b) => b.memberCount - a.memberCount)
      .slice(0, 5);

    // 3. Coverage by Employer (Top 5)
    const employerCounts = {};
    filteredPolicies.forEach((policy) => {
      const key = policy.employerName;
      if (key && key !== '—') {
        if (!employerCounts[key]) {
          employerCounts[key] = { policies: 0, members: 0 };
        }
        employerCounts[key].policies += 1;
        employerCounts[key].members += policyMemberCounts[policy.id] || 0;
      }
    });
    const coverageByEmployer = Object.entries(employerCounts)
      .map(([name, data]) => ({
        name,
        policies: data.policies,
        members: data.members
      }))
      .sort((a, b) => b.members - a.members)
      .slice(0, 5);

    // 4. Unused Policies List (policies with 0 members)
    const unusedPolicies = filteredPolicies
      .filter((policy) => (policyMemberCounts[policy.id] || 0) === 0)
      .map((policy) => ({
        id: policy.id,
        name: policy.name,
        code: policy.policyCode,
        status: policy.status,
        employerName: policy.employerName
      }))
      .slice(0, 10);

    return {
      policiesByStatus,
      topPoliciesByMembers,
      coverageByEmployer,
      unusedPolicies
    };
  }, [filteredPolicies, policyMemberCounts]);

  /**
   * Enrich policies with member count for table display
   */
  const enrichedPolicies = useMemo(() => {
    return filteredPolicies.map((policy) => ({
      ...policy,
      memberCount: policyMemberCounts[policy.id] || 0
    }));
  }, [filteredPolicies, policyMemberCounts]);

  return {
    // Data
    policies: enrichedPolicies,
    rawPolicies: policies,
    members,
    claims,

    // KPIs
    kpis,
    utilizationKpis,

    // Section 3: Limits Stress
    limitsStressData,

    // Section 4: Rejections
    rejectionsAnalysis,

    // Section 5: Policy Effectiveness Ranking
    policyEffectivenessRanking,

    // Insights
    insights,

    // State
    loading,
    error,
    pagination,

    // Actions
    refetch: fetchData,

    // Utilities
    policyMemberCounts
  };
};

export default useBenefitPolicyReport;
