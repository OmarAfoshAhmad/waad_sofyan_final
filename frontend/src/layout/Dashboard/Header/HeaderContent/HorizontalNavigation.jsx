import { useState, useRef, useCallback, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

// Material-UI
import {
  Box,
  Button,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Divider,
  Typography,
  Paper,
  Stack,
  alpha,
  useMediaQuery,
  useTheme
} from '@mui/material';
import { KeyboardArrowDown } from '@mui/icons-material';

// Project imports
import useRBACSidebar from 'hooks/useRBACSidebar';

/**
 * HorizontalNavigation - شريط تنقل أفقي احترافي
 *
 * ✅ Professional Hover Menu Behavior (FIXED):
 * - Desktop: Hover to open, auto-close on mouse leave
 * - Mobile/Tablet: Click to open (fallback)
 * - No flickering during transition
 * - Proper timeout cleanup to prevent stuck states
 * - Single menu open at a time (auto-close others)
 * - RBAC compatible
 */
export default function HorizontalNavigation() {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { sidebarGroups } = useRBACSidebar();
  const [anchorEls, setAnchorEls] = useState({});
  const timeoutRefs = useRef({});

  // Cleanup all timeouts on unmount
  useEffect(() => {
    return () => {
      Object.values(timeoutRefs.current).forEach((timeout) => {
        if (timeout) clearTimeout(timeout);
      });
    };
  }, []);

  // Clear all timeouts for a specific group
  const clearGroupTimeouts = useCallback((groupId) => {
    if (timeoutRefs.current[groupId]) {
      clearTimeout(timeoutRefs.current[groupId]);
      delete timeoutRefs.current[groupId];
    }
  }, []);

  // فتح dropdown menu
  const handleMenuOpen = useCallback(
    (groupId, event) => {
      // Clear any pending timeout for this group
      clearGroupTimeouts(groupId);

      // Close other open menus for cleaner UX (single menu at a time)
      setAnchorEls((prev) => {
        const newAnchors = {};
        // Close all other menus
        Object.keys(prev).forEach((key) => {
          if (key !== groupId) {
            clearGroupTimeouts(key);
          }
        });
        // Open this menu
        newAnchors[groupId] = event.currentTarget;
        return newAnchors;
      });
    },
    [clearGroupTimeouts]
  );

  // إغلاق dropdown menu مع تأخير بسيط لمنع الإغلاق أثناء الانتقال
  const handleMenuClose = useCallback(
    (groupId, immediate = false) => {
      // Clear existing timeout first to prevent race conditions
      clearGroupTimeouts(groupId);

      if (immediate) {
        setAnchorEls((prev) => {
          const newAnchors = { ...prev };
          delete newAnchors[groupId];
          return newAnchors;
        });
        return;
      }

      // Delay to allow smooth transition to menu (150ms for better UX)
      timeoutRefs.current[groupId] = setTimeout(() => {
        setAnchorEls((prev) => {
          const newAnchors = { ...prev };
          delete newAnchors[groupId];
          return newAnchors;
        });
        delete timeoutRefs.current[groupId]; // Clean up after execution
      }, 150);
    },
    [clearGroupTimeouts]
  );

  // إلغاء إغلاق القائمة (عند دخول الماوس إلى القائمة)
  const handleCancelClose = useCallback(
    (groupId) => {
      clearGroupTimeouts(groupId);
    },
    [clearGroupTimeouts]
  );

  // التنقل إلى صفحة
  const handleNavigate = (url, groupId) => {
    navigate(url);
    handleMenuClose(groupId);
  };

  // التحقق إذا كانت الصفحة نشطة
  const isActive = (url) => {
    if (!url) return false;
    return location.pathname === url || location.pathname.startsWith(url + '/');
  };

  // التحقق إذا كانت المجموعة نشطة
  const isGroupActive = (group) => {
    if (!group.children) return false;
    return group.children.some((child) => {
      if (child.type === 'item') {
        return isActive(child.url);
      }
      if (child.type === 'collapse' && child.children) {
        return child.children.some((subChild) => isActive(subChild.url));
      }
      return false;
    });
  };

  // عرض عنصر قائمة فرعية
  const renderMenuItem = (item, groupId) => {
    if (!item || !item.url) return null;

    const active = isActive(item.url);
    const Icon = item.icon;

    return (
      <MenuItem
        key={item.id}
        onClick={() => handleNavigate(item.url, groupId)}
        sx={{
          py: 1,
          px: '1.0rem',
          minWidth: '13.75rem',
          borderRadius: 1,
          mx: 0.5,
          my: 0.25,
          backgroundColor: active ? 'primary.lighter' : 'transparent',
          color: active ? 'primary.main' : 'text.primary',
          '&:hover': {
            backgroundColor: active ? 'primary.lighter' : 'action.hover'
          }
        }}
      >
        {Icon && (
          <ListItemIcon sx={{ minWidth: '2.25rem', color: active ? 'primary.main' : 'text.secondary' }}>
            <Icon sx={{ fontSize: '1.25rem' }} />
          </ListItemIcon>
        )}
        <ListItemText
          primary={item.title}
          primaryTypographyProps={{
            variant: 'body2',
            fontWeight: active ? 600 : 400
          }}
        />
      </MenuItem>
    );
  };

  // عرض مجموعة منطوية (collapse)
  const renderCollapseGroup = (collapse, groupId) => {
    if (!collapse.children || collapse.children.length === 0) return null;

    const Icon = collapse.icon;

    return (
      <Box key={collapse.id} sx={{ mb: 0.5 }}>
        {/* عنوان المجموعة الفرعية */}
        <Box sx={{ px: '1.0rem', py: 1 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            {Icon && <Icon sx={{ fontSize: '1rem', color: 'text.secondary' }} />}
            <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ textTransform: 'uppercase' }}>
              {collapse.title}
            </Typography>
          </Stack>
        </Box>

        {/* عناصر المجموعة */}
        {collapse.children.map((child) => {
          if (child.type === 'item') {
            return renderMenuItem(child, groupId);
          }
          return null;
        })}
      </Box>
    );
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      {sidebarGroups?.map((group) => {
        if (!group.children || group.children.length === 0) return null;

        const groupActive = isGroupActive(group);
        const anchorEl = anchorEls[group.id];
        const open = Boolean(anchorEl);

        // إذا كانت المجموعة تحتوي على عنصر واحد مباشر
        const hasDirectUrl = group.children.length === 1 && group.children[0].type === 'item';
        const directItem = hasDirectUrl ? group.children[0] : null;

        return (
          <Box
            key={group.id}
            onMouseEnter={(e) => {
              // Desktop: Hover to open
              if (!isMobile && !hasDirectUrl) {
                handleMenuOpen(group.id, e);
              }
            }}
            onMouseLeave={() => {
              // Desktop: Auto-close on mouse leave
              if (!isMobile && !hasDirectUrl) {
                handleMenuClose(group.id);
              }
            }}
          >
            <Button
              onClick={(e) => {
                if (directItem?.url) {
                  navigate(directItem.url);
                } else if (isMobile) {
                  // Mobile: Click to open
                  handleMenuOpen(group.id, e);
                }
              }}
              endIcon={
                !hasDirectUrl ? (
                  <KeyboardArrowDown
                    sx={{ fontSize: '1rem', transition: 'transform 0.2s', transform: open ? 'rotate(180deg)' : 'rotate(0deg)' }}
                  />
                ) : null
              }
              sx={{
                color: groupActive ? 'primary.main' : 'text.primary',
                backgroundColor: open ? alpha(theme.palette.primary.main, 0.12) : groupActive ? alpha(theme.palette.primary.main, 0.08) : 'transparent',
                px: '0.75rem',
                py: 0.5,
                fontWeight: groupActive ? 600 : 500,
                fontSize: '0.75rem',
                textTransform: 'none',
                minWidth: 'auto',
                transition: 'all 0.2s ease',
                '&:hover': {
                  backgroundColor: groupActive ? alpha(theme.palette.primary.main, 0.12) : 'action.hover'
                },
                borderBottom: groupActive ? '2px solid' : 'none',
                borderColor: 'primary.main',
                borderRadius: groupActive ? '4px 4px 0 0' : 1
              }}
            >
              {group.title}
            </Button>

            {/* Dropdown Menu */}
            {!hasDirectUrl && (
              <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={() => handleMenuClose(group.id, true)}
                anchorOrigin={{
                  vertical: 'bottom',
                  horizontal: 'left'
                }}
                transformOrigin={{
                  vertical: 'top',
                  horizontal: 'left'
                }}
                // Prevent closing when mouse enters menu area
                MenuListProps={{
                  onMouseEnter: () => handleCancelClose(group.id),
                  onMouseLeave: () => {
                    if (!isMobile) {
                      handleMenuClose(group.id);
                    }
                  }
                }}
                // Proper backdrop handling for hover UX
                disableAutoFocus
                disableEnforceFocus
                BackdropProps={{
                  invisible: true, // Invisible but still functional
                  onClick: (e) => {
                    e.stopPropagation();
                    handleMenuClose(group.id, true);
                  }
                }}
                sx={{
                  '& .MuiPaper-root': {
                    mt: 0.5,
                    minWidth: '15.0rem',
                    maxWidth: '22.5rem',
                    boxShadow: (theme) => theme.shadows[8],
                    borderRadius: '0.25rem',
                    border: '1px solid',
                    borderColor: 'divider'
                  }
                }}
              >
                <Box sx={{ py: 0.5 }}>
                  {group.children.map((child) => {
                    if (child.type === 'item') {
                      return renderMenuItem(child, group.id);
                    }
                    if (child.type === 'collapse') {
                      return renderCollapseGroup(child, group.id);
                    }
                    return null;
                  })}
                </Box>
              </Menu>
            )}
          </Box>
        );
      })}
    </Box>
  );
}


