import PropTypes from 'prop-types';
import { createContext, useContext, useEffect, useCallback } from 'react';
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

  // 2️⃣ Rehydrate on user change
  useEffect(() => {
    // Force 'light' mode at all times as per business requirement
    if (mode !== 'light') {
      setMode('light');
    }
  }, [setMode, mode]);

  // 4️⃣ Toggle function (DISABLED: Force light)
  const toggleTheme = useCallback(() => {
    setMode('light');
  }, [setMode]);

  return <ThemeModeContext.Provider value={{ mode: 'light', toggleTheme, userId }}>{children}</ThemeModeContext.Provider>;
};

ThemeModeProvider.propTypes = {
  children: PropTypes.node
};

export default ThemeModeContext;
