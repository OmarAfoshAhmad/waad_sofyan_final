import axiosClient from 'utils/axios';

const unwrap = (response) => response.data?.data || response.data;

export const getBenefitStructure = async (policyId) =>
  unwrap(await axiosClient.get(`/benefit-policies/${policyId}/structure`));

export const createBenefitGroup = async (policyId, payload) =>
  unwrap(await axiosClient.post(`/benefit-policies/${policyId}/structure/groups`, payload));

export const createLimitBucket = async (policyId, payload) =>
  unwrap(await axiosClient.post(`/benefit-policies/${policyId}/structure/buckets`, payload));

export const linkRuleToBucket = async (policyId, ruleId, payload) =>
  unwrap(await axiosClient.post(`/benefit-policies/${policyId}/structure/rules/${ruleId}/buckets`, payload));

export const deleteLimitBucket = async (policyId, bucketId) =>
  unwrap(await axiosClient.delete(`/benefit-policies/${policyId}/structure/buckets/${bucketId}`));

export const deleteBenefitGroup = async (policyId, groupId) =>
  unwrap(await axiosClient.delete(`/benefit-policies/${policyId}/structure/groups/${groupId}`));

export const deleteRuleBucketLink = async (policyId, linkId) =>
  unwrap(await axiosClient.delete(`/benefit-policies/${policyId}/structure/links/${linkId}`));

export const importBenefitStructure = async (policyId, file, dryRun = true) => {
  const formData = new FormData();
  formData.append('file', file);
  return unwrap(await axiosClient.post(`/benefit-policies/${policyId}/structure/import`, formData, {
    params: { dryRun },
    headers: { 'Content-Type': 'multipart/form-data' }
  }));
};
