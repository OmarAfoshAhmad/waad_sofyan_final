/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║           MEDICAL PROVIDER THEME - Unified Color System                      ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  PURPOSE: Unified color palette for all Provider Portal pages                ║
 * ║  CREATED: 2026-01-29                                                         ║
 * ║  SCOPE: Provider Portal ONLY (not admin pages)                               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * 🎨 Medical Color Palette:
 * ├── Primary (Medical Blue):  #0D47A1 - Headers, Primary Buttons
 * ├── Secondary (Medical Green): #2E7D32 - Success states, Approved
 * ├── Info:     #0288D1 - Information alerts
 * ├── Warning:  #F9A825 - Needs action
 * ├── Error:    #C62828 - Rejected, Errors
 * ├── Background: #F8FAFC (light) / #121212 (dark)
 * └── Card:     #FFFFFF (light) / #1E1E1E (dark)
 */

// ══════════════════════════════════════════════════════════════════════════════
// MEDICAL COLOR PALETTE
// ══════════════════════════════════════════════════════════════════════════════
export const MEDICAL_COLORS = {
  // Primary - Medical Blue (Professional Healthcare)
  primary: {
    main: '#0D47A1',
    light: '#5472D3',
    dark: '#002171',
    lighter: '#E3F2FD',
    contrastText: '#FFFFFF'
  },

  // Secondary - Medical Green (Success, Approved)
  secondary: {
    main: '#2E7D32',
    light: '#60AD5E',
    dark: '#005005',
    lighter: '#E8F5E9',
    contrastText: '#FFFFFF'
  },

  // Info - Information
  info: {
    main: '#0288D1',
    light: '#5EB8FF',
    dark: '#005B9F',
    lighter: '#E1F5FE',
    contrastText: '#FFFFFF'
  },

  // Warning - Needs Action
  warning: {
    main: '#F9A825',
    light: '#FFD95A',
    dark: '#C17900',
    lighter: '#FFF8E1',
    contrastText: '#212121'
  },

  // Error - Rejected, Errors
  error: {
    main: '#C62828',
    light: '#FF5F52',
    dark: '#8E0000',
    lighter: '#FFEBEE',
    contrastText: '#FFFFFF'
  },

  // Grey Scale
  grey: {
    50: '#FAFAFA',
    100: '#F5F5F5',
    200: '#EEEEEE',
    300: '#E0E0E0',
    400: '#BDBDBD',
    500: '#9E9E9E',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121'
  }
};

// ══════════════════════════════════════════════════════════════════════════════
// LIGHT THEME
// ══════════════════════════════════════════════════════════════════════════════
export const LIGHT_THEME = {
  mode: 'light',

  // Main Colors
  primary: MEDICAL_COLORS.primary.main,
  primaryLight: MEDICAL_COLORS.primary.light,
  primaryDark: MEDICAL_COLORS.primary.dark,
  primaryLighter: MEDICAL_COLORS.primary.lighter,

  secondary: MEDICAL_COLORS.secondary.main,
  secondaryLight: MEDICAL_COLORS.secondary.light,
  secondaryLighter: MEDICAL_COLORS.secondary.lighter,

  info: MEDICAL_COLORS.info.main,
  infoLight: MEDICAL_COLORS.info.light,
  infoLighter: MEDICAL_COLORS.info.lighter,

  warning: MEDICAL_COLORS.warning.main,
  warningLight: MEDICAL_COLORS.warning.light,
  warningLighter: MEDICAL_COLORS.warning.lighter,

  error: MEDICAL_COLORS.error.main,
  errorLight: MEDICAL_COLORS.error.light,
  errorLighter: MEDICAL_COLORS.error.lighter,

  // Background & Surface
  background: '#F8FAFC',
  paper: '#FFFFFF',
  cardBg: '#FFFFFF',

  // Text
  textPrimary: '#1F2937',
  textSecondary: '#6B7280',
  textDisabled: '#9CA3AF',

  // Borders & Dividers
  border: '#E0E0E0',
  divider: '#E5E7EB',

  // Table
  tableHeader: '#0D47A1',
  tableHeaderText: '#FFFFFF',
  tableRowHover: '#F1F5F9',
  tableRowAlt: '#FAFAFA',

  // Status Colors
  statusApproved: MEDICAL_COLORS.secondary.main,
  statusPending: MEDICAL_COLORS.info.main,
  statusRejected: MEDICAL_COLORS.error.main,
  statusDraft: MEDICAL_COLORS.grey[500]
};

// ══════════════════════════════════════════════════════════════════════════════
// DARK THEME
// ══════════════════════════════════════════════════════════════════════════════
export const DARK_THEME = {
  mode: 'dark',

  // Main Colors (Lighter for visibility on dark)
  primary: '#90CAF9',
  primaryLight: '#BBDEFB',
  primaryDark: '#42A5F5',
  primaryLighter: 'rgba(144, 202, 249, 0.12)',

  secondary: '#81C784',
  secondaryLight: '#A5D6A7',
  secondaryLighter: 'rgba(129, 199, 132, 0.12)',

  info: '#4FC3F7',
  infoLight: '#81D4FA',
  infoLighter: 'rgba(79, 195, 247, 0.12)',

  warning: '#FFD54F',
  warningLight: '#FFE082',
  warningLighter: 'rgba(255, 213, 79, 0.12)',

  error: '#EF5350',
  errorLight: '#E57373',
  errorLighter: 'rgba(239, 83, 80, 0.12)',

  // Background & Surface
  background: '#121212',
  paper: '#1E1E1E',
  cardBg: '#1E1E1E',

  // Text
  textPrimary: '#E5E7EB',
  textSecondary: '#9CA3AF',
  textDisabled: '#6B7280',

  // Borders & Dividers
  border: '#374151',
  divider: '#374151',

  // Table
  tableHeader: '#1E3A5F',
  tableHeaderText: '#E5E7EB',
  tableRowHover: '#2D3748',
  tableRowAlt: '#1A202C',

  // Status Colors
  statusApproved: '#81C784',
  statusPending: '#4FC3F7',
  statusRejected: '#EF5350',
  statusDraft: '#9CA3AF'
};

// ══════════════════════════════════════════════════════════════════════════════
// STATUS CHIP COLORS (Unified for all pages)
// ══════════════════════════════════════════════════════════════════════════════
export const STATUS_COLORS = {
  // Claim & Pre-Auth Status
  SUBMITTED: { bg: MEDICAL_COLORS.info.lighter, color: MEDICAL_COLORS.info.main, label: 'مُقدَّم' },
  PENDING: { bg: MEDICAL_COLORS.info.lighter, color: MEDICAL_COLORS.info.main, label: 'قيد الانتظار' },
  PENDING_REVIEW: { bg: MEDICAL_COLORS.warning.lighter, color: MEDICAL_COLORS.warning.dark, label: 'قيد المراجعة' },
  IN_REVIEW: { bg: MEDICAL_COLORS.warning.lighter, color: MEDICAL_COLORS.warning.dark, label: 'قيد المراجعة' },
  APPROVED: { bg: MEDICAL_COLORS.secondary.lighter, color: MEDICAL_COLORS.secondary.main, label: 'مقبول' },
  REJECTED: { bg: MEDICAL_COLORS.error.lighter, color: MEDICAL_COLORS.error.main, label: 'مرفوض' },
  CANCELLED: { bg: MEDICAL_COLORS.grey[200], color: MEDICAL_COLORS.grey[700], label: 'ملغي' },
  DRAFT: { bg: MEDICAL_COLORS.grey[200], color: MEDICAL_COLORS.grey[600], label: 'مسودة' },
  COMPLETED: { bg: MEDICAL_COLORS.secondary.lighter, color: MEDICAL_COLORS.secondary.main, label: 'مكتمل' },
  EXPIRED: { bg: MEDICAL_COLORS.grey[300], color: MEDICAL_COLORS.grey[700], label: 'منتهي' },

  // Eligibility Status
  ELIGIBLE: { bg: MEDICAL_COLORS.secondary.lighter, color: MEDICAL_COLORS.secondary.main, label: 'مؤهل' },
  NOT_ELIGIBLE: { bg: MEDICAL_COLORS.error.lighter, color: MEDICAL_COLORS.error.main, label: 'غير مؤهل' },
  PARTIAL: { bg: MEDICAL_COLORS.warning.lighter, color: MEDICAL_COLORS.warning.dark, label: 'جزئي' },

  // Visit Status
  CHECKED_IN: { bg: MEDICAL_COLORS.info.lighter, color: MEDICAL_COLORS.info.main, label: 'تم التسجيل' },
  IN_PROGRESS: { bg: MEDICAL_COLORS.warning.lighter, color: MEDICAL_COLORS.warning.dark, label: 'جاري' },

  // Default
  DEFAULT: { bg: MEDICAL_COLORS.grey[200], color: MEDICAL_COLORS.grey[600], label: 'غير محدد' }
};

/**
 * Get status color configuration
 * @param {string} status - Status string
 * @returns {object} - { bg, color, label }
 */
export const getStatusColor = (status) => {
  if (!status) return STATUS_COLORS.DEFAULT;
  const upperStatus = status.toUpperCase().replace(/[- ]/g, '_');
  return STATUS_COLORS[upperStatus] || STATUS_COLORS.DEFAULT;
};

// ══════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Get theme based on mode
 * @param {string} mode - 'light' or 'dark'
 * @returns {object} - Theme object
 */
export const getProviderTheme = (mode = 'light') => {
  return mode === 'dark' ? DARK_THEME : LIGHT_THEME;
};

/**
 * CSS Variables for Provider Portal
 * Apply these to root element for CSS-based theming
 */
export const getProviderCSSVariables = (mode = 'light') => {
  const theme = getProviderTheme(mode);
  return {
    '--provider-primary': theme.primary,
    '--provider-primary-light': theme.primaryLight,
    '--provider-primary-dark': theme.primaryDark,
    '--provider-primary-lighter': theme.primaryLighter,
    '--provider-secondary': theme.secondary,
    '--provider-secondary-light': theme.secondaryLight,
    '--provider-secondary-lighter': theme.secondaryLighter,
    '--provider-info': theme.info,
    '--provider-info-lighter': theme.infoLighter,
    '--provider-warning': theme.warning,
    '--provider-warning-lighter': theme.warningLighter,
    '--provider-error': theme.error,
    '--provider-error-lighter': theme.errorLighter,
    '--provider-bg': theme.background,
    '--provider-paper': theme.paper,
    '--provider-card-bg': theme.cardBg,
    '--provider-text-primary': theme.textPrimary,
    '--provider-text-secondary': theme.textSecondary,
    '--provider-border': theme.border,
    '--provider-divider': theme.divider,
    '--provider-table-header': theme.tableHeader,
    '--provider-table-header-text': theme.tableHeaderText,
    '--provider-table-row-hover': theme.tableRowHover
  };
};

// ══════════════════════════════════════════════════════════════════════════════
// MUI THEME OVERRIDES FOR PROVIDER PORTAL
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Get MUI sx props for consistent styling
 */
export const providerStyles = {
  // Page Container
  pageContainer: {
    maxWidth: '87.5rem',
    mx: 'auto',
    px: { xs: 2, sm: 3 }
  },

  // Card Styles
  card: {
    borderRadius: '0.25rem',
    border: '1px solid',
    borderColor: 'divider',
    boxShadow: 'none',
    '&:hover': {
      boxShadow: 1
    }
  },

  // Info Card (Read-only data)
  infoCard: (color = 'info') => ({
    borderRadius: '0.25rem',
    border: '1px solid',
    borderColor: 'divider',
    bgcolor: (theme) => (theme.palette.mode === 'dark' ? `${color}.darker` : `${color}.lighter`),
    '&:hover': {
      boxShadow: 1
    }
  }),

  // Table Header
  tableHeader: {
    bgcolor: MEDICAL_COLORS.primary.main,
    '& .MuiTableCell-head': {
      color: '#FFFFFF',
      fontWeight: 600,
      fontSize: '0.875rem',
      borderBottom: 'none'
    }
  },

  // Table Header (Dark Mode Compatible)
  tableHeaderDynamic: (theme) => ({
    bgcolor: theme.palette.mode === 'dark' ? '#1E3A5F' : MEDICAL_COLORS.primary.main,
    '& .MuiTableCell-head': {
      color: '#FFFFFF',
      fontWeight: 600,
      fontSize: '0.875rem',
      borderBottom: 'none'
    }
  }),

  // Section Header
  sectionHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    mb: '1.25rem'
  },

  // Section Icon Box
  sectionIcon: (color = 'primary') => ({
    width: '2.5rem',
    height: '2.5rem',
    borderRadius: '0.25rem',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    bgcolor: (theme) => (theme.palette.mode === 'dark' ? `${color}.dark` : `${color}.lighter`),
    color: `${color}.main`
  }),

  // Primary Button
  primaryButton: {
    borderRadius: '0.25rem',
    px: '1.5rem',
    py: 1,
    fontWeight: 600,
    textTransform: 'none',
    boxShadow: 2,
    '&:hover': {
      boxShadow: 4
    }
  },

  // Outlined Button
  outlinedButton: {
    borderRadius: '0.25rem',
    px: '1.5rem',
    py: 1,
    fontWeight: 500,
    textTransform: 'none'
  },

  // Sticky Action Bar
  stickyActionBar: {
    p: '1.25rem',
    borderRadius: '0.25rem',
    bgcolor: 'background.paper',
    position: 'sticky',
    bottom: '8.0rem',
    zIndex: 10,
    border: '1px solid',
    borderColor: 'divider',
    boxShadow: 3
  },

  // Status Chip
  statusChip: (status) => {
    const colors = getStatusColor(status);
    return {
      bgcolor: colors.bg,
      color: colors.color,
      fontWeight: 600,
      fontSize: '0.75rem',
      borderRadius: 1
    };
  }
};

export default {
  MEDICAL_COLORS,
  LIGHT_THEME,
  DARK_THEME,
  STATUS_COLORS,
  getStatusColor,
  getProviderTheme,
  getProviderCSSVariables,
  providerStyles
};



