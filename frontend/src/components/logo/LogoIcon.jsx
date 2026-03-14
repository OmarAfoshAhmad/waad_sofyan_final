// material-ui
import Box from '@mui/material/Box';
import Avatar from '@mui/material/Avatar';

// Company settings context - SINGLE SOURCE OF TRUTH
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// Fallback static asset
import waadLogoFallback from 'assets/images/waad-logo.png';

// ==============================|| LOGO ICON - COMPANY BRANDING ||============================== //

/**
 * LogoIcon - Compact company logo from centralized settings
 *
 * Uses CompanySettingsContext for dynamic branding.
 * Fallback: Static asset or initials avatar.
 */
export default function LogoIcon() {
  const { getLogoSrc, hasLogo, getInitials, primaryColor, companyName } = useCompanySettings();

  // Determine logo source
  const logoSrc = hasLogo() ? getLogoSrc() : waadLogoFallback;

  if (hasLogo() || waadLogoFallback) {
    return (
      <Box
        component="img"
        src={logoSrc}
        alt={companyName || 'Waad TPA'}
        sx={{
          width: '2.5rem',
          height: '2.5rem',
          objectFit: 'contain'
        }}
      />
    );
  }

  // Fallback: Initials avatar
  return (
    <Avatar
      sx={{
        bgcolor: primaryColor || '#1976d2',
        width: '2.5rem',
        height: '2.5rem',
        fontWeight: 700,
        fontSize: '1.2rem'
      }}
    >
      {getInitials()}
    </Avatar>
  );
}
