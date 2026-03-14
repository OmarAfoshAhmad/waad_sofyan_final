/**
 * Tailwind CSS-inspired MUI Theme
 * Mimics Tailwind's color palette and design system
 *
 * Color Reference:
 * - Tailwind Slate for neutral grays
 * - Tailwind Blue for primary
 * - Tailwind Emerald for success
 * - Tailwind Amber for warning
 * - Tailwind Red for error
 * - Tailwind Sky for info
 */

import { createTheme, alpha } from '@mui/material/styles';

// ==============================|| TAILWIND COLOR PALETTE ||============================== //

const tailwindColors = {
  // Slate (Neutral Gray)
  slate: {
    50: '#f8fafc',
    100: '#f1f5f9',
    200: '#e2e8f0',
    300: '#cbd5e1',
    400: '#94a3b8',
    500: '#64748b',
    600: '#475569',
    700: '#334155',
    800: '#1e293b',
    900: '#0f172a',
    950: '#020617'
  },

  // Blue (Primary)
  blue: {
    50: '#eff6ff',
    100: '#dbeafe',
    200: '#bfdbfe',
    300: '#93c5fd',
    400: '#60a5fa',
    500: '#3b82f6',
    600: '#2563eb',
    700: '#1d4ed8',
    800: '#1e40af',
    900: '#1e3a8a',
    950: '#172554'
  },

  // Emerald (Success)
  emerald: {
    50: '#ecfdf5',
    100: '#d1fae5',
    200: '#a7f3d0',
    300: '#6ee7b7',
    400: '#34d399',
    500: '#10b981',
    600: '#059669',
    700: '#047857',
    800: '#065f46',
    900: '#064e3b',
    950: '#022c22'
  },

  // Amber (Warning)
  amber: {
    50: '#fffbeb',
    100: '#fef3c7',
    200: '#fde68a',
    300: '#fcd34d',
    400: '#fbbf24',
    500: '#f59e0b',
    600: '#d97706',
    700: '#b45309',
    800: '#92400e',
    900: '#78350f',
    950: '#451a03'
  },

  // Red (Error)
  red: {
    50: '#fef2f2',
    100: '#fee2e2',
    200: '#fecaca',
    300: '#fca5a5',
    400: '#f87171',
    500: '#ef4444',
    600: '#dc2626',
    700: '#b91c1c',
    800: '#991b1b',
    900: '#7f1d1d',
    950: '#450a0a'
  },

  // Sky (Info)
  sky: {
    50: '#f0f9ff',
    100: '#e0f2fe',
    200: '#bae6fd',
    300: '#7dd3fc',
    400: '#38bdf8',
    500: '#0ea5e9',
    600: '#0284c7',
    700: '#0369a1',
    800: '#075985',
    900: '#0c4a6e',
    950: '#082f49'
  }
};

// ==============================|| MUI THEME WITH TAILWIND COLORS ||============================== //

const tailwindTheme = createTheme({
  palette: {
    mode: 'light',

    // Primary - Tailwind Blue
    primary: {
      lighter: tailwindColors.blue[50],
      light: tailwindColors.blue[300],
      main: tailwindColors.blue[600],
      dark: tailwindColors.blue[700],
      darker: tailwindColors.blue[900],
      contrastText: '#ffffff'
    },

    // Secondary - Tailwind Slate
    secondary: {
      lighter: tailwindColors.slate[100],
      light: tailwindColors.slate[300],
      main: tailwindColors.slate[600],
      dark: tailwindColors.slate[700],
      darker: tailwindColors.slate[900],
      contrastText: '#ffffff'
    },

    // Success - Tailwind Emerald
    success: {
      lighter: tailwindColors.emerald[50],
      light: tailwindColors.emerald[300],
      main: tailwindColors.emerald[600],
      dark: tailwindColors.emerald[700],
      darker: tailwindColors.emerald[900],
      contrastText: '#ffffff'
    },

    // Warning - Tailwind Amber
    warning: {
      lighter: tailwindColors.amber[50],
      light: tailwindColors.amber[300],
      main: tailwindColors.amber[500],
      dark: tailwindColors.amber[700],
      darker: tailwindColors.amber[900],
      contrastText: '#ffffff'
    },

    // Error - Tailwind Red
    error: {
      lighter: tailwindColors.red[50],
      light: tailwindColors.red[300],
      main: tailwindColors.red[600],
      dark: tailwindColors.red[700],
      darker: tailwindColors.red[900],
      contrastText: '#ffffff'
    },

    // Info - Tailwind Sky
    info: {
      lighter: tailwindColors.sky[50],
      light: tailwindColors.sky[300],
      main: tailwindColors.sky[500],
      dark: tailwindColors.sky[700],
      darker: tailwindColors.sky[900],
      contrastText: '#ffffff'
    },

    // Background
    background: {
      paper: '#ffffff',
      default: tailwindColors.slate[50]
    },

    // Text
    text: {
      primary: tailwindColors.slate[900],
      secondary: tailwindColors.slate[600],
      disabled: tailwindColors.slate[400]
    },

    // Divider
    divider: tailwindColors.slate[200],

    // Action
    action: {
      active: tailwindColors.slate[600],
      hover: alpha(tailwindColors.slate[500], 0.04),
      selected: alpha(tailwindColors.blue[600], 0.08),
      disabled: tailwindColors.slate[300],
      disabledBackground: tailwindColors.slate[100],
      focus: alpha(tailwindColors.blue[600], 0.12)
    }
  },

  // Typography - Tailwind-inspired font system
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      'sans-serif',
      '"Apple Color Emoji"',
      '"Segoe UI Emoji"',
      '"Segoe UI Symbol"'
    ].join(','),

    // Tailwind font sizes
    h1: {
      fontSize: '2.25rem', // text-4xl
      fontWeight: 700,
      lineHeight: 1.2
    },
    h2: {
      fontSize: '1.875rem', // text-3xl
      fontWeight: 700,
      lineHeight: 1.3
    },
    h3: {
      fontSize: '1.5rem', // text-2xl
      fontWeight: 600,
      lineHeight: 1.4
    },
    h4: {
      fontSize: '1.25rem', // text-xl
      fontWeight: 600,
      lineHeight: 1.5
    },
    h5: {
      fontSize: '1.125rem', // text-lg
      fontWeight: 600,
      lineHeight: 1.5
    },
    h6: {
      fontSize: '1rem', // text-base
      fontWeight: 600,
      lineHeight: 1.5
    },
    body1: {
      fontSize: '1rem', // text-base
      lineHeight: 1.6
    },
    body2: {
      fontSize: '0.875rem', // text-sm
      lineHeight: 1.5
    },
    caption: {
      fontSize: '0.75rem', // text-xs
      lineHeight: 1.4
    },
    button: {
      fontSize: '0.875rem',
      fontWeight: 600,
      textTransform: 'none' // Tailwind style - no uppercase
    }
  },

  // Spacing - Tailwind's 4px-based system
  spacing: 4, // Base unit = 4px (same as Tailwind)

  // Shape - Tailwind border radius
  shape: {
    borderRadius: '0.375rem' // Tailwind rounded-md (0.375rem)
  },

  // Shadows - Tailwind-inspired
  shadows: [
    'none',
    '0 1px 2px 0 rgba(0, 0, 0, 0.05)', // shadow-sm
    '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)', // shadow
    '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)', // shadow-md
    '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)', // shadow-lg
    '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)', // shadow-xl
    '0 25px 50px -12px rgba(0, 0, 0, 0.25)', // shadow-2xl
    ...Array(18).fill('none') // Fill remaining shadow levels
  ],

  // Component overrides
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: '0.375rem', // Tailwind rounded-lg
          textTransform: 'none',
          fontWeight: 600,
          padding: '8px 16px',
          boxShadow: 'none',
          '&:hover': {
            boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)'
          }
        },
        contained: {
          boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
          '&:hover': {
            boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)'
          }
        }
      }
    },

    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: '0.75rem', // Tailwind rounded-xl
          boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)'
        }
      }
    },

    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: '0.375rem', // Tailwind rounded-md
          fontWeight: 500
        }
      }
    },

    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            borderRadius: '0.375rem' // Tailwind rounded-lg
          }
        }
      }
    },

    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none'
        },
        rounded: {
          borderRadius: '0.75rem'
        }
      }
    },

    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: `1px solid ${tailwindColors.slate[200]}`
        },
        head: {
          fontWeight: 600,
          backgroundColor: alpha(tailwindColors.slate[100], 0.5)
        }
      }
    }
  }
});

// ==============================|| TAILWIND UTILITY EXPORTS ||============================== //

export const tw = {
  colors: tailwindColors,

  // Common Tailwind utilities
  shadow: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)'
  },

  rounded: {
    sm: 4,
    md: 6,
    lg: 8,
    xl: 12,
    '2xl': 16,
    full: 9999
  },

  ring: (color, opacity = 0.5) => ({
    boxShadow: `0 0 0 3px ${alpha(color, opacity)}`
  })
};

export default tailwindTheme;



