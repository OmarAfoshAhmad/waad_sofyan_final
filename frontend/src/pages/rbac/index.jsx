/**
 * RBAC Dashboard - Phase D3 Step 2
 * Central hub for RBAC management with navigation cards
 *
 * Features:
 * 1. Quick stats (Users, Roles, Permissions)
 * 2. Navigation cards to Users and Roles management
 * 3. Recent activity (optional)
 */

import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

// MUI Components
import { Box, Grid, Card, CardContent, CardActionArea, Typography, Avatar, Stack, Skeleton } from '@mui/material';

// MUI Icons
import SecurityIcon from '@mui/icons-material/Security';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import KeyIcon from '@mui/icons-material/Key';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Services
import usersService from 'services/rbac/users.service';
import { SystemRole } from 'constants/rbac';

// ============================================================================
// STAT CARD COMPONENT
// ============================================================================

const StatCard = ({ title, value, icon: Icon, color, loading }) => (
  <Card elevation={0} sx={{ bgcolor: `${color}.lighter`, height: '100%' }}>
    <CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {title}
          </Typography>
          {loading ? (
            <Skeleton width={60} height={40} />
          ) : (
            <Typography variant="h3" color={`${color}.dark`}>
              {value}
            </Typography>
          )}
        </Box>
        <Avatar sx={{ bgcolor: `${color}.main`, width: '3.0rem', height: '3.0rem' }}>
          <Icon />
        </Avatar>
      </Stack>
    </CardContent>
  </Card>
);

// ============================================================================
// NAVIGATION CARD COMPONENT
// ============================================================================

const NavCard = ({ title, description, icon: Icon, color, onClick }) => (
  <Card
    elevation={0}
    sx={{
      height: '100%',
      border: '1px solid',
      borderColor: 'divider',
      transition: 'all 0.2s ease',
      '&:hover': {
        borderColor: `${color}.main`,
        boxShadow: 3
      }
    }}
  >
    <CardActionArea onClick={onClick} sx={{ height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          <Avatar sx={{ bgcolor: `${color}.lighter`, color: `${color}.main`, width: '3.5rem', height: '3.5rem' }}>
            <Icon sx={{ fontSize: '1.75rem' }} />
          </Avatar>
          <Box>
            <Typography variant="h5" gutterBottom>
              {title}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {description}
            </Typography>
          </Box>
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: `${color}.main` }}>
            <Typography variant="button">عرض التفاصيل</Typography>
            <ArrowForwardIcon fontSize="small" />
          </Stack>
        </Stack>
      </CardContent>
    </CardActionArea>
  </Card>
);

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const RBACDashboard = () => {
  const navigate = useNavigate();

  // State for stats
  const [stats, setStats] = useState({ users: 0, roles: 0, permissions: 0 });
  const [loading, setLoading] = useState(true);

  // Load stats
  const loadStats = useCallback(async () => {
    try {
      setLoading(true);
      const [usersRes] = await Promise.all([
        usersService.getAllUsers()
      ]);

      const users = usersRes?.data?.data || usersRes?.data || [];
      const roles = Object.values(SystemRole);

      setStats({
        users: Array.isArray(users) ? users.length : 0,
        roles: roles.length,
        permissions: 0
      });
    } catch (err) {
      console.error('[RBACDashboard] Stats error:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box>
      {/* ====== PAGE HEADER ====== */}
      <ModernPageHeader
        title="إدارة الصلاحيات"
        subtitle="إدارة المستخدمين والأدوار والصلاحيات في النظام"
        icon={SecurityIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'الصلاحيات' }]}
      />

      {/* ====== STATS ROW ====== */}
      <Grid container spacing={3} sx={{ mb: '2.0rem' }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard title="المستخدمين" value={stats.users} icon={PeopleAltIcon} color="primary" loading={loading} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard title="الأدوار" value={stats.roles} icon={AdminPanelSettingsIcon} color="warning" loading={loading} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard title="الصلاحيات" value={stats.permissions} icon={KeyIcon} color="success" loading={loading} />
        </Grid>
      </Grid>

      {/* ====== NAVIGATION CARDS ====== */}
      <Typography variant="h5" gutterBottom sx={{ mb: '1.0rem' }}>
        الإدارة
      </Typography>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <NavCard
            title="إدارة المستخدمين"
            description="عرض وإدارة المستخدمين وتعيين الأدوار لهم. يمكنك تفعيل أو تعطيل أدوار كل مستخدم."
            icon={PeopleAltIcon}
            color="primary"
            onClick={() => navigate('/admin/users')}
          />
        </Grid>
        {/* Roles management card removed - roles are now managed inline */}
      </Grid>

      {/* ====== INFO CARD ====== */}
      <MainCard sx={{ mt: '2.0rem' }}>
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <Avatar sx={{ bgcolor: 'info.lighter', color: 'info.main' }}>
            <SecurityIcon />
          </Avatar>
          <Box>
            <Typography variant="h6" gutterBottom>
              كيفية عمل نظام الصلاحيات
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              يعتمد النظام على نموذج RBAC (Role-Based Access Control) حيث:
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: '1.0rem' }}>
              <Typography component="li" variant="body2" color="text.secondary">
                <strong>المستخدمين:</strong> يتم تعيين أدوار لهم
              </Typography>
              <Typography component="li" variant="body2" color="text.secondary">
                <strong>الأدوار:</strong> تحتوي على مجموعة من الصلاحيات
              </Typography>
              <Typography component="li" variant="body2" color="text.secondary">
                <strong>الصلاحيات الفعلية:</strong> مجموع صلاحيات جميع الأدوار المعيّنة للمستخدم
              </Typography>
              <Typography component="li" variant="body2" color="text.secondary">
                <strong>SUPER_ADMIN:</strong> يمتلك جميع الصلاحيات تلقائياً ولا يمكن تعديله
              </Typography>
            </Box>
          </Box>
        </Stack>
      </MainCard>
    </Box>
  );
};

export default RBACDashboard;

