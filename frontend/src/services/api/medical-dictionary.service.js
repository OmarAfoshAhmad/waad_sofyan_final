import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

const BASE_URL = '/medical-dictionary';
const unwrap = (response) => response.data?.data || response.data;

export const searchDictionaryEntries = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/entries`, { params });
  return normalizePaginatedResponse(response);
};

export const createDictionaryEntry = async (payload) => {
  const response = await axiosClient.post(`${BASE_URL}/entries`, payload);
  return unwrap(response);
};

export const addDictionarySynonym = async (entryId, payload) => {
  const response = await axiosClient.post(`${BASE_URL}/entries/${entryId}/synonyms`, payload);
  return unwrap(response);
};

export const toggleDictionarySynonym = async (synonymId) => {
  const response = await axiosClient.patch(`${BASE_URL}/synonyms/${synonymId}/toggle`);
  return unwrap(response);
};

export const matchMedicalDictionary = async (text) => {
  const response = await axiosClient.get(`${BASE_URL}/match`, { params: { text } });
  return unwrap(response);
};

export const listDictionarySuggestions = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/suggestions`, { params });
  return normalizePaginatedResponse(response);
};

export const createDictionarySuggestion = async (payload) => {
  const response = await axiosClient.post(`${BASE_URL}/suggestions`, payload);
  return unwrap(response);
};

export default {
  searchDictionaryEntries,
  createDictionaryEntry,
  addDictionarySynonym,
  toggleDictionarySynonym,
  matchMedicalDictionary,
  listDictionarySuggestions,
  createDictionarySuggestion
};
