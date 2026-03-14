import PropTypes from 'prop-types';
import { isValidElement } from 'react';
import { Box, Typography, Breadcrumbs, Link, Stack, Chip } from '@mui/material';
import { NavigateNext } from '@mui/icons-material';
import { Link as RouterLink } from 'react-router-dom';

/**
 * Modern Clean Page Header Component
 * Displays page title, breadcrumbs, and action buttons
 *
 * Icon prop can be either:
 * - A React component (e.g., PeopleAltIcon) - will be rendered as <Icon sx={...} />
 * - A JSX element (e.g., <LocalHospitalIcon />) - will be cloned with sx props
 */
const ModernPageHeader = ({ title, subtitle, breadcrumbs = [], actions, statusChip, icon }) => {
  // Render icon - handles both ComponentType and JSX Element
  const renderIcon = () => {
    if (!icon) return null;

    const iconSx = { fontSize: '1.5rem' };

    // If icon is a JSX element (e.g., <LocalHospitalIcon />), clone it with sx
    if (isValidElement(icon)) {
      // Clone the element and merge sx props
      const existingSx = icon.props?.sx || {};
      return (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '2.5rem',
            height: '2.5rem',
            borderRadius: 1,
            bgcolor: 'primary.lighter',
            color: 'primary.main'
          }}
        >
          {/* Clone element with merged sx */}
          {isValidElement(icon) && typeof icon.type !== 'string'
            ? { ...icon, props: { ...icon.props, sx: { ...iconSx, ...existingSx } } }
            : icon}
        </Box>
      );
    }

    // If icon is a component type (e.g., PeopleAltIcon), render it
    const Icon = icon;
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '2.5rem',
          height: '2.5rem',
          borderRadius: 1,
          bgcolor: 'primary.lighter',
          color: 'primary.main'
        }}
      >
        <Icon sx={iconSx} />
      </Box>
    );
  };

  return (
    <Box sx={{ mb: '1.5rem' }}>
      {/* Breadcrumbs - HIDDEN per user request */}
      {/* {breadcrumbs.length > 0 && (
        <Breadcrumbs separator={<NavigateNext fontSize="small" />} sx={{ mb: '0.75rem' }}>
          {breadcrumbs[0]?.label !== 'الرئيسية' && (
            <Link component={RouterLink} to="/" underline="hover" color="inherit" sx={{ display: 'flex', alignItems: 'center' }}>
              الرئيسية
            </Link>
          )}
          {breadcrumbs.map((crumb, index) => {
            const isLast = index === breadcrumbs.length - 1;
            return isLast ? (
              <Typography key={index} color="text.primary">
                {crumb.label}
              </Typography>
            ) : (
              <Link key={index} component={RouterLink} to={crumb.path || crumb.href || '/'} underline="hover" color="inherit">
                {crumb.label}
              </Link>
            );
          })}
        </Breadcrumbs>
      )} */}

      {/* Title and Actions Row */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1, minHeight: '2.5rem' }}>
        {/* Title Section */}
        <Stack direction="row" alignItems="center" spacing={1.5}>
          {renderIcon()}
          <Box>
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <Typography variant="h4" component="h1" sx={{ fontWeight: 600, fontSize: { xs: '1.25rem', md: '1.5rem' } }}>
                {title}
              </Typography>
              {statusChip && <Chip label={statusChip.label} color={statusChip.color || 'primary'} size="small" sx={{ height: '1.5rem' }} />}
            </Stack>
            {subtitle && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Stack>

        {/* Actions Section */}
        {actions && (
          <Stack direction="row" spacing={1.5} alignItems="center">
            {actions}
          </Stack>
        )}
      </Stack>
    </Box>
  );
};

ModernPageHeader.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  breadcrumbs: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.string.isRequired,
      path: PropTypes.string
    })
  ),
  actions: PropTypes.node,
  statusChip: PropTypes.shape({
    label: PropTypes.string.isRequired,
    color: PropTypes.oneOf(['default', 'primary', 'secondary', 'error', 'warning', 'info', 'success'])
  }),
  // Icon can be either a component type OR a JSX element
  icon: PropTypes.oneOfType([PropTypes.elementType, PropTypes.element])
};

export default ModernPageHeader;




