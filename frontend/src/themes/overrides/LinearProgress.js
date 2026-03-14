// ==============================|| OVERRIDES - LINER PROGRESS ||============================== //

export default function LinearProgress() {
  return {
    MuiLinearProgress: {
      styleOverrides: {
        root: {
          height: 6.0,
          borderRadius: 100.0
        },
        bar: {
          borderRadius: 100.0
        }
      }
    }
  };
}
