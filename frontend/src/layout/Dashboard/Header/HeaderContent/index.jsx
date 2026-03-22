import { useMemo } from 'react';

import useMediaQuery from '@mui/material/useMediaQuery';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Avatar from '@mui/material/Avatar';
import PersonIcon from '@mui/icons-material/Person';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

// project imports
import Profile from './Profile';
import ProviderThemeToggle from 'components/provider/ProviderThemeToggle';
// Arabic-only system – i18n disabled by design (Localization component removed)
// import Localization from './Localization';
import FullScreen from './FullScreen';
// REMOVED: Theme customization disabled - Fixed professional UI/UX
// import Customization from './Customization';
import MobileSection from './MobileSection';
import HorizontalNavigation from './HorizontalNavigation';

import useConfig from 'hooks/useConfig';
import useAuth from 'hooks/useAuth';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { MenuOrientation } from 'config';
import DrawerHeader from 'layout/Dashboard/Drawer/DrawerHeader';

// ==============================|| HEADER - CONTENT ||============================== //

export default function HeaderContent() {
  const { state } = useConfig();
  const { user } = useAuth();
  const { companyName, companyNameEn, primaryColor, getLogoSrc, hasLogo, getInitials, settings } = useCompanySettings();

  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));

  // Check if user is a Provider
  const isProvider = user?.roles?.includes('PROVIDER');
  const providerName = user?.providerName || null;

  // Arabic-only system – i18n disabled by design
  // const localization = useMemo(() => <Localization />, []);

  // Display name: Arabic for RTL, English for LTR
  const displayName = companyName || companyNameEn || 'TBA';

  return (
    <>
      {state.menuOrientation === MenuOrientation.HORIZONTAL && !downLG && <DrawerHeader open={true} />}

      {/* ✅ System Logo/Title - Different for Provider */}
      {!downLG && (
        <Box sx={{ display: 'flex', alignItems: 'center', mr: '1.0rem' }}>
          {isProvider ? (
            // Provider Portal branding
            <Stack direction="row" spacing={1} alignItems="center">
              <Avatar sx={{ bgcolor: 'primary.main', width: '2.0rem', height: '2.0rem' }}>
                <LocalHospitalIcon sx={{ fontSize: '1.125rem' }} />
              </Avatar>
              <Box>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 700,
                    color: 'primary.main',
                    lineHeight: 1.2
                  }}
                >
                  بوابة مقدم الخدمة
                </Typography>
                {providerName && (
                  <Typography
                    variant="caption"
                    sx={{
                      color: 'text.secondary',
                      fontSize: '0.7rem'
                    }}
                  >
                    {providerName}
                  </Typography>
                )}
              </Box>
            </Stack>
          ) : (
            // Company branding from settings (SINGLE SOURCE OF TRUTH)
            <Stack direction="row" spacing={1} alignItems="center">
              {/* Always show logo - uses fallback if no custom logo */}
              <Box
                component="img"
                src={getLogoSrc()}
                alt={displayName}
                sx={{
                  height: '2.0rem',
                  width: 'auto',
                  maxWidth: '6.25rem',
                  objectFit: 'contain'
                }}
                onError={(e) => {
                  // If image fails to load, hide it
                  e.target.style.display = 'none';
                }}
              />
              <Box sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 700,
                    lineHeight: 1.1,
                    color: 'primary.main',
                    fontSize: '0.85rem',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {displayName}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: 'text.secondary',
                    fontSize: '0.75rem',
                    lineHeight: 1,
                    whiteSpace: 'nowrap'
                  }}
                >
                  {settings?.businessType || 'إدارة التأمين الصحي'}
                </Typography>
              </Box>
            </Stack>
          )}
        </Box>
      )}

      {/* ✅ Navigation Horizontal - القائمة الأفقية */}
      {!downLG && <HorizontalNavigation />}

      <Box sx={{ flexGrow: 1 }} />

      <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
        {/* ✅ Welcome Message with Username */}
        {user && (
          <Chip
            icon={isProvider ? <LocalHospitalIcon /> : <PersonIcon />}
            label={`مرحباً، ${user.fullName || user.username}`}
            variant="outlined"
            color={isProvider ? 'success' : 'primary'}
            sx={{
              borderRadius: '0.25rem',
              fontWeight: 500,
              '& .MuiChip-icon': { color: isProvider ? 'success.main' : 'primary.main' }
            }}
          />
        )}
        {/* ✅ Theme Toggle (User Preference) - Available for ALL Users */}
        <ProviderThemeToggle />
        {/* Arabic-only system – i18n disabled by design */}
        {/* {localization} */}
        {!downLG && <FullScreen />}
        {/* REMOVED: Theme customization disabled - Fixed professional UI/UX */}
        {/* {!downLG && <Customization />} */}
        {!downLG && <Profile />}
        {downLG && <MobileSection />}
      </Stack>
    </>
  );
}

