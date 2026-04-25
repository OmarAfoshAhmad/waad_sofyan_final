import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

const BASE_URL = '/admin/medical-audit-logs';

const handleAuditErrors = createErrorHandler('سجل التدقيق', {
    403: 'ليس لديك صلاحية لمشاهدة سجلات التدقيق',
    404: 'سجل التدقيق غير موجود'
});

export const auditService = {
    /**
     * Search medical audit logs
     * @param {Object} params - {claimId, correlationId, page, size, sortBy, sortDir}
     */
    search: async (params = {}) => {
        try {
            const queryParams = new URLSearchParams();
            if (params.claimId) queryParams.append('claimId', params.claimId);
            if (params.correlationId) queryParams.append('correlationId', params.correlationId);
            if (params.page) queryParams.append('page', params.page);
            if (params.size) queryParams.append('size', params.size);
            if (params.sortBy) queryParams.append('sortBy', params.sortBy);
            if (params.sortDir) queryParams.append('sortDir', params.sortDir);

            const response = await axiosClient.get(`${BASE_URL}?${queryParams.toString()}`);
            return normalizePaginatedResponse(response);
        } catch (error) {
            throw handleAuditErrors(error);
        }
    },

    /**
     * Export audit logs to Excel
     */
    exportXlsx: async (params = {}) => {
        try {
            const queryParams = new URLSearchParams();
            if (params.claimId) queryParams.append('claimId', params.claimId);
            if (params.correlationId) queryParams.append('correlationId', params.correlationId);

            const response = await axiosClient.get(`${BASE_URL}/export.xlsx?${queryParams.toString()}`, {
                responseType: 'blob'
            });
            return response.data;
        } catch (error) {
            throw handleAuditErrors(error);
        }
    },

    /**
     * Bulk delete audit logs
     * @param {Object} data - {ids: [], password: ''}
     */
    deleteBulk: async (data) => {
        try {
            const response = await axiosClient.post(`${BASE_URL}/bulk-delete`, data);
            return response.data;
        } catch (error) {
            throw handleAuditErrors(error);
        }
    }
};

export default auditService;
