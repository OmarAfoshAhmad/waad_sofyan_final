import { Card, CardContent, CardHeader, List, ListItem, ListItemText, Chip, Typography, Box, Alert } from '@mui/material';
import { AccessTime, Warning } from '@mui/icons-material';

/**
 * Expiring soon alerts
 */
const ExpiringAlerts = ({ data, loading, withinDays = 7 }) => {
  if (loading) {
    return (
      <Card>
        <CardHeader title="تنبيهات الانتهاء القريب" avatar={<AccessTime color="warning" />} />
        <CardContent>
          <Typography variant="body2" color="text.secondary" align="center">
            جاري التحميل...
          </Typography>
        </CardContent>
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <CardHeader title="تنبيهات الانتهاء القريب" avatar={<AccessTime color="success" />} subheader={`خلال ${withinDays} أيام`} />
        <CardContent>
          <Alert severity="success" icon={false}>
            لا توجد موافقات تنتهي قريباً
          </Alert>
        </CardContent>
      </Card>
    );
  }

  const getDaysRemaining = (validUntil) => {
    const days = Math.ceil((new Date(validUntil) - new Date()) / (1000 * 60 * 60 * 24));
    return days;
  };

  const getDaysColor = (days) => {
    if (days <= 2) return 'error';
    if (days <= 5) return 'warning';
    return 'info';
  };

  return (
    <Card>
      <CardHeader
        title="تنبيهات الانتهاء القريب"
        avatar={<Warning color="warning" />}
        subheader={`${data.length} موافقة تنتهي خلال ${withinDays} أيام`}
      />
      <CardContent sx={{ p: 0 }}>
        <List>
          {data.map((item, index) => {
            const daysRemaining = getDaysRemaining(item.validUntil);
            const color = getDaysColor(daysRemaining);

            return (
              <ListItem
                key={item.id}
                divider={index < data.length - 1}
                sx={{
                  '&:hover': { bgcolor: 'action.hover' }
                }}
              >
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Typography variant="body2" fontWeight="medium">
                        {item.referenceNumber}
                      </Typography>
                      <Chip label={`${daysRemaining} ${daysRemaining === 1 ? 'يوم' : 'أيام'}`} color={color} size="small" />
                    </Box>
                  }
                  secondary={
                    <Box sx={{ mt: 0.5 }}>
                      <Typography variant="caption" display="block" color="text.secondary">
                        {item.memberName}
                      </Typography>
                      <Typography variant="caption" display="block" color="text.secondary">
                        ينتهي: {new Date(item.validUntil).toLocaleDateString('en-GB')}
                      </Typography>
                    </Box>
                  }
                />
              </ListItem>
            );
          })}
        </List>
      </CardContent>
    </Card>
  );
};

export default ExpiringAlerts;
