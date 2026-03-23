/**
 * TableRefreshContext - Global Table Refresh Mechanism
 * Phase D2.3 - Post-Create/Edit Refresh Contract
 *
 * ⚠️ CONTRACT:
 * - refreshKey is a number that increments on each triggerRefresh()
 * - TbaDataTable listens to refreshKey and fetches exactly ONCE when it changes
 * - Use triggerRefresh() after successful Create/Edit/Delete operations
 *
 * Usage:
 * 1. Wrap your list page with <TableRefreshProvider>
 * 2. Pass refreshKey to <TbaDataTable refreshKey={refreshKey} />
 * 3. Call triggerRefresh() from Create/Edit pages on success
 *
 * Example:
 * ```jsx
 * // In List page:
 * const { refreshKey } = useTableRefresh();
 * <TbaDataTable refreshKey={refreshKey} ... />
 *
 * // In Create page:
 * const { triggerRefresh } = useTableRefresh();
 * await createItem(data);
 * triggerRefresh();
 * navigate('/items');
 * ```
 */

import { createContext, useContext, useState, useMemo, useCallback } from 'react';
import { Outlet } from 'react-router-dom';
import PropTypes from 'prop-types';

// ============================================================================
// CONTEXT
// ============================================================================

const TableRefreshContext = createContext(null);

// ============================================================================
// PROVIDER (for wrapping components manually)
// ============================================================================

/**
 * TableRefreshProvider - Wrap around routes that need table refresh coordination
 */
export const TableRefreshProvider = ({ children }) => {
  const [refreshKey, setRefreshKey] = useState(0);

  /**
   * Trigger a table refresh by incrementing the key
   * This will cause TbaDataTable to re-fetch exactly once
   */
  const triggerRefresh = useCallback(() => {
    setRefreshKey((prev) => prev + 1);
  }, []);

  // Memoize context value to prevent unnecessary re-renders
  const value = useMemo(
    () => ({
      refreshKey,
      triggerRefresh
    }),
    [refreshKey, triggerRefresh]
  );

  return <TableRefreshContext.Provider value={value}>{children}</TableRefreshContext.Provider>;
};

TableRefreshProvider.propTypes = {
  children: PropTypes.node.isRequired
};

// ============================================================================
// ROUTE WRAPPER (for use in react-router element prop)
// ============================================================================

/**
 * TableRefreshLayout - Route layout that provides refresh context with Outlet
 * Use this as element in react-router routes to wrap child routes
 */
export const TableRefreshLayout = () => {
  const [refreshKey, setRefreshKey] = useState(0);

  const triggerRefresh = useCallback(() => {
    setRefreshKey((prev) => prev + 1);
  }, []);

  const value = useMemo(
    () => ({
      refreshKey,
      triggerRefresh
    }),
    [refreshKey, triggerRefresh]
  );

  return (
    <TableRefreshContext.Provider value={value}>
      <Outlet />
    </TableRefreshContext.Provider>
  );
};

// ============================================================================
// HOOK
// ============================================================================

/**
 * useTableRefresh - Access the table refresh context
 * @returns {{ refreshKey: number, triggerRefresh: () => void }}
 */
export const useTableRefresh = () => {
  const context = useContext(TableRefreshContext);

  // Return default values if used outside provider (graceful degradation)
  if (!context) {
    console.warn('[useTableRefresh] Used outside TableRefreshProvider - returning defaults');
    return {
      refreshKey: 0,
      triggerRefresh: () => {
        console.warn('[useTableRefresh] triggerRefresh called outside provider');
      }
    };
  }

  return context;
};

export default TableRefreshContext;
