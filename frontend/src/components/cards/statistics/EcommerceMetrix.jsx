import PropTypes from 'prop-types';
// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| MATRIX CARD ||============================== //

export default function EcommerceMetrix({ primary, secondary, content, iconPrimary, color }) {
  const IconPrimary = iconPrimary;
  const primaryIcon = iconPrimary ? <IconPrimary fontSize="large" /> : null;

  return (
    <MainCard
      content={false}
      sx={{
        bgcolor: color,
        position: 'relative',
        '&:before, &:after': {
          content: '""',
          width: '0.0625rem',
          height: '0.0625rem',
          position: 'absolute',
          background: 'linear-gradient(90deg, rgba(255, 255, 255, 0.0001) 22.07%, rgba(255, 255, 255, 0.15) 83.21%)',
          transform: 'matrix(0.9, 0.44, -0.44, 0.9, 0, 0)'
        },
        '&:after': {
          top: '50%',
          right: '-20px'
        },
        '&:before': {
          right: '-70px',
          bottom: '80%'
        }
      }}
    >
      <Box sx={(theme) => ({ px: '2.25rem', py: '2.0rem', color: 'background.paper', ...theme.applyStyles('dark', { color: 'text.primary' }) })}>
        <Grid container sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Grid>
            <Typography sx={(theme) => ({ opacity: 0.23, fontSize: '3.5rem', lineHeight: 0, ...theme.applyStyles('dark', { opacity: 0.5 }) })}>
              {primaryIcon}
            </Typography>
          </Grid>
          <Grid>
            <Stack sx={{ gap: 1, alignItems: 'flex-end' }}>
              <Typography variant="h4" sx={{ fontWeight: 500 }}>
                {primary}
              </Typography>
              <Typography variant="h2">{secondary}</Typography>
            </Stack>
          </Grid>
        </Grid>
        <Stack direction="row" sx={{ gap: 1, justifyContent: 'flex-end', pt: '1.125rem' }}>
          <Typography variant="h5" sx={{ fontWeight: 400 }}>
            {content}
          </Typography>
        </Stack>
      </Box>
    </MainCard>
  );
}

EcommerceMetrix.propTypes = {
  primary: PropTypes.any,
  secondary: PropTypes.any,
  content: PropTypes.any,
  iconPrimary: PropTypes.any,
  color: PropTypes.any
};
