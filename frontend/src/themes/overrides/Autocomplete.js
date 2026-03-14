// ==============================|| OVERRIDES - AUTOCOMPLETE ||============================== //

export default function Autocomplete() {
  return {
    MuiAutocomplete: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            padding: '3px 9px'
          }
        },
        paper: {
          minWidth: 200.0,
          boxShadow: '0px 8px 24px -4px rgba(0, 0, 0, 0.12)'
        },
        listbox: {
          '& .MuiAutocomplete-option': {
            fontSize: '0.875rem',
            padding: '8px 12px'
          }
        },
        popupIndicator: {
          width: 'auto',
          height: 'auto'
        },
        clearIndicator: {
          width: 'auto',
          height: 'auto'
        }
      }
    }
  };
}
