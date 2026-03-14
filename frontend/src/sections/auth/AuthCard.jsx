import PropTypes from 'prop-types';
// material-ui
import { alpha, useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| AUTHENTICATION - CARD WRAPPER ||============================== //

export default function AuthCard({ children, ...other }) {
  const theme = useTheme();

  return (
    <MainCard
      sx={{
        maxWidth: { xs: 400, sm: '237.5rem' },
        mx: 'auto',
        my: { xs: 2.5, md: 3 },
        '& > *': { flexGrow: 1, flexBasis: '50%' },
        backdropFilter: 'blur(20px)',

        // Modern Medical Aesthetic
        background: alpha(theme.palette.background.paper, 0.95),
        border: `1px solid ${alpha(theme.palette.primary.light, 0.2)}`, // Soft medical teal border
        borderRadius: '0.375rem',
        boxShadow: `0 12px 40px ${alpha(theme.palette.primary.main, 0.08)}`,

        transition: 'transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275), box-shadow 0.4s ease',

        '&:hover': {
          transform: 'translateY(-4px)',
          borderColor: alpha(theme.palette.primary.main, 0.4),
          boxShadow: `0 20px 60px ${alpha(theme.palette.primary.main, 0.12)}`
        }
      }}
      content={false}
      {...other}
      border={false}
    >
      <Box sx={{ p: { xs: 2, sm: 3, md: 4, xl: 5 } }}>{children}</Box>
    </MainCard>
  );
}

AuthCard.propTypes = { children: PropTypes.any, other: PropTypes.any };


