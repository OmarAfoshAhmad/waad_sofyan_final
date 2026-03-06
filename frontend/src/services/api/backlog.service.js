import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';

// ==============================|| BACKLOG SERVICE ||============================== //

const BASE_URL = '/api/v1/backlog';

const unwrap = (response) => response.data?.data || response.data;

const handleBacklogErrors = createErrorHandler('Backlog', {
    404: 'البيانات المطلوبة غير موجودة',
    400: 'خطأ في البيانات المُدخلة',
    500: 'فشل في عملية المعالجة'
});

export const backlogService = {
    /**
     * Create backlog claim manually
     * @param {Object} data - Backlog claim data
     */
    createManual: async (data) => {
        try {
            const response = await axiosClient.post(`${BASE_URL}/manual`, data);
            return unwrap(response);
        } catch (error) {
            throw handleBacklogErrors(error);
        }
    },

    /**
     * Import backlog claims from Excel
     * @param {FormData} formData - Excel file data
     */
    importExcel: async (formData) => {
        try {
            const response = await axiosClient.post(`${BASE_URL}/import`, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            });
            return unwrap(response);
        } catch (error) {
            throw handleBacklogErrors(error);
        }
    },

    /**
     * Cancel (soft-delete) a backlog claim
     * @param {number} claimId - Claim ID
     */
    deleteManual: async (claimId) => {
        try {
            const response = await axiosClient.delete(`${BASE_URL}/${claimId}`);
            return unwrap(response);
        } catch (error) {
            throw handleBacklogErrors(error);
        }
    }
};

export default backlogService;
