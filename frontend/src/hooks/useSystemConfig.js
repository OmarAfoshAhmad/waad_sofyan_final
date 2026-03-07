/**
 * useSystemConfig — React hook for system-wide configuration
 *
 * Fetches (once per session):
 *   - UI config: logoUrl, fontFamily, fontSizeBase, systemNameAr, systemNameEn
 *   - Feature flags: PROVIDER_PORTAL_ENABLED, BATCH_CLAIMS_ENABLED, etc.
 *
 * Results are cached in sessionStorage with a 5-minute TTL so the app
 * doesn't re-fetch on every route change.
 *
 * Usage:
 *   const { uiConfig, flags, loading } = useSystemConfig();
 *   if (flags.PROVIDER_PORTAL_ENABLED) { ... }
 */

import { useState, useEffect, useCallback } from 'react';
import featureFlagsService from 'services/api/featureFlags.service';

const CACHE_KEY   = '__sys_config__';
const CACHE_TTL   = 5 * 60 * 1000; // 5 minutes

const DEFAULT_UI_CONFIG = {
  logoUrl:      '',
  fontFamily:   'Tajawal',
  fontSizeBase: 14,
  systemNameAr: 'نظام واعد الطبي',
  systemNameEn: 'TBA WAAD System'
};

const DEFAULT_FLAGS = {
  PROVIDER_PORTAL_ENABLED:         false,
  DIRECT_CLAIM_SUBMISSION_ENABLED: false,
  BATCH_CLAIMS_ENABLED:            true
};

// ─── helpers ────────────────────────────────────────────────────────────────

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
    // sessionStorage full or unavailable — silently ignore
  }
}

/** Convert array of FeatureFlagDto to a flat key→boolean map */
function flagsToMap(flagList) {
  return flagList.reduce((acc, f) => {
    acc[f.flagKey] = f.enabled;
    return acc;
  }, { ...DEFAULT_FLAGS });
}

// ─── hook ────────────────────────────────────────────────────────────────────

export default function useSystemConfig() {
  const [uiConfig, setUiConfig] = useState(DEFAULT_UI_CONFIG);
  const [flags,    setFlags]    = useState(DEFAULT_FLAGS);
  const [loading,  setLoading]  = useState(true);

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

      const resolvedUi    = uiCfgResult.status   === 'fulfilled' ? uiCfgResult.value   : DEFAULT_UI_CONFIG;
      const resolvedFlags = flagsResult.status    === 'fulfilled' ? flagsToMap(flagsResult.value) : DEFAULT_FLAGS;

      setUiConfig(resolvedUi);
      setFlags(resolvedFlags);
      writeCache({ uiConfig: resolvedUi, flags: resolvedFlags });
    } catch {
      // Network failure — silently use defaults; the app still works
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** Force a cache-busting reload (called after admin saves settings) */
  const refresh = useCallback(() => {
    sessionStorage.removeItem(CACHE_KEY);
    load();
  }, [load]);

  return { uiConfig, flags, loading, refresh };
}
