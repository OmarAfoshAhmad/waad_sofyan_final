import { Box, Chip, Divider, Grid, Paper, Stack, Tooltip, Typography } from '@mui/material';
import { Badge as BadgeIcon, ContactPhone as ContactPhoneIcon, QrCode as QrCodeIcon } from '@mui/icons-material';
import MemberAvatar from 'components/tba/MemberAvatar';
import { formatDate } from 'utils/formatters';
import { GENDERS } from 'services/api/unified-members.service';
import MemberStatusChip from './MemberStatusChip';

/**
 * Tab 0: principal/dependent personal, employment and contact info.
 * Pure presentational -- all state (photo dialog, status menu) is owned by
 * UnifiedMemberView so it stays shared across tabs.
 */
const MemberPersonalInfoTab = ({ member, isPrincipal, onOpenPhoto, onOpenStatusMenu }) => (
  <Grid container spacing={2}>
    <Grid size={{ xs: 12, md: 3 }} sx={{ display: 'flex' }}>
      <Paper
        variant="outlined"
        sx={{
          p: '0.75rem',
          flex: 1,
          bgcolor: 'grey.50',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center'
        }}
      >
        <Tooltip title="اضغط لتكبير الصورة">
          <span>
            <MemberAvatar member={member} size={110} onClick={onOpenPhoto} sx={{ mb: '0.75rem' }} />
          </span>
        </Tooltip>

        <Stack spacing={1.5} alignItems="center" width="100%">
          <Stack direction="row" spacing={1.5} justifyContent="center" width="100%">
            <Chip
              label={isPrincipal ? 'موظف' : 'تابع'}
              color={isPrincipal ? 'primary' : 'secondary'}
              size="small"
              sx={{ height: '1.5rem', fontSize: '0.75rem' }}
            />
            <MemberStatusChip status={member.status} blockedReason={member.blockedReason} onClick={(e) => onOpenStatusMenu(e, member.id)} />
          </Stack>

          <Divider flexItem sx={{ width: '100%', my: 0.5 }} />

          <Box
            sx={{
              width: '100%',
              textAlign: 'center',
              p: 1,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              bgcolor: 'background.paper'
            }}
          >
            <Typography variant="caption" color="text.secondary" display="block" fontWeight="600">
              رقم البطاقة
            </Typography>
            <Typography variant="subtitle2" fontFamily="monospace" fontWeight="bold" sx={{ mt: 0.5 }}>
              {member.cardNumber || '-'}
            </Typography>
          </Box>

          {isPrincipal && member.barcode && (
            <Box
              sx={{
                width: '100%',
                textAlign: 'center',
                p: 1,
                bgcolor: 'primary.lighter',
                border: '1px solid',
                borderColor: 'primary.light',
                borderRadius: 1
              }}
            >
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5} sx={{ mb: 0.5 }}>
                <QrCodeIcon color="primary" sx={{ fontSize: '1.125rem' }} />
                <Typography variant="caption" color="primary.main" fontWeight="600">
                  Barcode
                </Typography>
              </Stack>
              <Typography variant="subtitle2" color="primary.main" fontWeight="bold" fontFamily="monospace">
                {member.barcode}
              </Typography>
            </Box>
          )}
        </Stack>
      </Paper>
    </Grid>

    <Grid size={{ xs: 12, md: 9 }}>
      <Stack spacing={2}>
        <Paper variant="outlined" sx={{ p: '1.0rem' }}>
          <Typography variant="subtitle2" color="primary" fontWeight="bold" gutterBottom>
            البيانات الشخصية
          </Typography>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="caption" color="text.secondary">
                الاسم الكامل
              </Typography>
              <Typography variant="h6" fontWeight="bold" sx={{ lineHeight: 1.2 }}>
                {member.fullName}
              </Typography>
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <Typography variant="caption" color="text.secondary">
                الرقم الوطني
              </Typography>
              <Typography variant="body2" fontFamily="monospace">
                {member.nationalNumber || '-'}
              </Typography>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Typography variant="caption" color="text.secondary">
                الجنسية
              </Typography>
              <Typography variant="body2">{member.nationality || '-'}</Typography>
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <Typography variant="caption" color="text.secondary">
                تاريخ الميلاد
              </Typography>
              <Typography variant="body2" dir="ltr">
                {formatDate(member.birthDate)}
              </Typography>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <Typography variant="caption" color="text.secondary">
                الجنس
              </Typography>
              <Typography variant="body2">
                {member.gender === GENDERS.MALE ? 'ذكر' : member.gender === GENDERS.FEMALE ? 'أنثى' : '-'}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, md: 10 }}>
              {member.notes && (
                <Typography
                  variant="caption"
                  sx={{ display: 'block', bgcolor: 'warning.lighter', color: 'warning.dark', p: 0.5, borderRadius: 0.5 }}
                >
                  ملاحظات: {member.notes}
                </Typography>
              )}
            </Grid>
          </Grid>
        </Paper>

        <Grid container spacing={2}>
          {isPrincipal && (
            <Grid size={{ xs: 12, md: 6 }} sx={{ display: 'flex' }}>
              <Paper variant="outlined" sx={{ p: '1.0rem', flex: 1 }}>
                <Stack direction="row" spacing={1} sx={{ mb: '0.75rem' }}>
                  <BadgeIcon fontSize="small" color="action" />
                  <Typography variant="subtitle2" fontWeight="bold">
                    بيانات العمل
                  </Typography>
                </Stack>
                <Stack spacing={1.5}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      جهة العمل
                    </Typography>
                    <Typography variant="body2" fontWeight="medium">
                      {member.employerName || '-'}
                    </Typography>
                  </Box>
                  <Grid container>
                    <Grid size={6}>
                      <Typography variant="caption" color="text.secondary">
                        الرقم الوظيفي
                      </Typography>
                      <Typography variant="body2" fontFamily="monospace">
                        {member.employeeNumber || '-'}
                      </Typography>
                    </Grid>
                    <Grid size={6}>
                      <Typography variant="caption" color="text.secondary">
                        المهنة
                      </Typography>
                      <Typography variant="body2">{member.occupation || '-'}</Typography>
                    </Grid>
                  </Grid>
                </Stack>
              </Paper>
            </Grid>
          )}

          <Grid size={{ xs: 12, md: isPrincipal ? 6 : 12 }} sx={{ display: 'flex' }}>
            <Paper variant="outlined" sx={{ p: '1.0rem', flex: 1 }}>
              <Stack direction="row" spacing={1} sx={{ mb: '0.75rem' }}>
                <ContactPhoneIcon fontSize="small" color="action" />
                <Typography variant="subtitle2" fontWeight="bold">
                  معلومات الاتصال
                </Typography>
              </Stack>
              <Stack spacing={2}>
                <Grid container>
                  <Grid size={6}>
                    <Typography variant="caption" color="text.secondary">
                      رقم الهاتف
                    </Typography>
                    <Typography variant="body2" dir="ltr">
                      {member.phone || '-'}
                    </Typography>
                  </Grid>
                  <Grid size={6}>
                    <Typography variant="caption" color="text.secondary">
                      البريد الإلكتروني
                    </Typography>
                    <Typography variant="caption" display="block" sx={{ wordBreak: 'break-all' }}>
                      {member.email || '-'}
                    </Typography>
                  </Grid>
                </Grid>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    العنوان
                  </Typography>
                  <Typography variant="body2">{member.address || '-'}</Typography>
                </Box>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      </Stack>
    </Grid>
  </Grid>
);

export default MemberPersonalInfoTab;
