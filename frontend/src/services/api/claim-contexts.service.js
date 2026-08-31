import axiosClient from 'utils/axios';

export const getActiveClaimContexts = async () => {
  const response = await axiosClient.get('/claim-contexts');
  return response.data?.data || response.data || [];
};
