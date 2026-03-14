/**
 * Phase 5.5: Landing Page Test Component
 * =========================================
 *
 * Manual test page to verify role-based landing pages
 * Access at: /test/landing-pages (development only)
 */

import { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Stack,
  Chip,
  Divider,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// Icons
import DashboardIcon from '@mui/icons-material/Dashboard';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import AssignmentIcon from '@mui/icons-material/Assignment';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import BusinessIcon from '@mui/icons-material/Business';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';

export default function LandingPageTest() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [testResults, setTestResults] = useState([]);

  const roleConfig = [
    {
      role: 'SUPER_ADMIN',
      route: '/dashboard',
      icon: <DashboardIcon />,
      label: 'لوحة التحكم',
      color: 'primary'
    },
    {
      role: 'ACCOUNTANT',
      route: '/settlement/batches',
      icon: <AccountBalanceIcon />,
      label: 'دفعات التسوية',
      color: 'success'
    },
    {
      role: 'MEDICAL_REVIEWER',
      route: '/approvals/dashboard',
      icon: <AssignmentIcon />,
      label: 'لوحة الموافقات',
      color: 'warning'
    },
    {
      role: 'PROVIDER',
      route: '/provider/visits',
      icon: <LocalHospitalIcon />,
      label: 'الزيارات الطبية',
      color: 'info'
    },
    {
      role: 'EMPLOYER',
      route: '/',
      icon: <BusinessIcon />,
      label: 'الصفحة الرئيسية',
      color: 'secondary'
    }
  ];

  const testRoleRoute = (role, expectedRoute) => {
    const actualRoute = getDefaultRouteForRole(role);
    const passed = actualRoute === expectedRoute;

    setTestResults((prev) => [
      ...prev,
      {
        role,
        expectedRoute,
        actualRoute,
        passed,
        timestamp: new Date().toLocaleTimeString('ar-SA')
      }
    ]);

    return passed;
  };

  const runAllTests = () => {
    setTestResults([]);
    roleConfig.forEach(({ role, route }) => {
      testRoleRoute(role, route);
    });
  };

  const clearTests = () => {
    setTestResults([]);
  };

  const navigateToRoute = (route) => {
    navigate(route);
  };

  const currentRoleLanding = user ? getDefaultRouteForRole(user.role) : null;

  return (
    <Box sx={{ p: '1.5rem' }}>
      {/* Header */}
      <Card sx={{ mb: '1.5rem' }}>
        <CardContent>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
            <Box>
              <Typography variant="h4" gutterBottom>
                🧪 اختبار صفحات الهبوط حسب الدور
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Phase 5.5: Critical Stabilization - Role-Based Landing Pages
              </Typography>
            </Box>
            {user && (
              <Stack spacing={1} alignItems="flex-end">
                <Chip label={`دورك الحالي: ${user.role}`} color="primary" size="small" />
                <Chip label={`صفحة الهبوط: ${currentRoleLanding}`} color="success" size="small" variant="outlined" />
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>

      {/* Role Configuration Table */}
      <Card sx={{ mb: '1.5rem' }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            📋 تكوين صفحات الهبوط
          </Typography>
          <Divider sx={{ my: '1.0rem' }} />

          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>الدور</TableCell>
                  <TableCell>صفحة الهبوط</TableCell>
                  <TableCell>الوصف</TableCell>
                  <TableCell align="center">الإجراء</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {roleConfig.map(({ role, route, icon, label, color }) => (
                  <TableRow key={role}>
                    <TableCell>
                      <Chip icon={icon} label={role} color={color} size="small" />
                    </TableCell>
                    <TableCell>
                      <code>{route}</code>
                    </TableCell>
                    <TableCell>{label}</TableCell>
                    <TableCell align="center">
                      <Button variant="outlined" size="small" endIcon={<NavigateNextIcon />} onClick={() => navigateToRoute(route)}>
                        انتقل
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Test Controls */}
      <Card sx={{ mb: '1.5rem' }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            🔬 إجراء الاختبارات
          </Typography>
          <Divider sx={{ my: '1.0rem' }} />

          <Stack direction="row" spacing={2}>
            <Button variant="contained" color="primary" onClick={runAllTests} startIcon={<CheckCircleIcon />}>
              تشغيل جميع الاختبارات
            </Button>
            <Button variant="outlined" color="secondary" onClick={clearTests}>
              مسح النتائج
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {/* Test Results */}
      {testResults.length > 0 && (
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              📊 نتائج الاختبارات
            </Typography>
            <Divider sx={{ my: '1.0rem' }} />

            {testResults.every((r) => r.passed) && (
              <Alert severity="success" sx={{ mb: '1.0rem' }}>
                ✅ جميع الاختبارات نجحت! ({testResults.length}/{testResults.length})
              </Alert>
            )}

            {testResults.some((r) => !r.passed) && (
              <Alert severity="error" sx={{ mb: '1.0rem' }}>
                ❌ بعض الاختبارات فشلت! ({testResults.filter((r) => r.passed).length}/{testResults.length} نجحت)
              </Alert>
            )}

            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>الوقت</TableCell>
                    <TableCell>الدور</TableCell>
                    <TableCell>المتوقع</TableCell>
                    <TableCell>الفعلي</TableCell>
                    <TableCell align="center">النتيجة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {testResults.map((result, index) => (
                    <TableRow key={index}>
                      <TableCell>{result.timestamp}</TableCell>
                      <TableCell>{result.role}</TableCell>
                      <TableCell>
                        <code>{result.expectedRoute}</code>
                      </TableCell>
                      <TableCell>
                        <code>{result.actualRoute}</code>
                      </TableCell>
                      <TableCell align="center">
                        {result.passed ? (
                          <Chip label="✅ نجح" color="success" size="small" />
                        ) : (
                          <Chip label="❌ فشل" color="error" size="small" />
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}
