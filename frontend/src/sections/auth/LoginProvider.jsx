import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';

// material-ui
import useMediaQuery from '@mui/material/useMediaQuery';
import Button from '@mui/material/Button';
import CardMedia from '@mui/material/CardMedia';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';

// project imports
import { APP_AUTH, AuthProvider } from 'config';

// assets
import Jwt from 'assets/images/icons/jwt.svg';
// removed external auth icons

// ==============================|| SOCIAL BUTTON ||============================== //

export default function LoginProvider({ currentLoginWith }) {
  const downSM = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const navigate = useNavigate();

  const loginHandlers = {
    Jwt: () => navigate(APP_AUTH === AuthProvider.JWT ? '/login' : '/jwt/login?auth=jwt')
  };

  const buttonData = [{ name: 'jwt', icon: Jwt, handler: loginHandlers.Jwt }];

  const currentLoginExists = buttonData.some((button) => button.name === currentLoginWith);

  return (
    <Stack
      direction="row"
      sx={{
        gap: { xs: 1, sm: '1.0rem' },
        justifyContent: { xs: 'space-around', sm: 'space-between' },
        '& .MuiButton-startIcon': { mr: { xs: 0, sm: 1 }, ml: { xs: 0, sm: -0.5 } }
      }}
    >
      {buttonData
        .filter((button) => {
          if (currentLoginExists) {
            return button.name !== currentLoginWith;
          }
          return button.name !== APP_AUTH;
        })
        .map((button) => (
          <Tooltip title={button.name} key={button.name}>
            <Button
              sx={{ borderColor: 'grey.300', color: 'grey.900', '&:hover': { borderColor: 'primary.400', backgroundColor: 'primary.100' } }}
              variant="outlined"
              color="secondary"
              fullWidth
              startIcon={<CardMedia component="img" sx={{ width: '1.625rem' }} src={button.icon} alt={button.name} />}
              onClick={button.handler}
            >
              {!downSM && button.name}
            </Button>
          </Tooltip>
        ))}
    </Stack>
  );
}

LoginProvider.propTypes = { currentLoginWith: PropTypes.string };
