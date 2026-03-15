/**
 * useSystemConfig — thin wrapper around SystemConfigContext.
 *
 * All logic (fetching, caching, refresh) lives in SystemConfigContext.
 * This hook exists only for backward-compatible imports; all consumers
 * (Navigation, SystemSettingsPage, ProviderPortalGuard …) now share the
 * SAME state → calling refresh() anywhere immediately updates ALL of them.
 */

import { useSystemConfigContext } from 'contexts/SystemConfigContext';

export default function useSystemConfig() {
  return useSystemConfigContext();
}
