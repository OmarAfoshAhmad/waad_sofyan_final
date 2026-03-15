/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║           PROVIDER THEME TOGGLE - Light/Dark Mode Button                     ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  PURPOSE: Toggle button for Provider Portal theme                            ║
 * ║  CREATED: 2026-01-29                                                         ║
 * ║  LOCATION: Header of Provider Portal                                         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

import { IconButton, Tooltip, Box } from '@mui/material';
// import { useColorScheme } from '@mui/material/styles'; // Replaced by global user-aware context
import { useThemeMode } from 'contexts/ThemeModeContext';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import { motion, AnimatePresence } from 'framer-motion';

export default function ProviderThemeToggle() {
  const { mode, toggleTheme } = useThemeMode();
  const isDark = mode === 'dark';

  const handleToggle = () => {
    toggleTheme();
  };

  return (
    <Tooltip title={isDark ? 'الوضع الفاتح' : 'الوضع الداكن'}>
      <IconButton
        onClick={handleToggle}
        sx={{
          width: '2.5rem',
          height: '2.5rem',
          borderRadius: '0.25rem',
          bgcolor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.04)',
          border: '1px solid',
          borderColor: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)',
          transition: 'all 0.3s ease',
          '&:hover': {
            bgcolor: isDark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.08)',
            transform: 'scale(1.05)'
          }
        }}
      >
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={isDark ? 'dark' : 'light'}
            initial={{ y: -20, opacity: 0, rotate: -90 }}
            animate={{ y: 0, opacity: 1, rotate: 0 }}
            exit={{ y: 20, opacity: 0, rotate: 90 }}
            transition={{ duration: 0.2 }}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          >
            {isDark ? (
              <LightModeIcon
                sx={{
                  fontSize: '1.375rem',
                  color: '#FFD54F'
                }}
              />
            ) : (
              <DarkModeIcon
                sx={{
                  fontSize: '1.375rem',
                  color: '#5C6BC0'
                }}
              />
            )}
          </motion.div>
        </AnimatePresence>
      </IconButton>
    </Tooltip>
  );
}
