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
import useConfig from 'hooks/useConfig';
import { withAlpha } from 'utils/colorUtils';

// third-party
import { presetDarkPalettes, presetPalettes } from '@ant-design/colors';

// assets
import colorLayout from 'assets/images/customization/theme-color.svg';

// ==============================|| CUSTOMIZATION - COLOR SCHEME ||============================== //

export default function ColorScheme() {
  const { colorScheme } = useColorScheme();
  const { state, setField } = useConfig();

  const colors = colorScheme === ThemeMode.DARK ? presetDarkPalettes : presetPalettes;
  const { blue } = colors;
  const colorOptions = [
    {
      id: 'default',
      primary: blue[5],
      lighter: blue[0],
      label: 'افتراضي',
      shadow: `0 0 0 2px ${withAlpha(blue[5], 0.2)}`
    },
    {
      id: 'theme1',
      primary: colorScheme === ThemeMode.DARK ? '#305bdd' : '#3366FF',
      lighter: colorScheme === ThemeMode.DARK ? '#1c2134' : '#D6E4FF',
      label: 'سمة 1',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#305bdd' : '#3366FF', 0.2)}`
    },
    {
      id: 'theme2',
      primary: colorScheme === ThemeMode.DARK ? '#655ac8' : '#7265E6',
      lighter: colorScheme === ThemeMode.DARK ? '#222130' : '#EEEDFC',
      label: 'سمة 2',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#655ac8' : '#7265E6', 0.2)}`
    },
    {
      id: 'theme3',
      primary: colorScheme === ThemeMode.DARK ? '#0a7d3e' : '#068e44',
      lighter: colorScheme === ThemeMode.DARK ? '#1a231f' : '#E6F3EC',
      label: 'سمة 3',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#0a7d3e' : '#068e44', 0.2)}`
    },
    {
      id: 'theme4',
      primary: colorScheme === ThemeMode.DARK ? '#5d7dcb' : '#3c64d0',
      lighter: colorScheme === ThemeMode.DARK ? '#1d212d' : '#f0f6ff',
      label: 'سمة 4',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#5d7dcb' : '#3c64d0', 0.2)}`
    },
    {
      id: 'theme5',
      primary: colorScheme === ThemeMode.DARK ? '#d26415' : '#f27013',
      lighter: colorScheme === ThemeMode.DARK ? '#32221a' : '#fff4e6',
      label: 'سمة 5',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#d26415' : '#f27013', 0.2)}`
    },
    {
      id: 'theme6',
      primary: colorScheme === ThemeMode.DARK ? '#288d99' : '#2aa1af',
      lighter: colorScheme === ThemeMode.DARK ? '#1c2628' : '#e1f0ef',
      label: 'سمة 6',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#288d99' : '#2aa1af', 0.2)}`
    },
    {
      id: 'theme7',
      primary: colorScheme === ThemeMode.DARK ? '#05934c' : '#00a854',
      lighter: colorScheme === ThemeMode.DARK ? '#1a2721' : '#d1e8d99c',
      label: 'سمة 7',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#05934c' : '#00a854', 0.2)}`
    },
    {
      id: 'theme8',
      primary: colorScheme === ThemeMode.DARK ? '#058478' : '#009688',
      lighter: colorScheme === ThemeMode.DARK ? '#1a2524' : '#c1d6d066',
      label: 'سمة 8',
      shadow: `0 0 0 2px ${withAlpha(colorScheme === ThemeMode.DARK ? '#058478' : '#009688', 0.2)}`
    }
  ];

  const handlePresetColorChange = (event) => {
    setField('presetColor', event.target.value);
  };

  return (
    <RadioGroup row aria-label="color-scheme" name="color-scheme" value={state.presetColor} onChange={handlePresetColorChange}>
      <Grid container rowSpacing={2} columnSpacing={2.5}>
        {colorOptions.map((color, index) => (
          <Grid key={index}>
            <FormControlLabel
              control={<Radio value={color.id} sx={{ display: 'none' }} />}
              label={
                <MainCard
                  content={false}
                  border={false}
                  boxShadow
                  sx={{
                    bgcolor: 'secondary.lighter',
                    p: 1,
                    ...(state.presetColor === color.id && {
                      bgcolor: color.lighter,
                      boxShadow: color.shadow,
                      '&:hover': { boxShadow: color.shadow }
                    })
                  }}
                >
                  <Stack sx={{ gap: '0.75rem', alignItems: 'center' }}>
                    <CardMedia
                      component="img"
                      src={colorLayout}
                      alt="Vertical"
                      sx={{
                        border: '1px solid',
                        borderColor: color.primary,
                        borderRadius: 1,
                        bgcolor: color.primary,
                        width: '2.5rem',
                        height: '2.5rem'
                      }}
                    />
                    <Typography variant="caption">{color.label}</Typography>
                  </Stack>
                </MainCard>
              }
              sx={{ m: 0 }}
            />
          </Grid>
        ))}
      </Grid>
    </RadioGroup>
  );
}


