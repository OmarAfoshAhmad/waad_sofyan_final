/**
 * SystemConfigContext — Shared system configuration (feature flags + UI config)
 *
 * Converts useSystemConfig from isolated per-component hook to a SINGLE shared
 * context so all consumers (Navigation, SystemSettingsPage, ProviderPortalGuard, …)
 * see the same state and react immediately to refresh().
 *
 * Problem it solves:
 *   - Before: each useSystemConfig() call had independent state → refresh() in
 *     SystemSettingsPage never triggered re-render in Navigation.
 *   - After:  one shared Provider → calling refresh() anywhere updates ALL consumers.
 */

import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import featureFlagsService from 'services/api/featureFlags.service';

// ─── cache helpers ─────────────────────────────────────────────────────────

const CACHE_KEY = '__sys_config__';
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes

function readCache() {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const { data, expiry } = JSON.parse(raw);
    if (Date.now() > expiry) {
      sessionStorage.removeItem(CACHE_KEY);
      return null;
    }
    return data;
  } catch {
    return null;
  }
}

function writeCache(data) {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ data, expiry: Date.now() + CACHE_TTL }));
  } catch {
    // sessionStorage unavailable — silently ignore
  }
}

function flagsToMap(flagList) {
  return flagList.reduce(
    (acc, f) => {
      acc[f.flagKey] = f.enabled;
      return acc;
    },
    { ...DEFAULT_FLAGS }
  );
}

// ─── defaults ─────────────────────────────────────────────────────────────

const DEFAULT_UI_CONFIG = {
  logoUrl: '',
  fontFamily: 'Tajawal',
  fontSizeBase: 14,
  systemNameAr: 'نظام واعد الطبي',
  systemNameEn: 'TBA WAAD System'
};

const DEFAULT_FLAGS = {
  PROVIDER_PORTAL_ENABLED: false,
  DIRECT_CLAIM_SUBMISSION_ENABLED: false,
  BATCH_CLAIMS_ENABLED: true
};

// ─── context ──────────────────────────────────────────────────────────────

const SystemConfigContext = createContext(null);

export function SystemConfigProvider({ children }) {
  const [uiConfig, setUiConfig] = useState(DEFAULT_UI_CONFIG);
  const [flags, setFlags] = useState(DEFAULT_FLAGS);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const cached = readCache();
    if (cached) {
      setUiConfig(cached.uiConfig);
      setFlags(cached.flags);
      setLoading(false);
      return;
    }

    try {
      const [uiCfgResult, flagsResult] = await Promise.allSettled([
        featureFlagsService.getUiConfig(),
        featureFlagsService.getPublicFlags()
      ]);

      const resolvedUi = uiCfgResult.status === 'fulfilled' ? uiCfgResult.value : DEFAULT_UI_CONFIG;
      const resolvedFlags = flagsResult.status === 'fulfilled' ? flagsToMap(flagsResult.value) : DEFAULT_FLAGS;

      setUiConfig(resolvedUi);
      setFlags(resolvedFlags);
      writeCache({ uiConfig: resolvedUi, flags: resolvedFlags });
    } catch {
      // Network failure — silently use defaults
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** Force a cache-busting reload — updates ALL consumers (async, needs API round-trip) */
  const refresh = useCallback(() => {
    sessionStorage.removeItem(CACHE_KEY);
    load();
  }, [load]);

  /**
   * Instantly apply flag overrides without waiting for API.
   * Use after admin saves/toggles a flag to get zero-delay UI update.
   * Also invalidates the cache so next load() reads fresh data.
   */
  const applyFlags = useCallback((updates) => {
    setFlags((prev) => {
      const next = { ...prev, ...updates };
      // Keep cache consistent so a future load() won't overwrite with stale data
      try {
        const raw = sessionStorage.getItem(CACHE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          parsed.data.flags = next;
          sessionStorage.setItem(CACHE_KEY, JSON.stringify(parsed));
        }
      } catch { /* ignore */ }
      return next;
    });
  }, []);

  /**
   * Instantly apply uiConfig overrides without waiting for API.
   * Use after admin saves company/branding to get zero-delay UI update.
   */
  const applyUiConfig = useCallback((updates) => {
    setUiConfig((prev) => {
      const next = { ...prev, ...updates };
      try {
        const raw = sessionStorage.getItem(CACHE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          parsed.data.uiConfig = next;
          sessionStorage.setItem(CACHE_KEY, JSON.stringify(parsed));
        }
      } catch { /* ignore */ }
      return next;
    });
  }, []);

  return (
    <SystemConfigContext.Provider value={{ uiConfig, flags, loading, refresh, applyFlags, applyUiConfig }}>
      {children}
    </SystemConfigContext.Provider>
  );
}

SystemConfigProvider.propTypes = { children: PropTypes.node.isRequired };

export function useSystemConfigContext() {
  const ctx = useContext(SystemConfigContext);
  if (!ctx) throw new Error('useSystemConfigContext must be used within SystemConfigProvider');
  return ctx;
}

export default SystemConfigContext;
