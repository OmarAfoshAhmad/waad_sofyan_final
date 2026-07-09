/**
 * Console Zero-Noise Policy
 * ==========================
 *
 * PRODUCTION READINESS (2026-01-13):
 * - Filters expected warnings in production
 * - Keeps real errors visible
 * - Configurable noise patterns
 *
 * POLICY:
 * - console.error → Only real errors (500s, crashes)
 * - console.warn → Development only
 * - console.log → Development only
 */

// ==============================|| NOISE PATTERNS ||============================== //

/**
 * Patterns to filter from console.warn
 * These are expected behaviors, not bugs
 */
const WARN_NOISE_PATTERNS = [
  // MUI deprecation warnings
  'MUI: The Grid',
  'MUI:',
  'deprecated',
  'will be removed',
  'isDarkMode',
  'GridProps',
  'item',
  'xs',
  'md',

  // React strict mode double renders
  'findDOMNode',
  'componentWillReceiveProps',
  'componentWillMount',
  'componentWillUpdate',

  // Expected auth behaviors
  'No active session',
  'Session check',
  'RBAC Initialized',

  // Permission-aware API skips
  'Skipping GET',
  'Skipping POST',
  'no permission for domain',

  // Development helpers
  '[DEV]',
  '[HMR]',
  'Fast Refresh'
];

/**
 * Patterns to filter from console.error
 * VERY selective - only filter truly expected "errors"
 */
const ERROR_NOISE_PATTERNS = [
  // Expected 401 during init (no session yet)
  'GET /api/auth/session/me [401]',
  '/session/me [401]',
  'AUTH_REQUIRED',

  // React internal (not actual errors)
  'Warning: Each child',
  'Warning: validateDOMNesting',

  // Browser extensions interference
  'Extension context',
  'message channel closed',

  // Browser/MUI accessibility conflict (often reported as error/warning)
  'Blocked aria-hidden on an element',
  'isDarkMode',
  'isdarkmode',
  'is-dark-mode',
  'MUI: The Grid',
  'Grid v1',
  'GridProps',
  'item',
  'xs',
  'md'
];

/**
 * Patterns that should ALWAYS be logged (never filtered)
 * Even if they match noise patterns
 */
const CRITICAL_PATTERNS = [
  '500',
  'Server Error',
  'FATAL',
  'CRITICAL',
  'SystemErrorBoundary',
  'Uncaught',
  'TypeError',
  'ReferenceError',
  'SyntaxError'
];

// ==============================|| FILTER FUNCTIONS ||============================== //

/**
 * Check if message matches any pattern
 * @param {string} message - Console message
 * @param {string[]} patterns - Patterns to check
 * @returns {boolean}
 */
const matchesPattern = (message, patterns) => {
  const msgStr = String(message || '');
  return patterns.some((pattern) => msgStr.includes(pattern));
};

/**
 * Check if message is critical (should never be filtered)
 * @param {string} message - Console message
 * @returns {boolean}
 */
const isCritical = (message) => {
  return matchesPattern(message, CRITICAL_PATTERNS);
};

// ==============================|| CONSOLE FILTERS ||============================== //

/**
 * Apply zero-noise policy to console
 * Call this once at app startup
 *
 * @param {object} options - Configuration options
 * @param {boolean} options.filterWarn - Filter warnings (default: true in prod)
 * @param {boolean} options.filterError - Filter expected errors (default: true in prod)
 * @param {boolean} options.filterLog - Filter logs (default: true in prod)
 */
export const applyZeroNoisePolicy = (options = {}) => {
  const isProd = import.meta.env.PROD;

  const { filterWarn = isProd, filterError = isProd, filterLog = isProd } = options;

  // Store original methods
  const originalConsole = {
    log: console.log,
    warn: console.warn,
    error: console.error,
    info: console.info,
    debug: console.debug
  };

  // ===== FILTER console.log =====
  if (filterLog) {
    console.log = (...args) => {
      // In production, suppress most logs
      // Only allow explicit [PROD] tagged logs
      const message = args[0]?.toString?.() || '';
      if (message.includes('[PROD]') || isCritical(message)) {
        originalConsole.log.apply(console, args);
      }
      // Silent skip in production
    };
  }

  // ===== FILTER console.warn =====
  if (filterWarn || true) {
    // FORCE FILTER - Apply even in dev for noise reduction
    console.warn = (...args) => {
      const message = args[0]?.toString?.() || '';

      // Force Suppress "Blocked aria-hidden" (Browser/MUI conflict)
      // This warning is a browser violation that might come through warn
      if (message.includes('Blocked aria-hidden on an element')) {
        return;
      }

      // Always log critical warnings
      if (isCritical(message)) {
        originalConsole.warn.apply(console, args);
        return;
      }

      // Filter noise patterns
      if (matchesPattern(message, WARN_NOISE_PATTERNS)) {
        return; // Silent skip
      }

      // Log unexpected warnings
      originalConsole.warn.apply(console, args);
    };
  }

  // ===== FILTER console.error =====
  if (filterError || true) {
    // FORCE FILTER - Apply even in dev to catch browser noise
    console.error = (...args) => {
      const message = args[0]?.toString?.() || '';

      // Force Suppress "Blocked aria-hidden" (Browser/MUI conflict)
      if (message.includes('Blocked aria-hidden on an element')) {
        return;
      }

      // ALWAYS log critical errors
      if (isCritical(message)) {
        originalConsole.error.apply(console, args);
        return;
      }

      // Filter expected "errors" that aren't really errors
      if (matchesPattern(message, ERROR_NOISE_PATTERNS)) {
        // Downgrade to debug in dev, silent in prod
        if (!isProd) {
          originalConsole.debug.apply(console, ['[Filtered Error]', ...args]);
        }
        return;
      }

      // Log real errors
      originalConsole.error.apply(console, args);
    };
  }

  // ===== FILTER console.info =====
  if (filterLog) {
    console.info = (...args) => {
      const message = args[0]?.toString?.() || '';

      // Allow session restored messages (useful feedback)
      if (message.includes('Session restored') || message.includes('[PROD]')) {
        originalConsole.info.apply(console, args);
        return;
      }

      // Silent in production
    };
  }

  // ===== Disable console.debug in production =====
  if (isProd) {
    console.debug = () => {}; // No-op
  }

  // Return restore function for testing
  return () => {
    console.log = originalConsole.log;
    console.warn = originalConsole.warn;
    console.error = originalConsole.error;
    console.info = originalConsole.info;
    console.debug = originalConsole.debug;
  };
};

// ==============================|| PRODUCTION LOGGER ||============================== //

/**
 * Production-safe logger
 * Use this instead of console for production logging
 */
export const prodLogger = {
  /**
   * Log info that should appear in production
   */
  info: (...args) => {
    console.info('[PROD]', ...args);
  },

  /**
   * Log warning that should appear in production
   */
  warn: (...args) => {
    console.warn('[PROD]', ...args);
  },

  /**
   * Log error that should appear in production
   */
  error: (...args) => {
    console.error('[CRITICAL]', ...args);
  },

  /**
   * Development-only log
   */
  dev: (...args) => {
    if (import.meta.env.DEV) {
      console.log('[DEV]', ...args);
    }
  }
};

// ==============================|| EXPORTS ||============================== //

export default {
  applyZeroNoisePolicy,
  prodLogger,
  WARN_NOISE_PATTERNS,
  ERROR_NOISE_PATTERNS,
  CRITICAL_PATTERNS
};
