import PropTypes from 'prop-types';
import { createContext, useEffect, useMemo } from 'react';

// project imports
import config from 'config';
import { useLocalStorage } from 'hooks/useLocalStorage';

// ==============================|| CONFIG CONTEXT ||============================== //

export const ConfigContext = createContext(undefined);

const LEGACY_CAIRO_DEFAULT = `'Cairo', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', sans-serif`;

// ==============================|| CONFIG PROVIDER ||============================== //

export function ConfigProvider({ children }) {
  const { state, setState, setField, resetState } = useLocalStorage('tba-waad-system-config', config);

  useEffect(() => {
    if (state?.fontFamily === LEGACY_CAIRO_DEFAULT) {
      setField('fontFamily', config.fontFamily);
    }
  }, [setField, state?.fontFamily]);

  const memoizedValue = useMemo(() => ({ state, setState, setField, resetState }), [state, setField, setState, resetState]);

  return <ConfigContext.Provider value={memoizedValue}>{children}</ConfigContext.Provider>;
}

ConfigProvider.propTypes = { children: PropTypes.node };
