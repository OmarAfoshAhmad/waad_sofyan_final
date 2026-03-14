/**
 * TBA WAAD SYSTEM - NAVIGATION CONFIGURATION
 *
 * Enterprise Navigation System Configuration
 *
 * ARCHITECTURE:
 * - Static ROLE_RESOURCE_ACCESS map drives menu visibility (see config/roleAccessMap.js)
 * - Click-only navigation (NO hover menus)
 * - Desktop-first with responsive collapse
 * - Keyboard navigation support
 *
 * DESIGN PRINCIPLES:
 * - Zero hover navigation
 * - One-click access
 * - Clear visual hierarchy
 * - No disabled/placeholder items
 */

// Sidebar configuration constants
export const SIDEBAR_CONFIG = {
  // Width settings
  width: {
    expanded: 280,
    collapsed: 72,
    mobile: 280
  },

  // Animation settings
  animation: {
    duration: 225,
    easing: 'ease-in-out'
  },

  // Responsive breakpoints
  breakpoints: {
    mobile: 768,
    tablet: 1024,
    desktop: '600.0rem'
  },

  // Default state
  defaultExpanded: true,

  // Auto-collapse sidebar after navigation
  autoCollapse: true,

  // Persist state in localStorage
  persistState: true,
  storageKey: 'tba_sidebar_state'
};

// Navigation behavior settings
export const NAV_BEHAVIOR = {
  // Menu expansion
  expandOnHover: false,
  autoCollapse: true,

  // Active state
  highlightActive: true,
  highlightParent: true,

  // Scrolling
  scrollToActive: true,

  // Keyboard
  keyboardNavigation: true
};

// Get active item from path
export const getActiveItem = (items, pathname) => {
  for (const item of items) {
    if (item.url === pathname) return item;
    if (item.url && pathname.startsWith(item.url + '/')) return item;
    if (item.children) {
      const found = getActiveItem(item.children, pathname);
      if (found) return found;
    }
  }
  return null;
};

// Get parent group of active item
export const getActiveParent = (items, pathname) => {
  for (const item of items) {
    if (item.children) {
      const hasActiveChild = item.children.some((child) => {
        if (child.url === pathname) return true;
        if (child.url && pathname.startsWith(child.url + '/')) return true;
        if (child.children) {
          return child.children.some((subChild) => subChild.url === pathname || (subChild.url && pathname.startsWith(subChild.url + '/')));
        }
        return false;
      });
      if (hasActiveChild) return item;
    }
  }
  return null;
};

export default {
  SIDEBAR_CONFIG,
  NAV_BEHAVIOR,
  getActiveItem,
  getActiveParent
};
