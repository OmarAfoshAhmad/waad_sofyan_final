import PropTypes from 'prop-types';
import { useEffect } from 'react';

// material-ui
import createCache from '@emotion/cache';
import { CacheProvider } from '@emotion/react';

// third-party
import rtlPlugin from 'stylis-plugin-rtl';

// project imports
import { ThemeDirection } from 'config';
import useConfig from 'hooks/useConfig';

const rtlCache = createCache({
  key: 'muirtl',
  stylisPlugins: [rtlPlugin]
});

const ltrCache = createCache({
  key: 'mui'
});

export default function RTLLayout({ children }) {
  const { state, setField } = useConfig();

  useEffect(() => {
    document.documentElement.dir = state.themeDirection;
    document.body.dir = state.themeDirection;
    // Set global font size for rem scaling
    document.documentElement.style.fontSize = `${state.fontSize}px`;
  }, [state.themeDirection, state.fontSize]);

  // Sync direction with language changes
  useEffect(() => {
    const expectedDirection = state.i18n === 'ar' ? ThemeDirection.RTL : ThemeDirection.LTR;
    if (state.themeDirection !== expectedDirection) {
      setField('themeDirection', expectedDirection);
    }
  }, [state.i18n, state.themeDirection, setField]);

  return <CacheProvider value={state.themeDirection === ThemeDirection.RTL ? rtlCache : ltrCache}>{children}</CacheProvider>;
}

RTLLayout.propTypes = { children: PropTypes.node };
