import PropTypes from 'prop-types';
//material-ui
import { styled } from '@mui/material/styles';

// third-party
import { SnackbarProvider } from 'notistack';

// project imports
import Loader from 'components/Loader';
import { useGetSnackbar } from 'api/snackbar';

// assets
import CheckCircleOutlined from '@ant-design/icons/CheckCircleOutlined';
import CloseCircleOutlined from '@ant-design/icons/CloseCircleOutlined';
import InfoCircleOutlined from '@ant-design/icons/InfoCircleOutlined';
import WarningOutlined from '@ant-design/icons/WarningOutlined';

// custom styles
const StyledSnackbarProvider = styled(SnackbarProvider)(({ theme }) => {
  const varsPalette = (theme?.vars && theme.vars.palette) || theme.palette || {};
  const primaryVars = varsPalette.primary || theme.palette?.primary || {};
  const errorVars = varsPalette.error || theme.palette?.error || {};
  const successVars = varsPalette.success || theme.palette?.success || {};
  const infoVars = varsPalette.info || theme.palette?.info || {};
  const warningVars = varsPalette.warning || theme.palette?.warning || {};

  return {
    '&.notistack-MuiContent-default': {
      background: primaryVars.main ?? theme.palette?.primary?.main ?? '#1976d2'
    },
    '&.notistack-MuiContent-error': {
      background: errorVars.main ?? theme.palette?.error?.main ?? '#f44336'
    },
    '&.notistack-MuiContent-success': {
      background: successVars.main ?? theme.palette?.success?.main ?? '#4caf50'
    },
    '&.notistack-MuiContent-info': {
      background: infoVars.main ?? theme.palette?.info?.main ?? '#2196f3'
    },
    '&.notistack-MuiContent-warning': {
      background: warningVars.main ?? theme.palette?.warning?.main ?? '#ff9800'
    }
  };
});

// ===========================|| SNACKBAR - NOTISTACK ||=========================== //

export default function Notistack({ children }) {
  const snackbar = useGetSnackbar();
  const iconSX = { marginRight: '0.375rem', fontSize: '1.15rem' };

  if (snackbar === undefined) return <Loader />;

  return (
    <StyledSnackbarProvider
      maxSnack={snackbar.maxStack}
      dense={snackbar.dense}
      iconVariant={
        snackbar.iconVariant === 'useemojis'
          ? {
              success: <CheckCircleOutlined style={iconSX} />,
              error: <CloseCircleOutlined style={iconSX} />,
              warning: <WarningOutlined style={iconSX} />,
              info: <InfoCircleOutlined style={iconSX} />
            }
          : undefined
      }
      hideIconVariant={snackbar.iconVariant === 'hide' ? true : false}
    >
      {children}
    </StyledSnackbarProvider>
  );
}

Notistack.propTypes = { children: PropTypes.any };


