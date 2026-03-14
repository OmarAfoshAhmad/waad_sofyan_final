// ==============================|| OVERRIDES - TABLE CELL ||============================== //

export default function TableCell(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const commonCell = {
    fontSize: '0.875rem', // 14px - increased from 12px for better readability
    textTransform: 'uppercase',
    '&:not(:last-of-type)': {
      backgroundImage: `linear-gradient(${varsPalette.divider ?? theme.palette.divider}, ${varsPalette.divider ?? theme.palette.divider})`,
      backgroundRepeat: 'no-repeat',
      /* 1px wide, shorter than full height */
      backgroundSize: '1px calc(100% - 30px)',
      /* 16px from top */
      backgroundPosition: 'right 16px'
    }
  };

  return {
    MuiTableCell: {
      styleOverrides: {
        root: ({ ownerState }) => {
          const baseStyle = {
            fontSize: '0.9375rem', // 15px - increased from 14px for body cells
            padding: '1.0rem', // increased from 12px for better spacing
            borderColor: varsPalette.divider ?? theme.palette.divider,
            lineHeight: 1.6 // improved line height for Arabic text
          };

          const align = ownerState.align;

          if (align === 'right') {
            return {
              ...baseStyle,
              justifyContent: 'flex-end',
              textAlign: 'right',
              '& > *': {
                justifyContent: 'flex-end',
                margin: '0 0 0 auto'
              },
              '& .MuiOutlinedInput-input': {
                textAlign: 'right'
              }
            };
          }

          if (align === 'center') {
            return {
              ...baseStyle,
              justifyContent: 'center',
              textAlign: 'center',
              '& > *': {
                justifyContent: 'center',
                margin: '0 auto'
              }
            };
          }

          return baseStyle;
        },
        sizeSmall: {
          padding: '0.75rem', // increased from 8px
          fontSize: '0.875rem' // 14px for small cells
        },
        head: {
          fontWeight: 700,
          fontSize: '0.9375rem', // 15px - increased for headers
          ...commonCell
        },
        footer: { ...commonCell }
      }
    }
  };
}


