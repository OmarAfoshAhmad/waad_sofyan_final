// ==============================|| THEME CONSTANT ||============================== //

export const twitterColor = '#1DA1F2';
export const facebookColor = '#3b5998';
export const linkedInColor = '#0e76a8';

export const APP_DEFAULT_PATH = '/dashboard';
export const HORIZONTAL_MAX_ITEM = 7;
export const DRAWER_WIDTH = '16.25rem';
export const MINI_DRAWER_WIDTH = '3.75rem';

export const CSS_VAR_PREFIX = '';

export let SimpleLayoutType;

(function (SimpleLayoutType) {
  SimpleLayoutType['SIMPLE'] = 'simple';
  SimpleLayoutType['LANDING'] = 'landing';
})(SimpleLayoutType || (SimpleLayoutType = {}));

export let ThemeMode;

(function (ThemeMode) {
  ThemeMode['LIGHT'] = 'light';
  ThemeMode['DARK'] = 'dark';
  ThemeMode['SYSTEM'] = 'system';
})(ThemeMode || (ThemeMode = {}));

export let MenuOrientation;

(function (MenuOrientation) {
  MenuOrientation['VERTICAL'] = 'vertical';
  MenuOrientation['HORIZONTAL'] = 'horizontal';
  MenuOrientation['MINI_VERTICAL'] = 'mini-vertical';
})(MenuOrientation || (MenuOrientation = {}));

export let ThemeDirection;

(function (ThemeDirection) {
  ThemeDirection['LTR'] = 'ltr';
  ThemeDirection['RTL'] = 'rtl';
})(ThemeDirection || (ThemeDirection = {}));

export let NavActionType;

(function (NavActionType) {
  NavActionType['FUNCTION'] = 'function';
  NavActionType['LINK'] = 'link';
})(NavActionType || (NavActionType = {}));

export let Gender;

(function (Gender) {
  Gender['MALE'] = 'Male';
  Gender['FEMALE'] = 'Female';
})(Gender || (Gender = {}));

export let DropzoneType;

(function (DropzoneType) {
  DropzoneType['DEFAULT'] = 'default';
  DropzoneType['STANDARD'] = 'standard';
})(DropzoneType || (DropzoneType = {}));

export let AuthProvider;

(function (AuthProvider) {
  AuthProvider['JWT'] = 'jwt';
  AuthProvider['FIREBASE'] = 'firebase';
  AuthProvider['AUTH0'] = 'auth0';
  AuthProvider['AWS'] = 'aws';
  AuthProvider['SUPABASE'] = 'supabase';
})(AuthProvider || (AuthProvider = {}));

export const APP_AUTH = AuthProvider.JWT;
export const DEFAULT_THEME_MODE = ThemeMode.LIGHT;

// ==============================|| THEME CONFIG ||============================== //

/**
 * TBA WAAD System - Fixed Theme Configuration
 *
 * These settings define the professional look and feel of the system.
 * They should NOT be changed by users to maintain consistent UX.
 *
 * Fixed Settings:
 * - menuOrientation: VERTICAL (Sidebar Navigation)
 * - themeDirection: RTL (يمين لليسار)
 * - presetColor: theme8 (سمة 8 - اللون الأخضر التيل)
 * - container: false (مرن - Full width)
 */
const config = {
  fontFamily: `'Cairo', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', sans-serif`,
  i18n: 'ar', // Phase D1.5: Arabic as default language
  menuOrientation: MenuOrientation.VERTICAL, // Fixed: Sidebar navigation (عمودي)
  container: false, // Fixed: Fluid width (مرن)
  presetColor: 'theme8', // Fixed: Theme 8 (سمة 8 - Teal color)
  themeDirection: ThemeDirection.RTL // Fixed: RTL for Arabic (يمين لليسار)
};

export default config;

// ==============================|| PROJECT SETTINGS - TBA SYSTEM ||============================== //

export const projectSettings = {
  // Control visibility of non-TBA menu items (Mantis template components)
  showEcommerce: false,
  showChat: false,
  showKanban: false,
  showWidgets: false,
  showCustomer: false,
  showAnalytics: false,
  showCharts: false,
  showCalendar: false,
  showInvoice: false,
  showProfiles: false,

  // Keep useful tools visible
  showTools: true,
  showAdministration: true,

  // TBA modules are always visible
  showTBAManagement: true,

  // ═══════════════════════════════════════════════════════════
  // WAAD FEATURE FLAGS (Phase 10)
  // Re-enable these to activate direct claim submission portal
  // ═══════════════════════════════════════════════════════════
  features: {
    // fasle = Hide all Provider Portal direct entry points
    providerPortalEnabled: false,
    // false = Hide "Claim" and "Pre-Auth" buttons in Visit List
    directClaimSubmissionEnabled: false
  }
};
