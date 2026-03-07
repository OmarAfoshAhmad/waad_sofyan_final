import PropTypes from 'prop-types';
import { useDeferredValue, useMemo } from 'react';

// material-ui
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import NavGroup from './NavGroup';
import menuItem, { filterMenuItemsByRole } from 'menu-items/components';
import useAuth from 'hooks/useAuth';
import useSystemConfig from 'hooks/useSystemConfig';

// ==============================|| DRAWER - NAVIGATION ||============================== //

export default function Navigation({ searchValue }) {
  const deferredSearch = useDeferredValue(searchValue?.trim().toLowerCase() ?? '');
  const { user } = useAuth();
  const role = user?.role || (Array.isArray(user?.roles) && user.roles[0]) || 'DATA_ENTRY';
  const { flags } = useSystemConfig();

  const filteredMenuItems = useMemo(() => {
    let providerScopedMenu = filterMenuItemsByRole(menuItem, role);

    // Hide provider_portal menu group when PROVIDER_PORTAL_ENABLED flag is off
    // SUPER_ADMIN always sees everything (they can still access the API)
    if (!flags.PROVIDER_PORTAL_ENABLED && role !== 'SUPER_ADMIN') {
      providerScopedMenu = providerScopedMenu
        .map((group) => ({
          ...group,
          children: group.children?.filter((child) => child.resource !== 'provider_portal')
        }))
        .filter((group) => !group.children || group.children.length > 0);
    }

    // Then, filter by search value
    if (!deferredSearch) return providerScopedMenu;

    const result = [];
    providerScopedMenu.forEach((parentMenu) => {
      const matchedChildren = parentMenu.children?.filter((child) => child.search?.toLowerCase().includes(deferredSearch));

      if (matchedChildren && matchedChildren.length > 0) {
        result.push({ ...parentMenu, children: matchedChildren });
      }
    });

    return result;
  }, [deferredSearch, role, flags.PROVIDER_PORTAL_ENABLED]);

  const navGroups = filteredMenuItems.map((item) => {
    switch (item.type) {
      case 'group':
        return <NavGroup key={item.id} item={item} />;
      default:
        return (
          <Typography key={item.id} variant="h6" color="error" align="center">
            Fix - Navigation Group
          </Typography>
        );
    }
  });

  return <Box sx={{ pt: 1 }}>{navGroups}</Box>;
}

Navigation.propTypes = { searchValue: PropTypes.string };
