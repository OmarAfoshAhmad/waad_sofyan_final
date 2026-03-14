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
import { MenuOrientation } from 'config';
import { handlerDrawerOpen } from 'api/menu';

// assets
import defaultLayout from 'assets/images/customization/default.svg';
import horizontalLayout from 'assets/images/customization/horizontal.svg';
import miniMenu from 'assets/images/customization/mini-menu.svg';

const layouts = [
  { value: MenuOrientation.VERTICAL, label: 'افتراضي', img: defaultLayout },
  { value: MenuOrientation.HORIZONTAL, label: 'أفقي', img: horizontalLayout },
  { value: MenuOrientation.MINI_VERTICAL, label: 'مصغر', img: miniMenu }
];

// ==============================|| CUSTOMIZATION - THEME LAYOUT/ORIENTATION ||============================== //

export default function ThemeLayout() {
  const { state, setField } = useConfig();

  const handleRadioChange = (event) => {
    const newValue = event.target.value;
    setField('menuOrientation', newValue);
    handlerDrawerOpen(newValue === MenuOrientation.MINI_VERTICAL ? false : true);
  };

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
              ...(state.menuOrientation === layoutValue && { ...activeCardStyle(theme) })
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
    <RadioGroup row aria-label="theme-layout" name="theme-layout" value={state.menuOrientation} onChange={handleRadioChange}>
      <Grid container spacing={2.5}>
        {layouts.map((layout) => renderLayoutCard(layout))}
      </Grid>
    </RadioGroup>
  );
}
