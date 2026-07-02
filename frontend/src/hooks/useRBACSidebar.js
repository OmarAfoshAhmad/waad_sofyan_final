import { useMemo } from 'react';
import menuItem, { filterMenuItemsByRole } from 'menu-items/components';
import useAuth from 'hooks/useAuth';
import useSystemConfig from 'hooks/useSystemConfig';

/**
 * useRBACSidebar Hook
 * Filters menu items using static ROLE_RESOURCE_ACCESS map.
 * No can(), no useResourcePermission — pure Role → Resource visibility.
 */
const useRBACSidebar = () => {
  const { user } = useAuth();

  const role = user?.role || (Array.isArray(user?.roles) && user.roles[0]) || 'DATA_ENTRY';

  const { flags } = useSystemConfig();

  const sidebarGroups = useMemo(() => {
    if (!user) return [];
    return filterMenuItemsByRole(menuItem, role, flags.PROVIDER_PORTAL_ENABLED);
  }, [user, role, flags.PROVIDER_PORTAL_ENABLED]);

  const sidebarItems = useMemo(() => {
    const items = [];

    const flatten = (nodes) => {
      nodes.forEach((node) => {
        if (node.type === 'item') {
          items.push({
            id: node.id,
            title: node.title,
            url: node.url,
            icon: node.icon,
            breadcrumbs: node.breadcrumbs
          });
        }
        if (node.children) {
          flatten(node.children);
        }
      });
    };

    if (sidebarGroups) {
      flatten(sidebarGroups);
    }

    return items;
  }, [sidebarGroups]);

  return {
    sidebarGroups,
    sidebarItems,
    loading: false
  };
};

export default useRBACSidebar;
