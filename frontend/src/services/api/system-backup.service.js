/**
 * System Backup API Service
 * Manual and scheduled backups, integrity validation and retention.
 *
 * API Base: /api/v1/system/backups  (SUPER_ADMIN only)
 */

import axiosClient from 'utils/axios';

const BASE_URL = '/system/backups';

export const systemBackupService = {
  /** Destination path health, free space and last-backup summary. */
  getStatus: async () => {
    const response = await axiosClient.get(`${BASE_URL}/status`);
    return response.data?.data;
  },

  /** Most recent backup jobs, newest first. */
  list: async () => {
    const response = await axiosClient.get(BASE_URL);
    return response.data?.data ?? [];
  },

  /**
   * Runs a backup immediately.
   * @param {'DATABASE_ONLY'|'FILES_ONLY'|'FULL_SYSTEM'} type
   * @param {string} [note]
   */
  create: async (type, note) => {
    const response = await axiosClient.post(BASE_URL, { type, note });
    return response.data?.data;
  },

  /** Recomputes the archive checksum and compares it with the recorded one. */
  validate: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/validate`);
    return response.data?.data;
  },

  /** Read-only restore rehearsal; never touches the live database. */
  rehearse: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/rehearse`);
    return response.data?.data;
  },

  /**
   * Retention cleanup. Defaults to a dry run so nothing is deleted by accident.
   * @param {boolean} dryRun
   */
  purge: async (dryRun = true) => {
    const response = await axiosClient.post(`${BASE_URL}/purge`, null, { params: { dryRun } });
    return response.data?.data;
  },

  getSettings: async () => {
    const response = await axiosClient.get(`${BASE_URL}/settings`);
    return response.data?.data;
  },

  updateSettings: async (settings) => {
    const response = await axiosClient.put(`${BASE_URL}/settings`, settings);
    return response.data?.data;
  },

  /** Downloads the archive as a blob so the caller can trigger a save dialog. */
  download: async (id) => {
    const response = await axiosClient.get(`${BASE_URL}/${id}/download`, { responseType: 'blob' });
    return response.data;
  }
};

export default systemBackupService;
