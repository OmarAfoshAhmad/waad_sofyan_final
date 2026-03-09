/**
 * CompanySettingsContext — localStorage-based company branding
 *
 * Settings are persisted in localStorage (key: 'companySettings').
 * Editable from /settings/company page.
 * No backend API required.
 */

import { createContext, useContext, useState, useCallback } from 'react';
import PropTypes from 'prop-types';
import waadLogoDefault from '../assets/images/waad-logo.png';

export { waadLogoDefault };

const STORAGE_KEY = 'companySettings';

// Default settings (used when nothing saved yet)
const DEFAULT_SETTINGS = {
  companyName: 'وعد',
  companyNameEn: 'Waad TPA',
  businessType: 'إدارة النفقات الطبية (ليبيا)',
  businessTypeEn: 'Medical Claims Management',
  logoUrl: null,
  logoBase64: null,
  primaryColor: '#1976d2',
  secondaryColor: '#42a5f5',
  headerStyle: 'gradient',
  phone: '',
  email: '',
  address: '',
  website: '',
  footerText: 'جميع الحقوق محفوظة © 2026 - نظام Lymed (لايميد) لإدارة النفقات الطبية',
  footerTextEn: 'All Rights Reserved © 2026'
};

/**
 * Load settings from localStorage, merge with defaults
 */
function loadSettings() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      return { ...DEFAULT_SETTINGS, ...JSON.parse(stored) };
    }
  } catch (e) {
    console.warn('[CompanySettings] Failed to load from localStorage:', e);
  }
  return DEFAULT_SETTINGS;
}

// Create context
const CompanySettingsContext = createContext(null);

/**
 * CompanySettingsProvider - Wraps app to provide company settings
 * Persists to localStorage (no backend API needed)
 */
export function CompanySettingsProvider({ children }) {
  const [settings, setSettings] = useState(loadSettings);
  const loading = false;
  const error = null;

  /**
   * Update and persist settings (called from /settings/company page)
   */
  const updateSettings = useCallback((newSettings) => {
    setSettings((prev) => {
      const merged = { ...prev, ...newSettings };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
      } catch (e) {
        console.warn('[CompanySettings] Failed to save to localStorage:', e);
      }
      return merged;
    });
  }, []);

  /**
   * Refresh settings from localStorage
   */
  const refreshSettings = useCallback(() => {
    setSettings(loadSettings());
    return Promise.resolve();
  }, []);

  /**
   * Get logo source (prioritize base64 > url > fallback)
   */
  const getLogoSrc = useCallback(() => {
    if (settings.logoBase64) return settings.logoBase64;
    if (settings.logoUrl) return settings.logoUrl;
    return waadLogoDefault;
  }, [settings.logoBase64, settings.logoUrl]);

  /**
   * Reset logo to factory default (removes any custom logo from localStorage)
   */
  const resetToDefaultLogo = useCallback(() => {
    setSettings((prev) => {
      const updated = { ...prev, logoBase64: null, logoUrl: null };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      } catch (e) {
        console.warn('[CompanySettings] Failed to save to localStorage:', e);
      }
      return updated;
    });
  }, []);

  /**
   * Check if logo is available
   */
  const hasLogo = useCallback(() => {
    return !!(settings.logoBase64 || settings.logoUrl);
  }, [settings.logoBase64, settings.logoUrl]);

  /**
   * Get company initials for fallback avatar
   */
  const getInitials = useCallback(() => {
    const name = settings.companyName || settings.companyNameEn || 'TBA';
    return name.charAt(0).toUpperCase();
  }, [settings.companyName, settings.companyNameEn]);

  const value = {
    settings,
    loading,
    error,
    updateSettings,
    refreshSettings,
    resetToDefaultLogo,
    getLogoSrc,
    hasLogo,
    hasCustomLogo: !!(settings.logoBase64 || settings.logoUrl),
    getInitials,
    companyName: settings.companyName,
    companyNameEn: settings.companyNameEn,
    logoUrl: settings.logoUrl,
    primaryColor: settings.primaryColor
  };

  return <CompanySettingsContext.Provider value={value}>{children}</CompanySettingsContext.Provider>;
}

CompanySettingsProvider.propTypes = {
  children: PropTypes.node.isRequired
};

/**
 * useCompanySettings - Hook to access company settings
 */
export function useCompanySettings() {
  const context = useContext(CompanySettingsContext);

  if (!context) {
    console.warn('[useCompanySettings] Must be used within CompanySettingsProvider');
    return {
      settings: DEFAULT_SETTINGS,
      loading: false,
      error: null,
      updateSettings: () => { },
      refreshSettings: () => Promise.resolve(),
      resetToDefaultLogo: () => { },
      getLogoSrc: () => waadLogoDefault,
      hasLogo: () => false,
      hasCustomLogo: false,
      getInitials: () => 'T',
      companyName: DEFAULT_SETTINGS.companyName,
      companyNameEn: DEFAULT_SETTINGS.companyNameEn,
      logoUrl: null,
      primaryColor: DEFAULT_SETTINGS.primaryColor
    };
  }

  return context;
}

export default CompanySettingsContext;
