import CardMedia from '@mui/material/CardMedia';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';
import useConfig from 'hooks/useConfig';

// assets
import defaultLayout from 'assets/images/customization/default.svg';
import containerLayout from 'assets/images/customization/container.svg';

// ==============================|| CUSTOMIZATION - CONTAINER ||============================== //

export default function ThemeWidth() {
  const { state, setField } = useConfig();

  const handleContainerChange = (e) => {
    const newValue = e.target.value === 'container';
    setField('container', newValue);
  };

  const activeCardStyle = (theme) => ({
    bgcolor: 'primary.lighter',
    boxShadow: theme.vars.customShadows.primary,
    '&:hover': { boxShadow: theme.vars.customShadows.primary }
  });

  return (
    <RadioGroup
      row
      aria-label="theme-width"
      name="theme-width"
      value={state.container ? 'container' : 'fluid'}
      onChange={handleContainerChange}
    >
      <Grid container spacing={2.5}>
        <Grid>
          <FormControlLabel
            control={<Radio value="fluid" sx={{ display: 'none' }} />}
            label={
              <MainCard
                content={false}
                border={false}
                boxShadow
                sx={(theme) => ({ bgcolor: 'secondary.lighter', p: 1, ...(!state.container && { ...activeCardStyle(theme) }) })}
              >
                <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
                  <CardMedia component="img" src={defaultLayout} alt="Vertical" sx={{ borderRadius: 1, width: '4.0rem', height: '4.0rem' }} />
                  <Typography variant="caption">مرن</Typography>
                </Stack>
              </MainCard>
            }
            sx={{ m: 0 }}
          />
        </Grid>
        <Grid>
          <FormControlLabel
            control={<Radio value="container" sx={{ display: 'none' }} />}
            label={
              <MainCard
                content={false}
                border={false}
                boxShadow
                sx={(theme) => ({ bgcolor: 'secondary.lighter', p: 1, ...(state.container && { ...activeCardStyle(theme) }) })}
              >
                <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
                  <CardMedia component="img" src={containerLayout} alt="Vertical" sx={{ borderRadius: 1, width: '4.0rem', height: '4.0rem' }} />
                  <Typography variant="caption">محدود</Typography>
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
