// ==============================|| OVERRIDES - TREE ITEM ||============================== //

export default function TreeItem() {
  return {
    MuiTreeItem: {
      styleOverrides: {
        content: {
          padding: '0.375rem'
        },
        iconContainer: {
          '& svg': {
            fontSize: '0.75rem'
          }
        },
        groupTransition: {
          paddingLeft: '0.75rem'
        }
      }
    }
  };
}



