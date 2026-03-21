import axiosClient from 'utils/axios';

const BASE_URL = '/claim-rejection-reasons';
const unwrap = (res) => res.data?.data || res.data;

export const claimRejectionReasonsService = {
    getAll: async () => {
        const res = await axiosClient.get(BASE_URL);
        return unwrap(res);
    },
    create: async (reasonText) => {
        const res = await axiosClient.post(BASE_URL, { reasonText });
        return unwrap(res);
    },
    update: async (id, reasonText) => {
        const res = await axiosClient.put(`${BASE_URL}/${id}`, { reasonText });
        return unwrap(res);
    },
    delete: async (id) => {
        const res = await axiosClient.delete(`${BASE_URL}/${id}`);
        return unwrap(res);
    }
};
