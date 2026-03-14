// ==============================|| OVERRIDES - SELECT ||============================== //

export default function Select() {
    return {
        MuiSelect: {
            styleOverrides: {
                select: {
                    '&:focus': {
                        backgroundColor: 'transparent'
                    }
                }
            },
            defaultProps: {
                MenuProps: {
                    PaperProps: {
                        style: {
                            minWidth: 220.0,
                            marginTop: '2.0rem'
                        }
                    }
                }
            }
        }
    };
}
