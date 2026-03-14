// ==============================|| OVERRIDES - MENU ||============================== //

export default function Menu(theme) {
    return {
        MuiMenu: {
            defaultProps: {
                disableScrollLock: true
            },
            styleOverrides: {
                paper: {
                    minWidth: 160.0,
                    boxShadow: theme.customShadows?.z1
                },
                list: {
                    padding: '0.375rem'
                }
            }
        },
        MuiMenuItem: {
            styleOverrides: {
                root: {
                    fontSize: '0.875rem',
                    padding: '8px 12px',
                    borderRadius: 4.0,
                    '&:hover': {
                        backgroundColor: theme.palette?.primary?.lighter ?? (theme.palette && theme.palette.primary ? theme.palette.primary.light + '20' : '#f5f5f5')
                    },
                    '&.Mui-selected': {
                        backgroundColor: theme.palette?.primary?.lighter ?? (theme.palette && theme.palette.primary ? theme.palette.primary.light + '40' : '#e3f2fd'),
                        '&:hover': {
                            backgroundColor: theme.palette?.primary?.lighter ?? (theme.palette && theme.palette.primary ? theme.palette.primary.light + '60' : '#bbdefb')
                        }
                    }
                }
            }
        }
    };
}

