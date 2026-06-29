import axiosClient from 'utils/axios';

const BASE_URL = '/payments';

export const getMonthlySettlementSummaries = async (params) => {
  const response = await axiosClient.get(`${BASE_URL}/summaries`, { params });
  return response?.data || [];
};

export const getPaymentRecords = async (employerId, providerId, year, month) => {
  const response = await axiosClient.get(`${BASE_URL}/records`, {
    params: { employerId, providerId, year, month }
  });
  return response?.data || [];
};

export const addPayment = async (data) => {
  const response = await axiosClient.post(BASE_URL, data);
  return response?.data;
};

export const updatePayment = async (id, data) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, data);
  return response?.data;
};

export const deletePayment = async (id, reason) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`, { params: { reason } });
  return response?.data;
};

export const getPaymentAuditLogs = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}/audit`);
  return response?.data || [];
};

const paymentsService = {
  getMonthlySettlementSummaries,
  getPaymentRecords,
  addPayment,
  updatePayment,
  deletePayment,
  getPaymentAuditLogs
};

export default paymentsService;
