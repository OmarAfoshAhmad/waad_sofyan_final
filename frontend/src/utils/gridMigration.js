/**
 * MUI Grid v2 Migration Utilities
 * ================================
 *
 * Helper utilities for migrating from MUI Grid v1 (deprecated) to Grid v2
 *
 * PRODUCTION STABILIZATION (2026-01-13):
 * - Suppress MUI deprecation warnings in production
 * - Provide migration helpers
 * - Document the migration path
 *
 * MUI GRID V2 MIGRATION GUIDE:
 * ============================
 *
 * BEFORE (Grid v1 - DEPRECATED):
 * ```jsx
 * <Grid container spacing={2}>
 *   <Grid item xs={12} sm={6} md={4}>
 *     <Content />
 *   </Grid>
 * </Grid>
 * ```
 *
 * AFTER (Grid v2):
 * ```jsx
 * import Grid from '@mui/material/Grid2';
 *
 * <Grid container spacing={2}>
 *   <Grid size={{ xs: 12, sm: 6, md: 4 }}>
 *     <Content />
 *   </Grid>
 * </Grid>
 * ```
 *
 * KEY CHANGES:
 * - Remove `item` prop (no longer needed)
 * - Replace `xs`, `sm`, `md`, `lg`, `xl` with `size` prop
 * - Use `size={{ xs: 12, sm: 6 }}` object syntax
 * - For fixed size: `size={6}` (same on all breakpoints)
 * - `offset` prop replaces complex margin workarounds
 */

// ==============================|| DEPRECATION WARNING FILTER ||============================== //

/**
 * Filter out MUI deprecation warnings in production
 * Call this once in your app's entry point (main.jsx)
 */
export const suppressMUIDeprecationWarnings = () => {
  // 1. Suppress Console Warnings (MUI Deprecations & Accessibility Noise)
  const originalWarn = console.warn;
  console.warn = (...args) => {
    const message = args[0]?.toString?.() || '';

    // A. Suppress "Blocked aria-hidden" warning (Browser/MUI conflict non-critical noise)
    if (message.includes('Blocked aria-hidden on an element')) {
      return;
    }

    // B. Suppress MUI Grid & Prop deprecations
    if (message.includes('MUI: The Grid') ||
      message.includes('deprecated') ||
      message.includes('isDarkMode') ||
      message.includes('GridProps')) {
      return;
    }

    originalWarn.apply(console, args);
  };

  // 2. Suppress Emotion SSR Unsafe Selector Warning (Errors) - In ALL environments
  // This is a false positive for client-side-only apps (Vite) but Emotion flags it anyway
  const originalError = console.error;
  console.error = (...args) => {
    const message = args[0]?.toString?.() || '';
    if (message.includes('The pseudo class ":first-child" is potentially unsafe when doing server-side rendering')) {
      return; // Suppress
    }
    originalError.apply(console, args);
  };
};

// ==============================|| GRID SIZE CONVERTER ||============================== //

/**
 * Convert Grid v1 props to Grid v2 size prop
 * Useful for programmatic migration
 *
 * @param {object} v1Props - Grid v1 props { xs, sm, md, lg, xl }
 * @returns {object} Grid v2 size prop object
 *
 * @example
 * convertToV2Size({ xs: 12, sm: 6, md: 4 })
 * // Returns: { xs: 12, sm: 6, md: 4 }
 */
export const convertToV2Size = ({ xs, sm, md, lg, xl }) => {
  const size = {};
  if (xs !== undefined) size.xs = xs;
  if (sm !== undefined) size.sm = sm;
  if (md !== undefined) size.md = md;
  if (lg !== undefined) size.lg = lg;
  if (xl !== undefined) size.xl = xl;
  return Object.keys(size).length > 0 ? size : undefined;
};

// ==============================|| MIGRATION REGEX PATTERNS ||============================== //

/**
 * Regex patterns for automated migration (use with caution)
 * These can be used in IDE find/replace or codemod scripts
 */
export const MIGRATION_PATTERNS = {
  // Replace: <Grid item xs={12}  →  <Grid size={12}
  itemWithSingleSize: {
    find: /<Grid\s+item\s+(xs|sm|md|lg|xl)=\{(\d+)\}/g,
    replace: '<Grid size={$2}'
  },

  // Replace: <Grid item xs={12} sm={6}  →  <Grid size={{ xs: 12, sm: 6 }}
  // This is more complex and needs manual review
  itemWithMultipleSizes: {
    description: 'Manual migration needed for multiple breakpoint sizes'
  },

  // Replace: import { Grid } from '@mui/material'  →  import Grid from '@mui/material/Grid2'
  importStatement: {
    find: /import\s*\{\s*([^}]*\b)Grid(\b[^}]*)\s*\}\s*from\s*['"]@mui\/material['"]/g,
    description: 'Replace with: import Grid from "@mui/material/Grid2"'
  }
};

// ==============================|| SAFE GRID WRAPPER (TEMPORARY) ||============================== //

/**
 * A wrapper component that accepts both v1 and v2 props
 * Use during migration period, then remove when fully migrated
 *
 * @deprecated - Use Grid v2 directly after migration
 */
export const SafeGrid = ({ item, xs, sm, md, lg, xl, size, ...rest }) => {
  // If v2 size prop is provided, use it
  // Otherwise, convert v1 props
  const computedSize = size || convertToV2Size({ xs, sm, md, lg, xl });

  // Import Grid v2 dynamically or use the regular Grid with size prop
  // Note: This is a simplified implementation
  // In production, import Grid from '@mui/material/Grid2'
  return null; // Placeholder - implement when Grid2 is available
};

// ==============================|| EXPORTS ||============================== //

export default {
  suppressMUIDeprecationWarnings,
  convertToV2Size,
  MIGRATION_PATTERNS
};

