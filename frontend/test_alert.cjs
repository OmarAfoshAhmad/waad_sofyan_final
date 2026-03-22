const { createTheme } = require('@mui/material/styles');
const t = createTheme({
  cssVariables: { cssVarPrefix: '' },
  colorSchemes: { light: { palette: {
    primary: { main: '#00838F', light: '#26C6DA', dark: '#006064', contrastText: '#fff', lighter: '#E0F7FA' },
    info: { main: '#00A2AE', light: '#26B0BA', dark: '#009AA7', contrastText: '#fff', lighter: '#E0F4F5' },
    Alert: { infoStandardBg: '#E0F7FA', infoIconColor: '#00838F', infoColor: '#006064' }
  }}}
});
const themeVars = t.generateThemeVars();
const alertKeys = Object.keys(themeVars).filter(k => k.indexOf('Alert') > -1);
console.log('=== Alert CSS Vars Generated ===');
alertKeys.forEach(k => console.log(k + ': ' + themeVars[k]));
console.log('\n=== palette.Alert actual values ===');
const alertPalette = t.colorSchemes.light.palette.Alert;
Object.keys(alertPalette).filter(k => k.indexOf('info') > -1 || k.indexOf('Info') > -1).forEach(k => console.log(k + ': ' + alertPalette[k]));
