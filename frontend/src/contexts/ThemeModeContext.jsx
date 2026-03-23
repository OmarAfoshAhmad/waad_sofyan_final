import PropTypes from 'prop-types';
import { createContext, useContext, useEffect, useCallback, useMemo } from 'react';
import { useColorScheme } from '@mui/material/styles';
import AuthContext from 'contexts/AuthContext';

// ==============================|| THEME MODE CONTEXT ||============================== //

const ThemeModeContext = createContext(null);

export const useThemeMode = () => {
  const context = useContext(ThemeModeContext);
  if (!context) throw new Error('useThemeMode must be used within ThemeModeProvider');
  return context;
};

export const ThemeModeProvider = ({ children }) => {
  const auth = useContext(AuthContext);
  const { mode, setMode } = useColorScheme();

  // 1️⃣ Determine userId (Fallback to 'guest')
  const userId = auth?.user?.id || 'guest';
  const storageKey = `theme-mode:${userId}`;

  // 2️⃣ Sync MUI mode with our custom persistence on mount or user change
  useEffect(() => {
    const savedMode = localStorage.getItem(storageKey);
    
    if (savedMode && (savedMode === 'light' || savedMode === 'dark')) {
      if (mode !== savedMode) {
        setMode(savedMode);
      }
    } else {
      // If no user-specific setting, stick to current or light
      if (!mode) {
        setMode('light');
      }
    }
  }, [userId, setMode]);

  // 3️⃣ Toggle function
  const toggleTheme = useCallback(() => {
    // If mode is undefined or system, we default to toggling away from light
    const currentActiveMode = mode === 'dark' ? 'dark' : 'light';
    const newMode = currentActiveMode === 'dark' ? 'light' : 'dark';
    
    setMode(newMode);
    localStorage.setItem(storageKey, newMode);
    // Also sync with MUI's default key for safety
    localStorage.setItem('theme-mode', newMode);
  }, [mode, setMode, storageKey]);

  const value = useMemo(() => ({
    mode: mode || 'light',
    toggleTheme,
    userId
  }), [mode, toggleTheme, userId]);

  return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>;
};

ThemeModeProvider.propTypes = {
  children: PropTypes.node
};

export default ThemeModeContext;
