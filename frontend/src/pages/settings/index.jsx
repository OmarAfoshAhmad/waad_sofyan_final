/**
 * Settings Page - Enterprise System Configuration
 *
 * Provides navigation to system configuration pages.
 * Theme settings are now fixed for professional consistent UX.
 * Last Updated: 2025-01-24
 */

import { useNavigate } from 'react-router-dom';
import { Box, Grid, Card, CardContent, Typography, CardActionArea } from '@mui/material';
import { TeamOutlined, SettingOutlined, MailOutlined } from '@ant-design/icons';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import useAuth from 'hooks/useAuth';

/**
 * Settings Card Component
 */
const SettingsCard = ({ icon: Icon, title, description, onClick, color = '#1890ff' }) => {
  return (
    <Card sx={{ height: '100%', transition: 'all 0.3s', '&:hover': { transform: 'translateY(-4px)', boxShadow: 6 } }}>
      <CardActionArea onClick={onClick} sx={{ height: '100%', p: 2 }}>
        <CardContent>
          <Box display="flex" alignItems="center" mb={2}>
            <Box
              sx={{
                width: 56,
                height: 56,
                borderRadius: 2,
                bgcolor: `${color}15`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mr: 2
              }}
            >
              <Icon style={{ fontSize: 28, color }} />
            </Box>
            <Typography variant="h4" fontWeight={600}>
              {title}
            </Typography>
          </Box>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

/**
 * Settings Page - Main Dashboard
 */
const SettingsPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  // Check user permissions
  const hasRole = (roles) => {
    if (!user?.roles || !Array.isArray(user.roles)) return false;
    return roles.some((role) => user.roles.includes(role));
  };

  const settingsSections = [
    {
      id: 'system',
      icon: SettingOutlined,
      title: 'إعدادات النظام الشاملة',
      description: 'تشمل معلومات المؤسسة وإعدادات النظام وبوابة مقدم الخدمة',
      color: '#fa8c16',
      route: '/settings/system',
      roles: ['SUPER_ADMIN', 'ACCOUNTANT']
    },
    // REMOVED: Theme customization disabled - Fixed professional UI/UX
    // The system now uses fixed theme settings for consistent user experience:
    // - Layout: Horizontal (أفقي)
    // - Direction: RTL (يمين لليسار)
    // - Color: Theme 8 (سمة 8)
    // - Width: Fluid (مرن)
    {
      id: 'users',
      icon: TeamOutlined,
      title: 'إدارة المستخدمين',
      description: 'إدارة المستخدمين والأدوار في النظام',
      color: '#52c41a',
      route: '/admin/users',
      roles: ['SUPER_ADMIN', 'ADMIN']
    }
  ];

  // Filter sections based on user roles
  const availableSections = settingsSections.filter((section) => hasRole(section.roles));

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader title="إعدادات النظام" subtitle="التحكم في إعدادات النظام والصلاحيات" icon={SettingOutlined} />

      {/* Settings Grid */}
      <Grid container spacing={3} sx={{ mt: 2 }}>
        {availableSections.map((section) => (
          <Grid key={section.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <SettingsCard
              icon={section.icon}
              title={section.title}
              description={section.description}
              color={section.color}
              onClick={() => (section.action ? section.action() : navigate(section.route))}
            />
          </Grid>
        ))}
      </Grid>

      {/* No Settings Available */}
      {availableSections.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <SettingOutlined style={{ fontSize: 64, color: '#d9d9d9', marginBottom: 16 }} />
          <Typography variant="h5" color="text.secondary">
            لا توجد إعدادات متاحة
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            ليس لديك صلاحيات للوصول إلى إعدادات النظام
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default SettingsPage;
