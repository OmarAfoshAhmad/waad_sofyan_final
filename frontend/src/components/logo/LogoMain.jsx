import PropTypes from 'prop-types';
// material-ui
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Avatar from '@mui/material/Avatar';

// Company settings context - SINGLE SOURCE OF TRUTH
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// Fallback static asset (only used if context not available)
import waadLogoFallback from 'assets/images/waad-logo.png';

// ==============================|| LOGO - COMPANY BRANDING ||============================== //

/**
 * LogoMain - Company logo from centralized settings
 *
 * Uses CompanySettingsContext for dynamic branding.
 * Fallback: Static asset or initials avatar.
 */
export default function LogoMain({ reverse }) {
  const { getLogoSrc, hasLogo, getInitials, primaryColor, companyName } = useCompanySettings();

  // Determine logo source
  const logoSrc = hasLogo() ? getLogoSrc() : waadLogoFallback;

  return (
    <Stack direction="row" alignItems="center" spacing={1}>
      {hasLogo() || waadLogoFallback ? (
        <Box
          component="img"
          src={logoSrc}
          alt={companyName || 'Waad TPA'}
          sx={{
            height: '2.5rem',
            width: 'auto',
            objectFit: 'contain'
          }}
        />
      ) : (
        // Fallback: Initials avatar
        <Avatar
          sx={{
            bgcolor: primaryColor || '#1976d2',
            width: '2.5rem',
            height: '2.5rem',
            fontWeight: 700
          }}
        >
          {getInitials()}
        </Avatar>
      )}
    </Stack>
  );
}

LogoMain.propTypes = { reverse: PropTypes.bool };
