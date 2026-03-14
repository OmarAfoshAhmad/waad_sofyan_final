import { useMemo, useState } from 'react';

// material-ui
import { useColorScheme } from '@mui/material/styles';
import Accordion from '@mui/material/Accordion';
import AccordionGroup from 'components/@extended/AccordionGroup';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import ThemeLayout from './ThemeLayout';
import DefaultThemeMode from './ThemeMode';
import ColorScheme from './ColorScheme';
import ThemeWidth from './ThemeWidth';
import ThemeFont from './ThemeFont';
import ThemeMenuDirection from './ThemeMenuDirection';

import MainCard from 'components/MainCard';
import Avatar from 'components/@extended/Avatar';
import IconButton from 'components/@extended/IconButton';
import SimpleBar from 'components/third-party/SimpleBar';

import { ThemeMode } from 'config';

// assets
import LayoutOutlined from '@ant-design/icons/LayoutOutlined';
import HighlightOutlined from '@ant-design/icons/HighlightOutlined';
import BorderInnerOutlined from '@ant-design/icons/BorderInnerOutlined';
import BgColorsOutlined from '@ant-design/icons/BgColorsOutlined';
import SettingOutlined from '@ant-design/icons/SettingOutlined';
import CloseCircleOutlined from '@ant-design/icons/CloseCircleOutlined';
import FontColorsOutlined from '@ant-design/icons/FontColorsOutlined';

// ==============================|| HEADER CONTENT - CUSTOMIZATION ||============================== //

export default function Customization() {
  const { colorScheme } = useColorScheme();
  const themeLayout = useMemo(() => <ThemeLayout />, []);
  const themeMenuDirection = useMemo(() => <ThemeMenuDirection />, []);
  const themeMode = useMemo(() => <DefaultThemeMode />, []);
  const themeColor = useMemo(() => <ColorScheme />, []);
  const themeWidth = useMemo(() => <ThemeWidth />, []);
  const themeFont = useMemo(() => <ThemeFont />, []);

  const [open, setOpen] = useState(false);
  const handleToggle = () => {
    setOpen(!open);
  };

  const closeIcon = { color: colorScheme === ThemeMode.DARK ? 'text.primary' : 'background.paper' };

  return (
    <>
      <Box sx={{ flexShrink: 0 }}>
        <Tooltip title="تخصيص المظهر">
          <IconButton
            color="secondary"
            variant="light"
            sx={(theme) => ({
              color: 'text.primary',
              bgcolor: open ? 'grey.100' : 'transparent',
              ...theme.applyStyles('dark', { bgcolor: open ? 'background.default' : 'transparent' }),
              '& svg': {
                animation: 'spin 2s linear infinite',
                '@keyframes spin': {
                  from: { transform: 'rotate(0deg)' },
                  to: { transform: 'rotate(360deg)' }
                }
              }
            })}
            onClick={handleToggle}
            aria-label="settings toggler"
          >
            <SettingOutlined />
          </IconButton>
        </Tooltip>
      </Box>
      <Drawer sx={{ zIndex: 2001 }} anchor="right" onClose={handleToggle} open={open} slotProps={{ paper: { sx: { width: '21.25rem' } } }}>
        {open && (
          <MainCard
            title="تخصيص المظهر"
            sx={{
              border: 'none',
              borderRadius: 0,
              height: '100vh',
              '& .MuiCardHeader-root': { ...closeIcon, bgcolor: 'primary.main', '& .MuiTypography-root': { fontSize: '1rem' } }
            }}
            content={false}
            secondary={
              <IconButton
                color="secondary"
                shape="rounded"
                size="small"
                onClick={handleToggle}
                sx={{ ...closeIcon, '&:hover': { bgcolor: 'transparent', color: 'error.main' } }}
              >
                <CloseCircleOutlined style={{ fontSize: '1.15rem' }} />
              </IconButton>
            }
          >
            <SimpleBar sx={{ height: 'calc(100vh - 70px)', '& .simplebar-content': { display: 'flex', flexDirection: 'column' } }}>
              <AccordionGroup variant="settings">
                <Accordion defaultExpanded sx={{ borderTop: 'none' }}>
                  <AccordionSummary aria-controls="panel1d-content" id="panel1d-header">
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center' }}>
                      <Avatar alt="settings toggler" variant="rounded">
                        <LayoutOutlined />
                      </Avatar>
                      <Stack>
                        <Typography variant="subtitle1" color="text.primary">
                          نوع التخطيط
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          اختر تخطيط الواجهة
                        </Typography>
                      </Stack>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>{themeLayout}</AccordionDetails>
                </Accordion>
                <Accordion defaultExpanded>
                  <AccordionSummary aria-controls="panel4d-content" id="panel4d-header">
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center' }}>
                      <Avatar alt="settings toggler" variant="rounded">
                        <BorderInnerOutlined />
                      </Avatar>
                      <Stack>
                        <Typography variant="subtitle1" color="text.primary">
                          اتجاه القائمة
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          اختر من اليمين لليسار أو اليسار لليمين
                        </Typography>
                      </Stack>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>{themeMenuDirection}</AccordionDetails>
                </Accordion>
                <Accordion defaultExpanded>
                  <AccordionSummary aria-controls="panel3d-content" id="panel3d-header">
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center' }}>
                      <Avatar alt="settings toggler" variant="rounded">
                        <BgColorsOutlined />
                      </Avatar>
                      <Stack>
                        <Typography variant="subtitle1" color="text.primary">
                          نظام الألوان
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          اختر لون السمة الرئيسي
                        </Typography>
                      </Stack>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>{themeColor}</AccordionDetails>
                </Accordion>
                <Accordion defaultExpanded>
                  <AccordionSummary aria-controls="panel4d-content" id="panel4d-header">
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center' }}>
                      <Avatar alt="settings toggler" variant="rounded">
                        <BorderInnerOutlined />
                      </Avatar>
                      <Stack>
                        <Typography variant="subtitle1" color="text.primary">
                          عرض التخطيط
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          اختر العرض المرن أو المحدود
                        </Typography>
                      </Stack>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>{themeWidth}</AccordionDetails>
                </Accordion>
                <Accordion defaultExpanded sx={{ borderBottom: 'none' }}>
                  <AccordionSummary aria-controls="panel4d-content" id="panel4d-header">
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center' }}>
                      <Avatar alt="settings toggler" variant="rounded">
                        <FontColorsOutlined />
                      </Avatar>
                      <Stack>
                        <Typography variant="subtitle1" color="text.primary">
                          نوع الخط
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          اختر نوع الخط المستخدم
                        </Typography>
                      </Stack>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>{themeFont}</AccordionDetails>
                </Accordion>
              </AccordionGroup>
            </SimpleBar>
          </MainCard>
        )}
      </Drawer>
    </>
  );
}


