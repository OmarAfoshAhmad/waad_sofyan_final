// material-ui
import { useColorScheme } from '@mui/material/styles';
import CardMedia from '@mui/material/CardMedia';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';

import { ThemeMode } from 'config';

// assets
import defaultLayout from 'assets/images/customization/default.svg';
import darkLayout from 'assets/images/customization/dark.svg';
import systemLayout from 'assets/images/customization/system.svg';

// ==============================|| CUSTOMIZATION - MODE ||============================== //

export default function ThemeModeLayout() {
  const { mode, setMode } = useColorScheme();

  const handleModeChange = (event) => {
    setMode(event.target.value);
  };

  const activeCardStyle = (theme) => ({
    bgcolor: 'primary.lighter',
    boxShadow: theme.vars.customShadows.primary,
    '&:hover': { boxShadow: theme.vars.customShadows.primary }
  });

  return (
    <RadioGroup row aria-label="payment-card" name="payment-card" value={mode} onChange={handleModeChange}>
      <Grid container spacing={2.5}>
        <Grid>
          <FormControlLabel
            control={<Radio value="light" sx={{ display: 'none' }} />}
            label={
              <MainCard
                content={false}
                border={false}
                boxShadow
                sx={(theme) => ({
                  bgcolor: 'secondary.lighter',
                  p: 1,
                  ...(mode === ThemeMode.LIGHT && { ...activeCardStyle(theme) })
                })}
              >
                <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
                  <CardMedia component="img" src={defaultLayout} alt="Vertical" sx={{ borderRadius: 1, width: '4.0rem', height: '4.0rem' }} />
                  <Typography variant="caption">فاتح</Typography>
                </Stack>
              </MainCard>
            }
            sx={{ m: 0 }}
          />
        </Grid>
        <Grid>
          <FormControlLabel
            control={<Radio value="dark" sx={{ display: 'none' }} />}
            label={
              <MainCard
                content={false}
                border={false}
                boxShadow
                sx={(theme) => ({
                  bgcolor: 'secondary.lighter',
                  p: 1,
                  ...(mode == ThemeMode.DARK && { ...activeCardStyle(theme) })
                })}
              >
                <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
                  <CardMedia component="img" src={darkLayout} alt="Vertical" sx={{ borderRadius: 1, width: '4.0rem', height: '4.0rem' }} />
                  <Typography variant="caption">داكن</Typography>
                </Stack>
              </MainCard>
            }
            sx={{ m: 0 }}
          />
        </Grid>
        <Grid>
          <FormControlLabel
            control={<Radio value="system" sx={{ display: 'none' }} />}
            label={
              <MainCard
                content={false}
                border={false}
                boxShadow
                sx={(theme) => ({
                  bgcolor: 'secondary.lighter',
                  p: 1,
                  ...(mode == ThemeMode.SYSTEM && { ...activeCardStyle(theme) })
                })}
              >
                <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
                  <CardMedia component="img" src={systemLayout} alt="Vertical" sx={{ borderRadius: 1, width: '4.0rem', height: '4.0rem' }} />
                  <Typography variant="caption">النظام</Typography>
                </Stack>
              </MainCard>
            }
            sx={{ m: 0 }}
          />
        </Grid>
      </Grid>
    </RadioGroup>
  );
}
