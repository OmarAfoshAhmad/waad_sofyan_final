// ==============================|| DEFAULT THEME - TYPOGRAPHY ||============================== //

export default function Typography(fontFamily, fontSize) {
  return {
    fontSize,
    htmlFontSize: 16,
    fontFamily,
    fontWeightLight: 300,
    fontWeightRegular: 400,
    fontWeightMedium: 500,
    fontWeightBold: 600,
    h1: {
      fontWeight: 600,
      fontSize: '2.625rem', // 42px - increased from 38px
      lineHeight: 1.25
    },
    h2: {
      fontWeight: 600,
      fontSize: '2.125rem', // 34px - increased from 30px
      lineHeight: 1.3
    },
    h3: {
      fontWeight: 600,
      fontSize: '1.75rem', // 28px - increased from 24px
      lineHeight: 1.35
    },
    h4: {
      fontWeight: 600,
      fontSize: '1.5rem', // 24px - increased from 20px
      lineHeight: 1.4
    },
    h5: {
      fontWeight: 600,
      fontSize: '1.125rem', // 18px - increased from 16px
      lineHeight: 1.5
    },
    h6: {
      fontWeight: 500, // increased from 400 for better visibility
      fontSize: '1rem', // 16px - increased from 14px
      lineHeight: 1.6
    },
    caption: {
      fontWeight: 400,
      fontSize: '0.8125rem', // 13px - increased from 12px
      lineHeight: 1.7
    },
    body1: {
      fontSize: '1rem', // 16px - increased from 14px (main content)
      lineHeight: 1.6
    },
    body2: {
      fontSize: '0.875rem', // 14px - increased from 12px (secondary content)
      lineHeight: 1.65
    },
    subtitle1: {
      fontSize: '1rem', // 16px - increased from 14px
      fontWeight: 600,
      lineHeight: 1.6
    },
    subtitle2: {
      fontSize: '0.875rem', // 14px - increased from 12px
      fontWeight: 500,
      lineHeight: 1.65
    },
    overline: {
      fontSize: '0.75rem', // 12px
      lineHeight: 1.7,
      textTransform: 'uppercase',
      letterSpacing: '0.5px'
    },
    button: {
      textTransform: 'capitalize',
      fontSize: '0.9375rem', // 15px - slightly increased for better readability
      fontWeight: 500
    }
  };
}
