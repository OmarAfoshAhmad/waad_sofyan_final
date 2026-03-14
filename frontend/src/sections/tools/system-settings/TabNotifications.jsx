import { useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';

// project imports
import MainCard from 'components/MainCard';
import { openSnackbar } from 'api/snackbar';

// icons
import { SaveOutlined, BellOutlined, MailOutlined, MessageOutlined, NotificationOutlined } from '@ant-design/icons';

// ==============================|| SYSTEM SETTINGS - NOTIFICATIONS ||============================== //

export default function TabNotifications() {
  const initialValues = {
    // Notification Channels
    enableEmail: true,
    enableSMS: false,
    enablePush: true,

    // Claims Notifications
    claimSubmitted: true,
    claimApproved: true,
    claimRejected: true,
    claimPendingReview: true,

    // PreAuth Notifications
    preauthRequest: true,
    preauthApproved: true,
    preauthRejected: true,
    preauthExpiring: true,

    // Members Notifications
    memberRegistered: true,
    memberUpdated: false,
    memberSuspended: true,

    // Visits Notifications
    visitRecorded: true,
    visitCompleted: false,

    // System Alerts
    systemMaintenance: true,
    securityAlerts: true,
    backupCompleted: false
  };

  const [formData, setFormData] = useState(initialValues);
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (event) => {
    setFormData({ ...formData, [field]: event.target.checked });
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      localStorage.setItem('system_notification_settings', JSON.stringify(formData));

      openSnackbar({
        open: true,
        message: 'Notification settings saved successfully',
        variant: 'success'
      });
    } catch {
      openSnackbar({
        open: true,
        message: 'Failed to save notification settings',
        variant: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Notification Settings" avatar={<BellOutlined style={{ fontSize: '1.5rem' }} />}>
          <Grid container spacing={3}>
            {/* Notification Channels */}
            <Grid size={12}>
              <Divider textAlign="left">Notification Channels</Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <FormControlLabel
                  control={<Switch checked={formData.enableEmail} onChange={handleChange('enableEmail')} color="primary" />}
                  label={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <MailOutlined />
                      <span>Email Notifications</span>
                    </Stack>
                  }
                />
                {formData.enableEmail && <Chip label="Active" color="success" size="small" />}
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <FormControlLabel
                  control={<Switch checked={formData.enableSMS} onChange={handleChange('enableSMS')} color="primary" />}
                  label={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <MessageOutlined />
                      <span>SMS Notifications</span>
                    </Stack>
                  }
                />
                {formData.enableSMS && <Chip label="Active" color="success" size="small" />}
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <FormControlLabel
                  control={<Switch checked={formData.enablePush} onChange={handleChange('enablePush')} color="primary" />}
                  label={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <NotificationOutlined />
                      <span>Push Notifications</span>
                    </Stack>
                  }
                />
                {formData.enablePush && <Chip label="Active" color="success" size="small" />}
              </Stack>
            </Grid>

            {/* Claims Notifications */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Claims Notifications
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.claimSubmitted} onChange={handleChange('claimSubmitted')} />}
                label="New Claim Submitted"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.claimApproved} onChange={handleChange('claimApproved')} color="success" />}
                label="Claim Approved"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.claimRejected} onChange={handleChange('claimRejected')} color="error" />}
                label="Claim Rejected"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.claimPendingReview} onChange={handleChange('claimPendingReview')} color="warning" />}
                label="Claim Pending Review"
              />
            </Grid>

            {/* PreAuth Notifications */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Pre-authorization Notifications
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.preauthRequest} onChange={handleChange('preauthRequest')} />}
                label="New Pre-authorization Request"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.preauthApproved} onChange={handleChange('preauthApproved')} color="success" />}
                label="Pre-authorization Approved"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.preauthRejected} onChange={handleChange('preauthRejected')} color="error" />}
                label="Pre-authorization Rejected"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.preauthExpiring} onChange={handleChange('preauthExpiring')} color="warning" />}
                label="Pre-authorization Expiring Soon"
              />
            </Grid>

            {/* Members Notifications */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Members Notifications
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.memberRegistered} onChange={handleChange('memberRegistered')} color="primary" />}
                label="New Member Registered"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.memberUpdated} onChange={handleChange('memberUpdated')} />}
                label="Member Profile Updated"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.memberSuspended} onChange={handleChange('memberSuspended')} color="error" />}
                label="Member Suspended"
              />
            </Grid>

            {/* Visits Notifications */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Visits Notifications
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.visitRecorded} onChange={handleChange('visitRecorded')} />}
                label="New Visit Recorded"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.visitCompleted} onChange={handleChange('visitCompleted')} color="success" />}
                label="Visit Completed"
              />
            </Grid>

            {/* System Alerts */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                System Alerts
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.systemMaintenance} onChange={handleChange('systemMaintenance')} color="warning" />}
                label="System Maintenance Alerts"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.securityAlerts} onChange={handleChange('securityAlerts')} color="error" />}
                label="Security Alerts"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={formData.backupCompleted} onChange={handleChange('backupCompleted')} color="success" />}
                label="Backup Completed"
              />
            </Grid>

            {/* Save Button */}
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                <Button variant="contained" startIcon={<SaveOutlined />} onClick={handleSave} disabled={loading}>
                  {loading ? 'Saving...' : 'Save Changes'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
