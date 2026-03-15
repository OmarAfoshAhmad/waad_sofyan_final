import { useParams, useNavigate } from 'react-router-dom';
import { Box, Typography, Button, Stack, Chip, Paper } from '@mui/material';
import {
  Construction as ConstructionIcon,
  ArrowBack as ArrowBackIcon,
  LocalHospital,
  People,
  BarChart,
  TrendingUp,
  PieChart,
  Assessment
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';

/**
 * Coming Soon Report - صفحة تقرير قيد التطوير
 *
 * صفحة عنصر نائب لعرض التقارير التي لا تزال قيد التطوير
 * تعرض معلومات واضحة للمستخدم حول الغرض من التقرير وحالته
 *
 * لا تقوم بأي استدعاءات API - واجهة أمامية فقط
 *
 * Permissions: ACCOUNTANT, MEDICAL_REVIEWER, SUPER_ADMIN (NOT PROVIDER)
 */

// Report configurations based on report ID
const REPORT_CONFIG = {
  'provider-settlement': {
    title: 'تقارير تسوية مقدمي الخدمة',
    titleEn: 'Provider Settlement Reports',
    description: 'تقارير التسويات المالية مع مقدمي الخدمة الصحية. تتضمن ملخص المبالغ المستحقة، الفواتير المعلقة، وتفاصيل الدفعات المكتملة.',
    icon: LocalHospital,
    color: '#2e7d32',
    features: [
      'ملخص التسويات حسب مقدم الخدمة',
      'تقرير الفواتير المعلقة',
      'سجل المدفوعات التاريخي',
      'تصدير إلى Excel' // PDF disabled - Excel is the official format
    ]
  },
  'member-statements': {
    title: 'كشوف حساب الأعضاء',
    titleEn: 'Member Statements',
    description: 'كشوف حساب تفصيلية للمؤمن عليهم تتضمن جميع المطالبات والزيارات والمبالغ المستهلكة من التغطية التأمينية.',
    icon: People,
    color: '#ed6c02',
    features: ['كشف حساب فردي لكل منتفع', 'ملخص الاستهلاك السنوي', 'تفاصيل المطالبات والزيارات', 'المتبقي من الحد الأقصى']
  },
  'utilization-reports': {
    title: 'تقارير الاستخدام',
    titleEn: 'Utilization Reports',
    description: 'تحليل استخدام الخدمات الطبية والتغطية التأمينية. يساعد في فهم أنماط الاستهلاك وتحسين تصميم المنافع.',
    icon: BarChart,
    color: '#9c27b0',
    features: ['تحليل استخدام الخدمات حسب النوع', 'مقارنة الاستهلاك بين الشركاء', 'اتجاهات الاستخدام الشهرية', 'أعلى الخدمات استخداماً']
  },
  'financial-analytics': {
    title: 'التقارير المالية المتقدمة',
    titleEn: 'Advanced Financial Reports',
    description: 'تقارير مالية متقدمة تشمل التحليلات والتوقعات والمقارنات الزمنية لأداء العمليات التأمينية.',
    icon: TrendingUp,
    color: '#0288d1',
    features: ['تحليل الربحية حسب الشريك', 'توقعات المطالبات المستقبلية', 'مقارنة الأداء السنوي', 'مؤشرات الأداء الرئيسية (KPIs)']
  },
  analytics: {
    title: 'التحليلات والإحصائيات',
    titleEn: 'Analytics & Statistics',
    description: 'رسوم بيانية وإحصائيات تفاعلية لعرض بيانات النظام بشكل مرئي يسهل فهمه وتحليله.',
    icon: PieChart,
    color: '#d32f2f',
    features: ['لوحات معلومات تفاعلية', 'رسوم بيانية متنوعة', 'فلترة وتخصيص العرض', 'مقارنات متعددة الأبعاد']
  }
};

// Default config for unknown reports
const DEFAULT_CONFIG = {
  title: 'تقرير قيد التطوير',
  titleEn: 'Report Under Development',
  description: 'هذا التقرير قيد التطوير حالياً وسيكون متاحاً في إصدار قادم.',
  icon: Assessment,
  color: '#757575',
  features: []
};

const ComingSoonReport = () => {
  const { reportId } = useParams();
  const navigate = useNavigate();

  // Get report configuration or use default
  const config = REPORT_CONFIG[reportId] || DEFAULT_CONFIG;
  const IconComponent = config.icon;

  const handleGoBack = () => {
    navigate('/reports');
  };

  return (
    <>
      <Box>
        <ModernPageHeader
          title={config.title}
          subtitle={config.titleEn}
          icon={<IconComponent sx={{ color: config.color }} />}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'التقارير', path: '/reports' }, { label: config.title }]}
        />

        <MainCard>
          <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" py={8} textAlign="center">
            {/* Construction Icon with Badge */}
            <Box position="relative" mb={4}>
              <Box
                sx={{
                  width: '7.5rem',
                  height: '7.5rem',
                  borderRadius: '50%',
                  bgcolor: `${config.color}15`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <IconComponent sx={{ fontSize: '3.75rem', color: config.color }} />
              </Box>
              <Chip
                icon={<ConstructionIcon />}
                label="قيد التطوير"
                color="warning"
                size="small"
                sx={{
                  position: 'absolute',
                  bottom: -10,
                  left: '50%',
                  transform: 'translateX(-50%)',
                  fontWeight: 'bold'
                }}
              />
            </Box>

            {/* Title */}
            <Typography variant="h4" fontWeight="bold" color="text.primary" gutterBottom>
              {config.title}
            </Typography>

            <Typography variant="subtitle1" color="text.secondary" sx={{ mb: '1.5rem' }}>
              {config.titleEn}
            </Typography>

            {/* Description */}
            <Typography variant="body1" color="text.secondary" sx={{ maxWidth: '37.5rem', mb: '2.0rem', lineHeight: 1.8 }}>
              {config.description}
            </Typography>

            {/* Features List */}
            {config.features.length > 0 && (
              <Paper
                variant="outlined"
                sx={{
                  p: '1.5rem',
                  maxWidth: '31.25rem',
                  width: '100%',
                  mb: '2.0rem',
                  bgcolor: 'grey.50'
                }}
              >
                <Typography variant="subtitle2" fontWeight="bold" gutterBottom color="primary">
                  المميزات المخطط لها:
                </Typography>
                <Stack spacing={1} alignItems="flex-start">
                  {config.features.map((feature, idx) => (
                    <Stack key={idx} direction="row" spacing={1} alignItems="center">
                      <Box
                        sx={{
                          width: '0.375rem',
                          height: '0.375rem',
                          borderRadius: '50%',
                          bgcolor: config.color
                        }}
                      />
                      <Typography variant="body2" color="text.secondary">
                        {feature}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              </Paper>
            )}

            {/* Status Message */}
            <Paper
              sx={{
                p: '1.0rem',
                bgcolor: 'warning.lighter',
                border: '1px solid',
                borderColor: 'warning.light',
                borderRadius: '0.25rem',
                maxWidth: '31.25rem',
                mb: '2.0rem'
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
                <ConstructionIcon color="warning" />
                <Typography variant="body2" color="warning.dark">
                  هذا التقرير قيد التطوير النشط وسيكون متاحاً في إصدار قادم من النظام.
                </Typography>
              </Stack>
            </Paper>

            {/* Back Button */}
            <Button variant="contained" startIcon={<ArrowBackIcon />} onClick={handleGoBack} size="large">
              العودة لصفحة التقارير
            </Button>
          </Box>
        </MainCard>
      </Box>
    </>
  );
};

export default ComingSoonReport;
