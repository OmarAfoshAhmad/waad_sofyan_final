import { useNavigate } from 'react-router-dom';

// material-ui
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActionArea from '@mui/material/CardActionArea';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';

// icons
import {
  Assessment,
  Receipt,
  LocalHospital,
  People,
  BarChart,
  PieChart,
  TrendingUp,
  Construction,
  Business,
  Policy,
  ArrowForward
} from '@mui/icons-material';

// project imports
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// ==============================|| REPORTS PAGE ||============================== //

/**
 * Reports Page - صفحة التقارير
 *
 * Dashboard للوصول إلى جميع التقارير المتاحة في النظام
 */

// التقارير الفعالة والمطلوبة
const availableReports = [
  {
    id: 'claims-report',
    title: 'تقرير المطالبات (للمراجعة)',
    titleEn: 'Claims Review Report',
    description: 'تقرير تشغيلي لتتبع دورة حياة المطالبات ومراجعة الحالات الطبية',
    icon: Receipt,
    color: '#1976d2',
    path: '/reports/claims'
  },
  {
    id: 'provider-account-summary',
    title: 'كشف حساب المزودين (ملخص مالي)',
    titleEn: 'Provider Account Summary',
    description: 'تقرير إجمالي لصافي المستحقات (له/عليه) ونسب الخصم لكل مزود',
    icon: Assessment,
    color: '#2e7d32',
    path: '/reports/provider-settlement-summary'
  },
  {
    id: 'rejections-report',
    title: 'تقرير المرفوضات التفصيلي',
    titleEn: 'Detailed Rejections Report',
    description: 'حصر شامل لجميع الخدمات المرفوضة مع أسباب الرفض والمبالغ المستقطعة',
    icon: TrendingUp,
    color: '#d32f2f',
    path: '/reports/rejections'
  }
];

export default function ReportsPage() {
  const navigate = useNavigate();

  const handleReportClick = (path) => {
    if (path) {
      navigate(path);
    }
  };

  return (
    <>
      <ModernPageHeader
        title="مركز التقارير الموحد"
        subtitle="التقارير التشغيلية والمالية الأساسية للمنظومة"
        icon={Assessment}
      />

      {/* التقارير المتاحة */}
      <Box sx={{ mt: '2.0rem' }}>
        <Grid container spacing={3}>
          {availableReports.map((report) => {
            const IconComponent = report.icon;
            return (
              <Grid key={report.id} size={{ xs: 12, sm: 6, md: 4 }}>
                <Card
                  elevation={3}
                  sx={{
                    height: '100%',
                    borderRadius: '0.1875rem',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    '&:hover': {
                      boxShadow: 10,
                      transform: 'translateY(-8px)',
                      borderColor: report.color
                    },
                    border: '1px solid rgba(0,0,0,0.05)'
                  }}
                >
                  <CardActionArea onClick={() => handleReportClick(report.path)} sx={{ height: '100%', p: 1 }}>
                    <CardContent>
                      <Stack spacing={2.5}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '4.0rem',
                            height: '4.0rem',
                            borderRadius: '1.0rem',
                            bgcolor: `${report.color}15`,
                            boxShadow: `0 8px 16px -4px ${report.color}33`
                          }}
                        >
                          <IconComponent sx={{ color: report.color, fontSize: '2.25rem' }} />
                        </Box>

                        <Box>
                          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5, color: 'text.primary' }}>
                            {report.title}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, letterSpacing: 0.5 }}>
                            {report.titleEn}
                          </Typography>
                        </Box>

                        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
                          {report.description}
                        </Typography>

                        <Stack direction="row" spacing={1} alignItems="center">
                          <Box sx={{ flexGrow: 1 }} />
                          <Typography variant="button" sx={{ color: report.color, fontWeight: 700, fontSize: '0.75rem' }}>
                            دخول التقرير
                          </Typography>
                          <ArrowForward sx={{ color: report.color, fontSize: '1.125rem' }} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      </Box>

      {/* ملاحظة تذكيرية */}
      <Box sx={{ mt: '4.0rem', textAlign: 'center', opacity: 0.6 }}>
        <Typography variant="caption" color="text.secondary">
          جميع التقارير تعتمد نظام الـ Batches كنظام أساسي للتسوية والتدقيق المالي.
        </Typography>
      </Box>
    </>
  );
}



