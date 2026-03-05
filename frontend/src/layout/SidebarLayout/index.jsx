/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TBA WAAD SYSTEM - ENTERPRISE NAVBAR LAYOUT ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Professional enterprise-grade layout using top navigation instead of sidebar.
 *
 * ARCHITECTURE (NON-NEGOTIABLE):
 * - TopBar: 64px height - Logo, Horizontal Navigation Menu, User, Profile
 * - Content: 100% width fill, NO max-width containers
 * - Layout: 100vw × 100vh viewport occupation
 *
 * @author TBA WAAD Development Team
 * @version 5.0.0 - Navbar Architecture
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import { useState, useMemo, useCallback, createContext, useContext } from 'react';
import { Outlet, useLocation, Navigate, useNavigate } from 'react-router-dom';
import {
  Box,
  Drawer,
  IconButton,
  Typography,
  Divider,
  Stack,
  Tooltip,
  useMediaQuery,
  useTheme,
  alpha,
  styled,
  Button,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Avatar,
  List,
  ListItem,
  ListItemButton,
  Collapse,
  Container
} from '@mui/material';
import {
  Menu as MenuIcon,
  ExpandMore as ExpandMoreIcon,
  Logout as LogoutIcon
} from '@mui/icons-material';

// Project imports
import useAuth from 'hooks/useAuth';
import useRBACSidebar from 'hooks/useRBACSidebar';
import Loader from 'components/Loader';
import PageErrorBoundary from 'components/SafeStates/PageErrorBoundary';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import SimpleBar from 'components/third-party/SimpleBar';
import Profile from 'layout/Dashboard/Header/HeaderContent/Profile';

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS & STYLES
// ═══════════════════════════════════════════════════════════════════════════════

const TOPBAR_HEIGHT = 64;

const MainContent = styled(Box)(({ theme }) => ({
  display: 'flex',
  flexDirection: 'column',
  width: '100%',
  minWidth: 0,
  height: '100vh',
  overflow: 'hidden'
}));

// Enterprise TopBar - 64px
const TopBar = styled(Box)(({ theme }) => ({
  height: TOPBAR_HEIGHT,
  minHeight: TOPBAR_HEIGHT,
  maxHeight: TOPBAR_HEIGHT,
  backgroundColor: theme.palette.background.paper,
  borderBottom: `1px solid ${theme.palette.divider}`,
  flexShrink: 0,
  zIndex: theme.zIndex.appBar
}));

// ═══════════════════════════════════════════════════════════════════════════════
// DESKTOP HORIZONTAL NAVIGATION COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

const DesktopNavItem = ({ item, onClick }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const isActive = item.url === location.pathname || (item.url && location.pathname.startsWith(item.url + '/'));
  const Icon = item.icon;

  const handleClick = () => {
    if (item.url) {
      navigate(item.url);
      if (onClick) onClick();
    }
  };

  return (
    <MenuItem onClick={handleClick} selected={isActive} sx={{ borderRadius: 1, mb: 0.5, mx: 1 }}>
      {Icon && (
        <ListItemIcon sx={{ minWidth: 32 }}>
          <Icon fontSize="small" color={isActive ? "primary" : "inherit"} />
        </ListItemIcon>
      )}
      <ListItemText
        primary={item.title}
        primaryTypographyProps={{
          fontWeight: isActive ? 600 : 400,
          color: isActive ? 'primary.main' : 'text.primary',
          fontSize: '0.875rem'
        }}
      />
    </MenuItem>
  );
};

const DesktopNavCollapseItems = ({ collapse, onClick }) => {
  return (
    <Box>
      {collapse.title && (
        <Typography variant="overline" sx={{ px: 2, pt: 1, pb: 0.5, color: 'text.secondary', display: 'block', lineHeight: 1, fontWeight: 700 }}>
          {collapse.title}
        </Typography>
      )}
      {collapse.children?.map(child => (
        <DesktopNavItem key={child.id} item={child} onClick={onClick} />
      ))}
    </Box>
  );
};

const DesktopNavGroupButton = ({ group }) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();

  // If group has only 1 child and it's an item, it can be a direct link
  const isDirectLink = group.children?.length === 1 && group.children[0].type === 'item';
  const directItem = isDirectLink ? group.children[0] : null;

  const isActive = useMemo(() => {
    const checkActive = (nodes) => {
      if (!nodes) return false;
      return nodes.some(n => {
        if (n.url && (location.pathname === n.url || location.pathname.startsWith(n.url + '/'))) return true;
        if (n.children) return checkActive(n.children);
        return false;
      });
    };
    return checkActive(group.children);
  }, [group, location.pathname]);

  const handleClick = (event) => {
    if (isDirectLink) {
      if (directItem.url) navigate(directItem.url);
    } else {
      setAnchorEl(event.currentTarget);
    }
  };

  const handleClose = () => setAnchorEl(null);

  return (
    <>
      <Button
        onClick={handleClick}
        color={isActive ? "primary" : "inherit"}
        endIcon={!isDirectLink ? <ExpandMoreIcon /> : null}
        sx={{
          fontWeight: isActive ? 700 : 500,
          opacity: isActive ? 1 : 0.8,
          '&:hover': { opacity: 1, backgroundColor: 'action.hover' },
          mx: 0.5,
          whiteSpace: 'nowrap',
          fontSize: '0.9rem'
        }}
      >
        {group.title}
      </Button>
      {!isDirectLink && (
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={handleClose}
          PaperProps={{
            elevation: 3,
            sx: { mt: 1.5, minWidth: 220, borderRadius: 2, p: 1 }
          }}
        >
          {group.children?.map(child => {
            if (child.type === 'item') {
              return <DesktopNavItem key={child.id} item={child} onClick={handleClose} />;
            }
            if (child.type === 'collapse') {
              return <Box key={child.id}>
                <DesktopNavCollapseItems collapse={child} onClick={handleClose} />
                <Divider sx={{ my: 1 }} />
              </Box>;
            }
            return null;
          })}
        </Menu>
      )}
    </>
  );
};

// ═══════════════════════════════════════════════════════════════════════════════
// MOBILE NAVIGATION COMPONENTS (DRAWER)
// ═══════════════════════════════════════════════════════════════════════════════

const MobileNavItem = ({ item, level = 0, onClose }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();

  if (!item || item.type === 'divider') {
    return <Divider sx={{ my: 1, mx: 2 }} />;
  }

  const isActive = item.url === location.pathname || (item.url && location.pathname.startsWith(item.url + '/'));
  const Icon = item.icon;
  const paddingLeft = 16 + level * 24;

  const handleClick = () => {
    if (item.url) {
      navigate(item.url);
      if (onClose) onClose();
    }
  };

  return (
    <ListItem disablePadding sx={{ display: 'block' }}>
      <ListItemButton
        onClick={handleClick}
        sx={{
          minHeight: 40,
          px: 1.5,
          pl: `${paddingLeft}px`,
          borderRadius: 1,
          mx: 1,
          my: 0.125,
          backgroundColor: isActive ? alpha(theme.palette.primary.main, 0.12) : 'transparent',
          color: isActive ? 'primary.main' : 'text.primary',
          '&:hover': {
            backgroundColor: isActive ? alpha(theme.palette.primary.main, 0.16) : 'transparent'
          }
        }}
      >
        {Icon && (
          <ListItemIcon sx={{ minWidth: 40, color: isActive ? 'primary.main' : 'text.secondary' }}>
            <Icon sx={{ fontSize: 22 }} />
          </ListItemIcon>
        )}
        <ListItemText
          primary={item.title}
          primaryTypographyProps={{
            fontSize: level === 0 ? '0.875rem' : '0.8125rem',
            fontWeight: isActive ? 600 : 400
          }}
        />
      </ListItemButton>
    </ListItem>
  );
};

const MobileNavCollapse = ({ item, level = 0, onClose }) => {
  const [open, setOpen] = useState(false);
  const theme = useTheme();
  const Icon = item.icon;
  const paddingLeft = 16 + level * 24;

  const handleToggle = () => setOpen(!open);

  return (
    <>
      <ListItem disablePadding sx={{ display: 'block' }}>
        <ListItemButton
          onClick={handleToggle}
          sx={{
            minHeight: 40,
            px: 1.5,
            pl: `${paddingLeft}px`,
            borderRadius: 1,
            mx: 1,
            my: 0.125,
            color: 'text.primary'
          }}
        >
          {Icon && (
            <ListItemIcon sx={{ minWidth: 40, color: 'text.secondary' }}>
              <Icon sx={{ fontSize: 22 }} />
            </ListItemIcon>
          )}
          <ListItemText primary={item.title} primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: 500 }} />
          <ExpandMoreIcon sx={{ fontSize: 18, transform: open ? 'rotate(180deg)' : 'rotate(0deg)', transition: '0.2s' }} />
        </ListItemButton>
      </ListItem>
      <Collapse in={open} timeout="auto" unmountOnExit>
        <List component="div" disablePadding>
          {item.children?.map(child => (
            <MobileNavItemRenderer key={child.id} item={child} level={level + 1} onClose={onClose} />
          ))}
        </List>
      </Collapse>
    </>
  );
};

const MobileNavGroup = ({ item, onClose }) => {
  if (!item.children || item.children.length === 0) return null;

  return (
    <Box component="nav" sx={{ mb: 1 }}>
      {item.title && (
        <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, textTransform: 'uppercase', color: 'text.secondary', px: 2, py: 1, mt: 1 }}>
          {item.title}
        </Typography>
      )}
      <List disablePadding>
        {item.children.map(child => (
          <MobileNavItemRenderer key={child.id} item={child} level={0} onClose={onClose} />
        ))}
      </List>
    </Box>
  );
};

const MobileNavItemRenderer = ({ item, level, onClose }) => {
  if (!item) return null;
  switch (item.type) {
    case 'group': return <MobileNavGroup item={item} onClose={onClose} />;
    case 'collapse': return <MobileNavCollapse item={item} level={level} onClose={onClose} />;
    case 'item': return <MobileNavItem item={item} level={level} onClose={onClose} />;
    case 'divider': return <Divider sx={{ my: 1, mx: 2 }} />;
    default: return null;
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN LAYOUT COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

// Context kept for backwards compatibility if any deeply nested component uses it
const SidebarContext = createContext({
  expanded: true,
  toggleExpanded: () => { },
  setExpanded: () => { },
  openGroups: {},
  toggleGroup: () => { }
});
export const useSidebar = () => useContext(SidebarContext);

export default function SidebarLayout() {
  const theme = useTheme();
  const { user, logout } = useAuth();
  const { companyName, companyNameEn, getLogoSrc, settings } = useCompanySettings();
  const isMobile = useMediaQuery(theme.breakpoints.down('lg'));

  const [mobileOpen, setMobileOpen] = useState(false);
  const toggleMobile = useCallback(() => setMobileOpen(prev => !prev), []);

  const { sidebarGroups, loading } = useRBACSidebar();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const isProvider = user?.roles?.includes('PROVIDER');
  const displayName = companyName || companyNameEn || 'TBA';
  const primaryRole = user.roles?.[0]?.replace('_', ' ') || 'مستخدم';

  return (
    <SidebarContext.Provider value={{ expanded: false, toggleExpanded: () => { }, setExpanded: () => { }, openGroups: {}, toggleGroup: () => { } }}>
      <Box sx={{ display: 'flex', width: '100vw', height: '100vh', overflow: 'hidden' }}>

        {/* Mobile Drawer (Only shown on small screens) */}
        {isMobile && (
          <Drawer
            variant="temporary"
            anchor="right"
            open={mobileOpen}
            onClose={toggleMobile}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': { width: 280, boxSizing: 'border-box' } }}
          >
            <Box sx={{ p: 2, display: 'flex', alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="h6" fontWeight={700} color="primary">القائمة الرئيسية</Typography>
            </Box>
            <SimpleBar style={{ height: 'calc(100vh - 140px)' }}>
              <Box sx={{ py: 1 }}>
                {!loading && sidebarGroups?.map(group => (
                  <MobileNavItemRenderer key={group.id} item={group} level={0} onClose={toggleMobile} />
                ))}
              </Box>
            </SimpleBar>
            <Box sx={{ position: 'absolute', bottom: 0, width: '100%', p: 2, borderTop: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
              <Stack direction="row" alignItems="center" spacing={1.5}>
                <Avatar sx={{ width: 36, height: 36, bgcolor: isProvider ? 'success.main' : 'primary.main' }}>
                  {user.fullName?.[0] || user.username?.[0] || 'U'}
                </Avatar>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="subtitle2" noWrap fontWeight={600}>{user.fullName || user.username}</Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>{primaryRole}</Typography>
                </Box>
                <IconButton size="small" onClick={logout} color="error">
                  <LogoutIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Box>
          </Drawer>
        )}

        {/* Main Content Area */}
        <MainContent>
          <TopBar>
            <Box sx={{ width: '100%', maxWidth: '1600px', mx: 'auto', px: { xs: 2, sm: 3 }, height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              {/* Left Section: Logo & Mobile Menu */}
              <Stack direction="row" alignItems="center" spacing={2} sx={{ minWidth: 200 }}>
                {isMobile && (
                  <IconButton onClick={toggleMobile} edge="start">
                    <MenuIcon />
                  </IconButton>
                )}
                <Box
                  component="img"
                  src={getLogoSrc()}
                  alt={displayName}
                  sx={{ height: 38, width: 'auto', maxWidth: 120, objectFit: 'contain' }}
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
                {!isMobile && (
                  <Box>
                    <Typography variant="subtitle1" fontWeight={700} color="primary.main" lineHeight={1.2}>
                      {displayName}
                    </Typography>
                  </Box>
                )}
              </Stack>

              {/* Center Section: Desktop Navigation */}
              {!isMobile && !loading && (
                <Stack direction="row" alignItems="center" spacing={1} sx={{ flex: 1, justifyContent: 'center', overflowX: 'auto' }}>
                  {sidebarGroups?.map(group => (
                    <DesktopNavGroupButton key={group.id} group={group} />
                  ))}
                </Stack>
              )}

              {/* Right Section: User & Profile */}
              <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 200, justifyContent: 'flex-end' }}>
                {!isMobile && (
                  <Box sx={{ textAlign: 'left', mr: 1, display: { xs: 'none', lg: 'block' } }}>
                    <Typography variant="body2" fontWeight={600} color="text.primary" lineHeight={1.2}>
                      {user.fullName || user.username}
                    </Typography>
                    <Typography variant="caption" color={isProvider ? 'success.main' : 'text.secondary'} fontWeight={500}>
                      {primaryRole}
                    </Typography>
                  </Box>
                )}
                {!isMobile && <Profile />}
              </Stack>
            </Box>
          </TopBar>

          {/* Page Content */}
          <Box
            component="main"
            sx={{
              flex: 1,
              width: '100%',
              minWidth: 0,
              height: `calc(100vh - ${TOPBAR_HEIGHT}px)`,
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: (theme) => (theme.palette.mode === 'dark' ? 'background.default' : alpha(theme.palette.grey[100], 0.5))
            }}
          >
            {/* Scrollable Content Container */}
            <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ width: '100%', maxWidth: '1600px', mx: 'auto', px: { xs: 2, sm: 3 }, py: { xs: 1, sm: 1.5 }, flex: 1, display: 'flex', flexDirection: 'column' }}>
                <PageErrorBoundary pageName="Dashboard Content">
                  <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                    <Outlet />
                  </Box>
                </PageErrorBoundary>
              </Box>
            </Box>

            {/* Uniform System Footer (Always Visible) */}
            <Box
              sx={{
                textAlign: 'center',
                py: 0.5,
                color: 'text.secondary',
                fontSize: '12px',
                flexShrink: 0,
                bgcolor: 'background.paper',
                borderTop: 1,
                borderColor: 'divider',
                width: '100%'
              }}
            >
              Designed & Developed by TBA WAAD Team - 2026
            </Box>
          </Box>
        </MainContent>
      </Box>
    </SidebarContext.Provider>
  );
}
