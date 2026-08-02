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

export const listDictionarySynonyms = async (entryId, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/entries/${entryId}/synonyms`, { params });
  return normalizePaginatedResponse(response);
};

export const searchDictionarySynonyms = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/synonyms/search`, { params });
  return normalizePaginatedResponse(response);
};

export const toggleDictionarySynonym = async (synonymId) => {
  const response = await axiosClient.patch(`${BASE_URL}/synonyms/${synonymId}/toggle`);
  return unwrap(response);
};

export const matchMedicalDictionary = async (text) => {
  const response = await axiosClient.get(`${BASE_URL}/match`, { params: { text } });
  return unwrap(response);
};

export const classifyPriceListWithDictionary = async (payload) => {
  const response = await axiosClient.post(`${BASE_URL}/price-lists/classify`, payload);
  return unwrap(response);
};

export const savePriceListClassificationSession = async (payload) => {
  const response = await axiosClient.post(`${BASE_URL}/price-lists/sessions`, payload);
  return unwrap(response);
};

export const listPriceListClassificationSessions = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/price-lists/sessions`, { params });
  return normalizePaginatedResponse(response);
};

export const getPriceListClassificationSession = async (sessionId) => {
  const response = await axiosClient.get(`${BASE_URL}/price-lists/sessions/${sessionId}`);
  return unwrap(response);
};

export const deletePriceListClassificationSession = async (sessionId) => {
  const response = await axiosClient.delete(`${BASE_URL}/price-lists/sessions/${sessionId}`);
  return unwrap(response);
};

export const postPriceListClassificationSessionToContract = async (sessionId, payload) => {
  const response = await axiosClient.post(`${BASE_URL}/price-lists/sessions/${sessionId}/post-to-contract`, payload);
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

export const approveDictionarySuggestion = async (suggestionId, payload) => {
  const response = await axiosClient.post(`${BASE_URL}/suggestions/${suggestionId}/approve`, payload);
  return unwrap(response);
};

export const rejectDictionarySuggestion = async (suggestionId, payload) => {
  const response = await axiosClient.post(`${BASE_URL}/suggestions/${suggestionId}/reject`, payload);
  return unwrap(response);
};

export default {
  searchDictionaryEntries,
  createDictionaryEntry,
  addDictionarySynonym,
  listDictionarySynonyms,
  searchDictionarySynonyms,
  toggleDictionarySynonym,
  matchMedicalDictionary,
  classifyPriceListWithDictionary,
  savePriceListClassificationSession,
  listPriceListClassificationSessions,
  getPriceListClassificationSession,
  deletePriceListClassificationSession,
  postPriceListClassificationSessionToContract,
  listDictionarySuggestions,
  createDictionarySuggestion,
  approveDictionarySuggestion,
  rejectDictionarySuggestion
};
