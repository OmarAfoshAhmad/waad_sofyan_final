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
import rtlLayout from 'assets/images/customization/rtl.svg';

const layouts = [
  { value: 'ltr', label: 'يسار لليمين', img: defaultLayout },
  { value: 'rtl', label: 'يمين لليسار', img: rtlLayout }
];

// ==============================|| CUSTOMIZATION - MENU DIRECTION ||============================== //

export default function ThemeMenuDirection() {
  const { state, setField } = useConfig();

  const activeCardStyle = (theme) => ({
    bgcolor: 'primary.lighter',
    boxShadow: theme.vars.customShadows.primary,
    '&:hover': { boxShadow: theme.vars.customShadows.primary }
  });

  const renderLayoutCard = ({ value: layoutValue, label, img }) => (
    <Grid key={layoutValue}>
      <FormControlLabel
        value={layoutValue}
        control={<Radio sx={{ display: 'none' }} />}
        label={
          <MainCard
            content={false}
            border={false}
            boxShadow
            sx={(theme) => ({
              bgcolor: 'secondary.lighter',
              p: 1,
              ...(state.themeDirection === layoutValue && { ...activeCardStyle(theme) })
            })}
          >
            <Stack sx={{ gap: '0.625rem', alignItems: 'center' }}>
              <CardMedia component="img" src={img} alt={label} sx={{ borderRadius: 1, width: '3.75rem', height: '3.75rem' }} />
              <Typography variant="caption">{label}</Typography>
            </Stack>
          </MainCard>
        }
        sx={{ m: 0 }}
      />
    </Grid>
  );

  return (
    <RadioGroup
      row
      aria-label="menu-direction"
      name="menu-direction"
      value={state.themeDirection}
      onChange={(e) => setField('themeDirection', e.target.value)}
    >
      <Grid container spacing={2.5}>
        {layouts.map((layout) => renderLayoutCard(layout))}
      </Grid>
    </RadioGroup>
  );
}
