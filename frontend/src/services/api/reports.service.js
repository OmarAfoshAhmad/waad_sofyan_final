import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

// ==============================|| REPORTS SERVICE ||============================== //

const BASE_URL = '/reports';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for reports service
 * Provides user-friendly Arabic error messages
 */
const handleReportErrors = createErrorHandler('التقرير', {
  404: 'التقرير غير موجود',
  400: 'معرف مقدم الخدمة مطلوب',
  403: 'ليس لديك صلاحية لعرض هذا التقرير'
});

export const reportsService = {
  // ======================= FINANCIAL SUMMARY =======================

  /**
   * Get comprehensive financial summary (AUTHORITATIVE - Single Source of Truth)
   * @param {Object} params - {employerOrgId, fromDate, toDate}
   * @returns {Promise<Object>} Financial summary DTO
   */
  getFinancialSummary: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.employerOrgId) queryParams.append('employerOrgId', params.employerOrgId);
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);

      const url = queryParams.toString() ? `${BASE_URL}/financial-summary?${queryParams.toString()}` : `${BASE_URL}/financial-summary`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  /**
   * Get settlement-focused summary
   * @param {Object} params - {employerOrgId}
   * @returns {Promise<Object>} Settlement summary DTO
   */
  getSettlementSummary: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.employerOrgId) queryParams.append('employerOrgId', params.employerOrgId);

      const url = queryParams.toString() ? `${BASE_URL}/settlement-summary?${queryParams.toString()}` : `${BASE_URL}/settlement-summary`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= FINANCIAL CONSOLIDATION =======================

  /**
   * Get dynamic multi-entity financial consolidation report (Excel Replacement)
   * @param {Object} params - {year}
   * @returns {Promise<Array>} Array of FinancialConsolidationDto
   */
  getFinancialConsolidation: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.year) queryParams.append('year', params.year);

      const url = queryParams.toString() 
        ? `${BASE_URL}/financial-consolidation?${queryParams.toString()}` 
        : `${BASE_URL}/financial-consolidation`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= ACCOUNTANT PROFIT REPORT =======================

  /**
   * Get accountant profit report (Company Discount Profits)
   * @param {Object} params - {employerId, year, month, providerId}
   * @returns {Promise<Array>} Array of CompanyProfitReportRowDto
   */
  getCompanyProfitReport: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.employerId) queryParams.append('employerId', params.employerId);
      if (params.year) queryParams.append('year', params.year);
      if (params.month) queryParams.append('month', params.month);
      if (params.providerId) queryParams.append('providerId', params.providerId);

      const url = queryParams.toString() 
        ? `${BASE_URL}/company-profit?${queryParams.toString()}` 
        : `${BASE_URL}/company-profit`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= ADJUDICATION REPORTS =======================

  /**
   * Get adjudication report
   * @param {Object} params - {fromDate, toDate, providerName, statuses}
   * @returns {Promise<Object>} Adjudication report DTO
   */
  getAdjudicationReport: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);
      if (params.providerName) queryParams.append('providerName', params.providerName);
      if (params.statuses && params.statuses.length > 0) {
        params.statuses.forEach((s) => queryParams.append('statuses', s));
      }

      const response = await axiosClient.get(`${BASE_URL}/adjudication?${queryParams.toString()}`);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= PROVIDER SETTLEMENT REPORTS =======================

  /**
   * Get provider settlement report with line-level detail
   *
   * ⚠️ CANONICAL: All calculations done in backend
   * ⚠️ SECURITY: Provider users can only see their own provider
   *
   * @param {Object} params - {
   *   providerId: number (required for admin, ignored for provider users),
  *   employerOrgId: number (optional),
   *   fromDate: string (YYYY-MM-DD),
   *   toDate: string (YYYY-MM-DD),
   *   statuses: string[] (APPROVED, SETTLED),
   *   claimNumber: string,
   *   preAuthNumber: string,
   *   memberId: number
   * }
   * @returns {Promise<Object>} ProviderSettlementReportDto with line-level details
   */
  getProviderSettlementReport: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();

      // Required for admin, backend will override for provider users
      if (params.providerId) queryParams.append('providerId', params.providerId);

      // Optional employer filter
      if (params.employerOrgId) queryParams.append('employerOrgId', params.employerOrgId);

      // Date filters
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);

      // Status filter (can be multiple)
      if (params.statuses && params.statuses.length > 0) {
        params.statuses.forEach((s) => queryParams.append('statuses', s));
      }

      // Additional filters
      if (params.claimNumber) queryParams.append('claimNumber', params.claimNumber);
      if (params.preAuthNumber) queryParams.append('preAuthNumber', params.preAuthNumber);
      if (params.memberId) queryParams.append('memberId', params.memberId);

      const url = queryParams.toString()
        ? `${BASE_URL}/provider-settlements?${queryParams.toString()}`
        : `${BASE_URL}/provider-settlements`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  /**
   * Get list of providers available for settlement reports
   * - Admin: returns all providers
   * - Provider: returns only their provider
   *
   * @returns {Promise<Array>} Array of {id, name, nameabic}
   */
  getProvidersForSettlementReport: async () => {
    try {
      const response = await axiosClient.get(`${BASE_URL}/provider-settlements/providers`);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= MEMBER STATEMENT =======================

  /**
   * Get member statement
   * @param {number} memberId - Member ID
   * @param {Object} params - {fromDate, toDate}
   * @returns {Promise<Object>} Member statement DTO
   */
  getMemberStatement: async (memberId, params = {}) => {
    try {
      if (!memberId) throw new Error('معرف العضو مطلوب');

      const queryParams = new URLSearchParams();
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);

      const url = queryParams.toString()
        ? `${BASE_URL}/member-statement/${memberId}?${queryParams.toString()}`
        : `${BASE_URL}/member-statement/${memberId}`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  },

  // ======================= SUMMARY / DASHBOARD =======================

  /**
   * Get summary statistics for dashboard
   * @returns {Promise<Object>} Summary report DTO
   */
  getSummary: async () => {
    try {
      const response = await axiosClient.get(`${BASE_URL}/summary`);
      return unwrap(response);
    } catch (error) {
      throw handleReportErrors(error);
    }
  }
};

export default reportsService;
