import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

// ==============================|| SETTLEMENT SERVICE - API v1 ||============================== //
// Phase 3B + API Contract Enforcement: Settlement API Service for Provider Accounts and Batches
//
// ✅ ALL ENDPOINTS USE /api/v1/ VERSIONING (handled by axios baseURL)
// ✅ TypeScript contracts available in: frontend/src/types/api/settlement/index.ts
// ✅ Backend contracts: backend/src/main/java/com/waad/tba/modules/settlement/api/
// ✅ FINANCIAL SAFETY: Frontend NEVER sends monetary values

const PROVIDER_ACCOUNTS_URL = '/provider-accounts';
const SETTLEMENT_BATCHES_URL = '/settlement-batches';
const PROVIDER_PAYMENTS_URL = '/settlements/payments';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for settlement service
 */
const handleSettlementErrors = createErrorHandler('التسوية', {
  404: 'السجل غير موجود',
  400: 'طلب غير صالح',
  409: 'تعارض في البيانات - قد تكون المطالبة موجودة في دفعة أخرى',
  422: 'البيانات المُدخلة غير صحيحة'
});

// ==============================|| PROVIDER ACCOUNTS ||============================== //

export const providerAccountsService = {
  /**
   * Get all provider accounts with outstanding balance
   * @returns {Promise<Array>} List of provider accounts
   */
  getAll: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.status) queryParams.append('status', params.status);
      if (params.hasBalance !== undefined) queryParams.append('hasBalance', String(!!params.hasBalance));

      const url = queryParams.toString() ? `${PROVIDER_ACCOUNTS_URL}?${queryParams.toString()}` : PROVIDER_ACCOUNTS_URL;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get account summary by provider ID
   * @param {number} providerId - Provider ID
   * @returns {Promise<Object>} Account summary with balance info
   */
  getByProviderId: async (providerId) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const response = await axiosClient.get(`${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get account by account ID
   * @param {number} accountId - Account ID
   * @returns {Promise<Object>} Account details
   */
  getById: async (accountId) => {
    try {
      if (!accountId) throw new Error('معرف الحساب مطلوب');
      const response = await axiosClient.get(`${PROVIDER_ACCOUNTS_URL}/${accountId}`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get transactions for a provider
   * @param {number} providerId - Provider ID
   * @param {Object} params - Pagination params {page, size, startDate, endDate}
   * @returns {Promise<Object>} Paginated transactions
   */
  getTransactions: async (providerId, params = {}) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.startDate) queryParams.append('startDate', params.startDate);
      if (params.endDate) queryParams.append('endDate', params.endDate);

      const url = `${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}/transactions${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get recent transactions (last 10) for quick view
   * @param {number} providerId - Provider ID
   * @returns {Promise<Array>} Last 10 transactions
   */
  getRecentTransactions: async (providerId) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const response = await axiosClient.get(`${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}/transactions/recent`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get total outstanding balance across all providers
   * @returns {Promise<Object>} Summary with total outstanding
   */
  getTotalOutstanding: async () => {
    try {
      const response = await axiosClient.get(`${PROVIDER_ACCOUNTS_URL}/summary/total-outstanding`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Verify account balance integrity
   * @param {number} accountId - Account ID
   * @returns {Promise<Object>} Verification result
   */
  verifyBalance: async (accountId) => {
    try {
      if (!accountId) throw new Error('معرف الحساب مطلوب');
      const response = await axiosClient.get(`${PROVIDER_ACCOUNTS_URL}/${accountId}/verify-balance`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Settle full remaining provider balance using manual adjustment debit.
   * @param {number} providerId - Provider ID
   * @param {string} reason - Manual settlement reason
   */
  settleRemainingBalance: async (providerId, reason) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const response = await axiosClient.post(`${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}/settle-remaining`, {
        reason: reason || 'Manual settlement from provider account details page'
      });
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Recalculate running_balance from transaction history (repair tool for SUPER_ADMIN).
   * Use when balance is stale due to claims deleted before the reversal fix.
   * @param {number} providerId - Provider ID
   */
  recalculateBalance: async (providerId) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const response = await axiosClient.post(
        `${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}/recalculate-balance`
      );
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Recalculate running_balance for ALL provider accounts in one operation.
   * Idempotent — safe to call multiple times.
   * Requires SUPER_ADMIN role.
   */
  recalculateAllBalances: async () => {
    try {
      const response = await axiosClient.post(`${PROVIDER_ACCOUNTS_URL}/recalculate-all-balances`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  }
};

// ==============================|| SETTLEMENT BATCHES ||============================== //

export const settlementBatchesService = {
  /**
   * Create a new settlement batch (API v1)
   * @param {Object} data - CreateSettlementBatchRequest: {providerId, description?, claimIds?}
   * @returns {Promise<Object>} SettlementBatchResponse
   *
   * ⚠️ CONTRACT ENFORCEMENT:
   *    - providerId: required (number)
   *    - description: optional (string)
   *    - claimIds: optional (number[])
   *    - NO amount fields allowed (backend calculates)
   *
   * @see frontend/src/types/api/settlement/index.ts - CreateSettlementBatchRequest
   * @see backend: CreateSettlementBatchRequest.java
   */
  create: async (data) => {
    try {
      if (!data.providerId) throw new Error('معرف مقدم الخدمة مطلوب');

      // Strict contract enforcement - only send defined fields
      const contractRequest = {
        providerId: data.providerId,
        ...(data.description && { description: data.description }),
        ...(data.claimIds && Array.isArray(data.claimIds) && { claimIds: data.claimIds })
      };

      // SAFETY: Strip any unknown/calculated fields (totals, amounts, etc.)
      const response = await axiosClient.post(SETTLEMENT_BATCHES_URL, contractRequest);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get batch details by ID
   * @param {number} batchId - Batch ID
   * @returns {Promise<Object>} Batch summary with items
   */
  getById: async (batchId) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');
      const response = await axiosClient.get(`${SETTLEMENT_BATCHES_URL}/${batchId}`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get all batches with optional status filter
   * @param {Object} params - {status, page, size}
   * @returns {Promise<Object>} Paginated batches
   */
  getAll: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.status) queryParams.append('status', params.status);
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);

      const url = `${SETTLEMENT_BATCHES_URL}${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get batch items (claims in batch)
   * @param {number} batchId - Batch ID
   * @returns {Promise<Array>} List of claims in batch
   */
  getItems: async (batchId) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');
      const response = await axiosClient.get(`${SETTLEMENT_BATCHES_URL}/${batchId}/items`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Get available claims for batching (APPROVED + not in batch)
   * @param {number} providerId - Provider ID
   * @returns {Promise<Array>} List of available claims
   */
  getAvailableClaims: async (providerId) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const response = await axiosClient.get(`${SETTLEMENT_BATCHES_URL}/available-claims/${providerId}`);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Add claims to a DRAFT batch (API v1)
   * @param {number} batchId - Batch ID
   * @param {Array<number>} claimIds - Claim IDs to add
   * @returns {Promise<Object>} BatchOperationResultResponse
   *
   * ⚠️ CONTRACT ENFORCEMENT:
   *    - claimIds: required (number[], min 1)
   *    - NO amount fields (backend recalculates batch total)
   *
   * @see frontend/src/types/api/settlement/index.ts - AddClaimsToBatchRequest
   * @see backend: AddClaimsToBatchRequest.java
   */
  addClaims: async (batchId, claimIds) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');
      if (!claimIds?.length) throw new Error('يجب تحديد مطالبة واحدة على الأقل');

      // Strict contract - only claimIds
      const contractRequest = { claimIds };

      const response = await axiosClient.put(`${SETTLEMENT_BATCHES_URL}/${batchId}/claims`, contractRequest);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Remove claims from a DRAFT batch (API v1)
   * @param {number} batchId - Batch ID
   * @param {Array<number>} claimIds - Claim IDs to remove
   * @returns {Promise<Object>} BatchOperationResultResponse
   *
   * ⚠️ CONTRACT ENFORCEMENT:
   *    - claimIds: required (number[], min 1)
   *    - NO amount fields (backend recalculates batch total)
   *
   * @see frontend/src/types/api/settlement/index.ts - RemoveClaimsFromBatchRequest
   * @see backend: RemoveClaimsFromBatchRequest.java
   */
  removeClaims: async (batchId, claimIds) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');
      if (!claimIds?.length) throw new Error('يجب تحديد مطالبة واحدة على الأقل');

      // Strict contract - only claimIds
      const contractRequest = { claimIds };

      const response = await axiosClient.delete(`${SETTLEMENT_BATCHES_URL}/${batchId}/claims`, {
        data: contractRequest
      });
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  /**
   * Confirm a DRAFT batch (DRAFT → CONFIRMED) - API v1
   * @param {number} batchId - Batch ID
   * @returns {Promise<Object>} SettlementBatchResponse
   *
   * ⚠️⚠️ FINANCIAL LOCK POINT ⚠️⚠️
   * After confirmation:
   *    - batch.totalNetAmount is locked (immutable)
   *    - No claims can be added/removed
   *    - Amount used for payment = locked totalNetAmount
   *
   * ⚠️ CONTRACT ENFORCEMENT:
   *    - NO amount confirmation required (backend recalculates)
   *    - Optional note field only
   *
   * @see frontend/src/types/api/settlement/index.ts - ConfirmSettlementBatchRequest
   * @see backend: ConfirmSettlementBatchRequest.java
   */
  confirm: async (batchId) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');

      // Empty body or optional note - NO amounts
      const contractRequest = {};

      const response = await axiosClient.post(`${SETTLEMENT_BATCHES_URL}/${batchId}/confirm`, contractRequest);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },


}

export const providerPaymentsService = {
  getConfirmedBatches: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);

      const url = `${PROVIDER_PAYMENTS_URL}/confirmed-batches${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  createPayment: async (batchId, data = {}) => {
    try {
      if (!batchId) throw new Error('معرف الدفعة مطلوب');
      const paymentReference = String(data.paymentReference || '').trim();
      if (!paymentReference) throw new Error('مرجع الدفع مطلوب');
      if (!data.amount) throw new Error('قيمة الدفع مطلوبة');

      const contractRequest = {
        amount: data.amount,
        paymentReference,
        paymentMethod: data.paymentMethod || 'BANK_TRANSFER',
        ...(data.notes && { notes: String(data.notes) })
      };

      const response = await axiosClient.post(`${PROVIDER_PAYMENTS_URL}/batches/${batchId}`, contractRequest);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  },

  createProviderInstallment: async (providerId, data = {}) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const paymentReference = String(data.paymentReference || '').trim();
      if (!paymentReference) throw new Error('مرجع الدفع مطلوب');
      if (!data.amount) throw new Error('مبلغ الدفعة مطلوب');

      const contractRequest = {
        amount: data.amount,
        paymentReference,
        paymentMethod: data.paymentMethod || 'BANK_TRANSFER',
        ...(data.notes && { notes: String(data.notes) })
      };

      const response = await axiosClient.post(`${PROVIDER_ACCOUNTS_URL}/by-provider/${providerId}/pay`, contractRequest);
      return unwrap(response);
    } catch (error) {
      throw handleSettlementErrors(error);
    }
  }
};

// Default export for convenience
export default {
  providerAccounts: providerAccountsService,
  batches: settlementBatchesService,
  providerPayments: providerPaymentsService
};
