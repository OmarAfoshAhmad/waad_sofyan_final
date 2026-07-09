/**
 * Company Service — localStorage-based (no PDF API)
 *
 * Stores company settings in localStorage under key 'companySettings'.
 * The /settings/company page reads/writes through this service.
 * CompanySettingsContext also reads from the same localStorage key.
 *
 * @updated 2026-02-25 — Removed PDF API dependency
 */

const STORAGE_KEY = 'companySettings';

const DEFAULT_COMPANY = {
  id: 1,
  name: 'وعد',
  code: 'WAAD',
  active: true,
  isDefault: true,
  logoUrl: null,
  businessType: 'إدارة النفقات الطبية',
  phone: '',
  email: '',
  address: '',
  website: '',
  taxNumber: '',
  createdAt: null,
  updatedAt: null
};

/**
 * Load from localStorage
 */
function loadCompany() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      return {
        ...DEFAULT_COMPANY,
        name: parsed.companyName || DEFAULT_COMPANY.name,
        logoUrl: parsed.logoUrl || parsed.logoBase64 || DEFAULT_COMPANY.logoUrl,
        businessType: parsed.businessType || DEFAULT_COMPANY.businessType,
        phone: parsed.phone || DEFAULT_COMPANY.phone,
        email: parsed.email || DEFAULT_COMPANY.email,
        address: parsed.address || DEFAULT_COMPANY.address,
        website: parsed.website || DEFAULT_COMPANY.website,
        taxNumber: parsed.taxNumber || DEFAULT_COMPANY.taxNumber
      };
    }
  } catch (e) {
    console.warn('[CompanyService] Failed to load from localStorage:', e);
  }
  return DEFAULT_COMPANY;
}

/**
 * Save to localStorage
 */
function saveCompany(data) {
  try {
    const existing = loadCompany();
    const merged = { ...existing, ...data };
    // Save in the format CompanySettingsContext expects
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        companyName: merged.name,
        companyNameEn: merged.code || 'Waad TPA',
        businessType: merged.businessType,
        businessTypeEn: 'Medical Claims Management',
        logoUrl: merged.logoUrl,
        logoBase64: null,
        primaryColor: '#1976d2',
        secondaryColor: '#42a5f5',
        headerStyle: 'gradient',
        phone: merged.phone,
        email: merged.email,
        address: merged.address,
        website: merged.website,
        taxNumber: merged.taxNumber,
        footerText: 'جميع الحقوق محفوظة © 2026 - نظام وعد لإدارة النفقات الطبية',
        footerTextEn: 'All Rights Reserved © 2026'
      })
    );
    return merged;
  } catch (e) {
    console.warn('[CompanyService] Failed to save to localStorage:', e);
    return data;
  }
}

// ============================================================================

const getPublicSettings = async () => {
  return { data: loadCompany() };
};

const getDefaultCompany = async () => {
  return {
    success: true,
    message: 'Company loaded successfully',
    data: loadCompany()
  };
};

const updateDefaultCompany = async (data) => {
  const saved = saveCompany(data);
  return {
    success: true,
    message: 'Company updated successfully',
    data: saved
  };
};

const getAll = async () => ({
  success: true,
  data: [loadCompany()]
});

const getById = async () => ({
  success: true,
  data: loadCompany()
});

const getByCode = async () => getDefaultCompany();

const create = async (data) => {
  const saved = saveCompany(data);
  return { success: true, data: saved };
};

const update = async (id, data) => {
  return updateDefaultCompany({ id, ...data });
};

const remove = async () => {};

const activate = async () => ({
  success: true,
  data: { ...loadCompany(), active: true }
});

const deactivate = async () => ({
  success: true,
  data: { ...loadCompany(), active: false }
});

// ============================================================================

export const companyService = {
  getPublicSettings,
  getDefaultCompany,
  updateDefaultCompany,
  getSystemCompany: getDefaultCompany,
  getAll,
  getById,
  getByCode,
  create,
  update,
  delete: remove,
  activate,
  deactivate
};

export default companyService;
