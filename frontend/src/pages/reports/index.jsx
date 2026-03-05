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

// التقارير المتاحة حالياً
const availableReports = [
  {
    id: 'claims-report',
    title: 'تقرير المطالبات',
    titleEn: 'Claims Report',
    description: 'تقرير تشغيلي للمطالبات الطبية مع إمكانية الفلترة والتصدير',
    icon: Receipt,
    color: '#1976d2',
    path: '/reports/claims',
    available: true
  },
  {
    id: 'visits-report',
    title: 'تقرير الزيارات',
    titleEn: 'Visits Report',
    description: 'تقرير تشغيلي للزيارات الطبية مع KPIs والتحليلات',
    icon: LocalHospital,
    color: '#2e7d32',
    path: '/reports/visits',
    available: true
  },
  {
    id: 'benefit-policy-report',
    title: 'تقرير وثائق المنافع',
    titleEn: 'Benefit Policy Report',
    description: 'تحليل وثائق المنافع والتغطيات التأمينية',
    icon: Policy,
    color: '#ed6c02',
    path: '/reports/benefit-policy',
    available: true
  },
  {
    id: 'employer-dashboard',
    title: 'لوحة الشريك',
    titleEn: 'Employer Dashboard',
    description: 'تحليلات شاملة لجهات العمل والمستفيدين',
    icon: Business,
    color: '#9c27b0',
    path: '/reports/employer-dashboard',
    available: true
  },
  {
    id: 'provider-settlement',
    title: 'تقارير تسوية مقدمي الخدمة',
    titleEn: 'Provider Settlement Reports',
    description: 'تقارير التسويات المالية مع مقدمي الخدمة الصحية',
    icon: LocalHospital,
    color: '#00796b',
    path: '/reports/provider-settlement',
    available: true
  }
];

// التقارير قيد التطوير
const upcomingReports = [
  {
    id: 'member-statements',
    title: 'كشوف حساب الأعضاء',
    titleEn: 'Member Statements',
    description: 'كشوف حساب تفصيلية للمستفيدين',
    icon: People,
    color: '#ed6c02',
    path: '/reports/coming-soon/member-statements',
    status: 'coming-soon'
  },
  {
    id: 'utilization-reports',
    title: 'تقارير الاستخدام',
    titleEn: 'Utilization Reports',
    description: 'تحليل استخدام الخدمات الطبية والتغطية',
    icon: BarChart,
    color: '#9c27b0',
    path: '/reports/coming-soon/utilization-reports',
    status: 'coming-soon'
  },
  {
    id: 'financial-analytics',
    title: 'التقارير المالية المتقدمة',
    titleEn: 'Advanced Financial Reports',
    description: 'تقارير مالية متقدمة مع التحليلات',
    icon: TrendingUp,
    color: '#0288d1',
    path: '/reports/coming-soon/financial-analytics',
    status: 'coming-soon'
  },
  {
    id: 'analytics',
    title: 'التحليلات والإحصائيات',
    titleEn: 'Analytics & Statistics',
    description: 'رسوم بيانية وإحصائيات تفاعلية',
    icon: PieChart,
    color: '#d32f2f',
    path: '/reports/coming-soon/analytics',
    status: 'coming-soon'
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
        title="التقارير"
        subtitle="مركز التقارير والتحليلات"
        icon={Assessment}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'التقارير' }]}
      />

      {/* التقارير المتاحة */}
      <MainCard title="التقارير التشغيلية المتاحة" sx={{ mb: 3 }}>
        <Grid container spacing={3}>
          {availableReports.map((report) => {
            const IconComponent = report.icon;
            return (
              <Grid item xs={12} sm={6} md={3} key={report.id}>
                <Card
                  sx={{
                    height: '100%',
                    transition: 'all 0.3s',
                    '&:hover': {
                      boxShadow: 6,
                      transform: 'translateY(-4px)'
                    }
                  }}
                >
                  <CardActionArea onClick={() => handleReportClick(report.path)} sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={2}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: 56,
                            height: 56,
                            borderRadius: 2,
                            bgcolor: `${report.color}15`
                          }}
                        >
                          <IconComponent sx={{ color: report.color, fontSize: 32 }} />
                        </Box>

                        <Box>
                          <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
                            {report.title}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" display="block">
                            {report.titleEn}
                          </Typography>
                        </Box>

                        <Typography variant="body2" color="text.secondary">
                          {report.description}
                        </Typography>

                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Chip label="متاح" size="small" color="success" />
                          <ArrowForward sx={{ color: report.color, fontSize: 20 }} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      </MainCard>

      {/* التقارير قيد التطوير */}
      <MainCard
        title="تقارير قيد التطوير"
        secondary={<Chip label="قريباً" color="warning" size="small" icon={<Construction sx={{ fontSize: 16 }} />} />}
      >
        <Grid container spacing={3}>
          {upcomingReports.map((report) => {
            const IconComponent = report.icon;
            return (
              <Grid item xs={12} sm={6} md={4} key={report.id}>
                <Card
                  sx={{
                    height: '100%',
                    transition: 'all 0.3s',
                    '&:hover': {
                      boxShadow: 4,
                      transform: 'translateY(-2px)'
                    }
                  }}
                >
                  <CardActionArea onClick={() => handleReportClick(report.path)} sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={2}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: 48,
                            height: 48,
                            borderRadius: 2,
                            bgcolor: `${report.color}15`
                          }}
                        >
                          <IconComponent sx={{ color: report.color, fontSize: 28 }} />
                        </Box>

                        <Box>
                          <Typography variant="h6" sx={{ fontWeight: 600 }}>
                            {report.title}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {report.titleEn}
                          </Typography>
                        </Box>

                        <Typography variant="body2" color="text.secondary">
                          {report.description}
                        </Typography>

                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Chip
                            label="قيد التطوير"
                            size="small"
                            color="warning"
                            icon={<Construction sx={{ fontSize: 14 }} />}
                            sx={{ fontSize: '0.7rem' }}
                          />
                          <ArrowForward sx={{ color: report.color, fontSize: 18, opacity: 0.7 }} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      </MainCard>
    </>
  );
}
