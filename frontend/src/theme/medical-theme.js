/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 MEDICAL THEME - Professional Healthcare UI Colors & Styles
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Design Principles:
 * ✓ High Contrast for readability
 * ✓ Calm colors to reduce cognitive load
 * ✓ Medical-grade color coding
 * ✓ No distracting gradients
 * ✓ Desktop-first (1280px+)
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

export const MEDICAL_COLORS = {
  // ═══════════════════════════════════════════════════════════════════════
  // Primary Colors (Medical Blue/Teal)
  // ═══════════════════════════════════════════════════════════════════════
  primary: {
    main: '#0288D1', // Medical Blue
    light: '#4FC3F7', // Light Blue
    dark: '#01579B', // Dark Blue
    contrast: '#FFFFFF'
  },

  secondary: {
    main: '#00897B', // Medical Teal
    light: '#4DB6AC', // Light Teal
    dark: '#00695C', // Dark Teal
    contrast: '#FFFFFF'
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Status Colors (Medical-Grade)
  // ═══════════════════════════════════════════════════════════════════════
  status: {
    approved: {
      main: '#2E7D32', // Calm Green
      light: '#66BB6A',
      bg: '#E8F5E9',
      text: '#1B5E20'
    },
    rejected: {
      main: '#C62828', // Soft Red
      light: '#EF5350',
      bg: '#FFEBEE',
      text: '#B71C1C'
    },
    pending: {
      main: '#F57C00', // Amber
      light: '#FFB74D',
      bg: '#FFF3E0',
      text: '#E65100'
    },
    hold: {
      main: '#5E35B1', // Purple
      light: '#9575CD',
      bg: '#EDE7F6',
      text: '#4527A0'
    },
    processing: {
      main: '#1976D2', // Blue
      light: '#64B5F6',
      bg: '#E3F2FD',
      text: '#0D47A1'
    }
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Alert/Warning Colors
  // ═══════════════════════════════════════════════════════════════════════
  alert: {
    error: '#D32F2F',
    warning: '#F57C00',
    info: '#0288D1',
    success: '#388E3C'
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Neutral Colors
  // ═══════════════════════════════════════════════════════════════════════
  neutral: {
    white: '#FFFFFF',
    lightest: '#FAFAFA',
    lighter: '#F5F5F5',
    light: '#EEEEEE',
    medium: '#BDBDBD',
    dark: '#757575',
    darker: '#424242',
    darkest: '#212121',
    black: '#000000'
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Document Type Colors
  // ═══════════════════════════════════════════════════════════════════════
  documentType: {
    lab: '#1976D2', // Blue
    prescription: '#7B1FA2', // Purple
    imaging: '#0097A7', // Cyan
    report: '#00796B', // Teal
    invoice: '#F57C00', // Orange
    other: '#616161' // Gray
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Background Colors
  // ═══════════════════════════════════════════════════════════════════════
  background: {
    default: '#FAFAFA',
    paper: '#FFFFFF',
    sidebar: '#F5F5F5',
    header: '#FFFFFF',
    footer: '#F5F5F5',
    hover: '#F5F5F5',
    selected: '#E3F2FD',
    disabled: '#EEEEEE'
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Border Colors
  // ═══════════════════════════════════════════════════════════════════════
  border: {
    light: '#E0E0E0',
    medium: '#BDBDBD',
    dark: '#9E9E9E',
    focus: '#0288D1'
  },

  // ═══════════════════════════════════════════════════════════════════════
  // Text Colors
  // ═══════════════════════════════════════════════════════════════════════
  text: {
    primary: '#212121',
    secondary: '#757575',
    disabled: '#BDBDBD',
    hint: '#9E9E9E',
    link: '#0288D1'
  }
};

// ═══════════════════════════════════════════════════════════════════════
// Typography
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_TYPOGRAPHY = {
  fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',

  sizes: {
    xs: '0.75rem', // 12px
    sm: '0.875rem', // 14px
    base: '1rem', // 16px
    lg: '1.125rem', // 18px
    xl: '1.25rem', // 20px
    '2xl': '1.5rem', // 24px
    '3xl': '1.875rem' // 30px
  },

  weights: {
    light: 300,
    normal: 400,
    medium: '250.0rem',
    semibold: 600,
    bold: 700
  },

  lineHeights: {
    tight: 1.25,
    normal: 1.5,
    relaxed: 1.75
  }
};

// ═══════════════════════════════════════════════════════════════════════
// Spacing (8px base)
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_SPACING = {
  xs: '4px',
  sm: '0.375rem',
  md: '16px',
  lg: '24px',
  xl: '32px',
  '2xl': '48px',
  '3xl': '64px'
};

// ═══════════════════════════════════════════════════════════════════════
// Shadows (Medical-grade - subtle)
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_SHADOWS = {
  none: 'none',
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
  base: '0 1px 3px 0 rgba(0, 0, 0, 0.1)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1)'
};

// ═══════════════════════════════════════════════════════════════════════
// Border Radius
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_RADIUS = {
  none: '0',
  sm: '0.375rem',
  base: '8px',
  md: '12px',
  lg: '16px',
  full: '9999px'
};

// ═══════════════════════════════════════════════════════════════════════
// Z-Index Layers
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_Z_INDEX = {
  base: 0,
  dropdown: 1000,
  sticky: 1100,
  fixed: 1200,
  modal: 1300,
  popover: 1400,
  tooltip: '750.0rem'
};

// ═══════════════════════════════════════════════════════════════════════
// Breakpoints (Desktop-First)
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_BREAKPOINTS = {
  desktop: '80.0rem', // Minimum width
  wide: '1440px',
  ultrawide: '1920px'
};

// ═══════════════════════════════════════════════════════════════════════
// Component-Specific Styles
// ═══════════════════════════════════════════════════════════════════════

export const MEDICAL_COMPONENTS = {
  // Split Screen Layout
  splitScreen: {
    leftWidth: '60%',
    rightWidth: '40%',
    minWidth: '80.0rem',
    gap: '0' // No gap for seamless experience
  },

  // Header
  header: {
    height: '4.0rem',
    padding: '16px 24px',
    background: MEDICAL_COLORS.background.header,
    borderBottom: `1px solid ${MEDICAL_COLORS.border.light}`,
    position: 'sticky',
    top: 0,
    zIndex: MEDICAL_Z_INDEX.sticky
  },

  // Footer
  footer: {
    height: '4.5rem',
    padding: '16px 24px',
    background: MEDICAL_COLORS.background.footer,
    borderTop: `1px solid ${MEDICAL_COLORS.border.light}`,
    position: 'sticky',
    bottom: 0,
    zIndex: MEDICAL_Z_INDEX.sticky
  },

  // Document Viewer
  documentViewer: {
    background: MEDICAL_COLORS.neutral.darkest,
    padding: '1.5rem',
    maxHeight: 'calc(100vh - 200px)',
    overflow: 'auto'
  },

  // Cards
  card: {
    background: MEDICAL_COLORS.background.paper,
    borderRadius: MEDICAL_RADIUS.base,
    padding: '1.0rem',
    boxShadow: MEDICAL_SHADOWS.sm,
    border: `1px solid ${MEDICAL_COLORS.border.light}`
  }
};

// ═══════════════════════════════════════════════════════════════════════
// Keyboard Shortcuts Colors
// ═══════════════════════════════════════════════════════════════════════
export const KEYBOARD_HINT_STYLE = {
  background: MEDICAL_COLORS.neutral.darker,
  color: MEDICAL_COLORS.neutral.white,
  padding: '2px 6px',
  borderRadius: MEDICAL_RADIUS.sm,
  fontSize: MEDICAL_TYPOGRAPHY.sizes.xs,
  fontWeight: MEDICAL_TYPOGRAPHY.weights.medium,
  marginLeft: '0.375rem'
};

// ═══════════════════════════════════════════════════════════════════════
// Export Default Theme Object
// ═══════════════════════════════════════════════════════════════════════
export const MEDICAL_THEME = {
  colors: MEDICAL_COLORS,
  typography: MEDICAL_TYPOGRAPHY,
  spacing: MEDICAL_SPACING,
  shadows: MEDICAL_SHADOWS,
  radius: MEDICAL_RADIUS,
  zIndex: MEDICAL_Z_INDEX,
  breakpoints: MEDICAL_BREAKPOINTS,
  components: MEDICAL_COMPONENTS,
  keyboardHint: KEYBOARD_HINT_STYLE
};

export default MEDICAL_THEME;


