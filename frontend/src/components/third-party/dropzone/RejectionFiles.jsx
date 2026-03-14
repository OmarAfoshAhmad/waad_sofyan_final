import PropTypes from 'prop-types';
// material-ui
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// utils
import { withAlpha } from 'utils/colorUtils';
import getDropzoneData from 'utils/getDropzoneData';

export default function RejectionFiles({ fileRejections }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        py: 1,
        px: '1.0rem',
        mt: '1.5rem',
        borderColor: 'error.light',
        bgcolor: (theme) => {
          const varsPalette = (theme?.vars && theme.vars.palette) || theme.palette || {};
          const errorVars = varsPalette.error || theme.palette?.error || {};
          const errorMain = errorVars.main ?? theme.palette?.error?.main ?? '#f44336';
          return withAlpha(errorMain, 0.08);
        }
      }}
    >
      {fileRejections.map(({ file, errors }) => {
        const { path, size } = getDropzoneData(file);

        return (
          <Box key={path} sx={{ my: 1 }}>
            <Typography variant="subtitle2" noWrap>
              {path} - {size ? size : ''}
            </Typography>

            {errors.map((error) => (
              <Box key={error.code} component="li" sx={{ typography: 'caption' }}>
                {error.message}
              </Box>
            ))}
          </Box>
        );
      })}
    </Paper>
  );
}

RejectionFiles.propTypes = { fileRejections: PropTypes.array };
