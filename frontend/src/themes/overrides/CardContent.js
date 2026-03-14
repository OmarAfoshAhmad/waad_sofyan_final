// ==============================|| OVERRIDES - CARD CONTENT ||============================== //

export default function CardContent() {
  return {
    MuiCardContent: {
      styleOverrides: {
        root: {
          padding: '1.25rem',
          '&:last-child': {
            paddingBottom: '1.25rem'
          }
        }
      }
    }
  };
}
