import { useNavigate } from 'react-router-dom';

// material-ui
import { Button, Stack, Typography, Grid } from '@mui/material';

// project imports
import ModernPageHeader from 'components/tba/ModernPageHeader';
import ModernEmptyState from 'components/tba/ModernEmptyState';

// assets
import LockOutlined from '@ant-design/icons/LockOutlined';

// ==============================|| 403 FORBIDDEN PAGE ||============================== //

export default function Forbidden403() {
  const navigate = useNavigate();

  const handleGoBack = () => {
    navigate('/dashboard');
  };

  return (
    <>
      <ModernPageHeader title="غير مسموح بالدخول" subtitle="ليس لديك الصلاحية للوصول إلى هذه الصفحة" />

      <Grid container spacing={3}>
        <Grid size={12}>
          <ModernEmptyState
            icon={LockOutlined}
            title="وصول محظور (403)"
            description="عذراً، ليس لديك الصلاحيات الكافية للوصول إلى هذه الصفحة. يرجى التواصل مع المسؤول إذا كنت تعتقد أنه يجب أن يكون لديك وصول."
            iconColor="error"
          />
        </Grid>

        <Grid size={12}>
          <Stack direction="row" spacing={2} justifyContent="center" sx={{ mt: '1.0rem' }}>
            <Button variant="contained" color="primary" size="large" onClick={handleGoBack}>
              الرجوع للوحة التحكم
            </Button>
            <Button variant="outlined" color="secondary" size="large" onClick={() => navigate(-1)}>
              رجوع
            </Button>
          </Stack>
        </Grid>
      </Grid>
    </>
  );
}
