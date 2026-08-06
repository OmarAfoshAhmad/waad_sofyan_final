import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  IconButton,
  LinearProgress,
  Stack,
  Typography
} from '@mui/material';
import { Close as CloseIcon, AccountBalanceWallet as WalletIcon } from '@mui/icons-material';
import { getMemberBenefitUsage } from 'services/api/unified-members.service';

const money = (value) => (value == null ? 'غير محدود' : `${Number(value).toLocaleString('ar-LY', { maximumFractionDigits: 2 })} د.ل`);

const bucketState = {
  UNUSED: { label: 'غير مستخدم', color: 'default' },
  PARTIALLY_USED: { label: 'مستخدم جزئيًا', color: 'info' },
  EXHAUSTED: { label: 'السقف مستهلك بالكامل', color: 'warning' },
  UNLIMITED: { label: 'غير محدود', color: 'success' },
  MEMBERSHIP_INELIGIBLE: { label: 'العضوية غير مؤهلة', color: 'error' }
};

export default function MemberBenefitUsageDrawer({ open, memberId, onClose }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !memberId) return;
    let active = true;
    setLoading(true);
    setError('');
    getMemberBenefitUsage(memberId)
      .then((payload) => active && setData(payload?.data ?? payload))
      .catch((err) => active && setError(err?.response?.data?.message || 'تعذر تحميل حالة السقوف'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [open, memberId]);

  const totals = useMemo(() => {
    const finite = (data?.buckets || []).filter((item) => item.amountLimit != null);
    return finite.reduce(
      (acc, item) => ({ limit: acc.limit + Number(item.amountLimit || 0), used: acc.used + Number(item.usedAmount || 0) }),
      { limit: 0, used: 0 }
    );
  }, [data]);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 560 }, maxWidth: '100%' } } }}
    >
      <Box sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Stack direction="row" spacing={1} alignItems="center">
            <WalletIcon color="primary" />
            <Typography variant="h5">حالة التغطية والسقوف</Typography>
          </Stack>
          <IconButton onClick={onClose} aria-label="إغلاق"><CloseIcon /></IconButton>
        </Stack>
        {data && (
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 2, flexWrap: 'wrap', gap: 1 }}>
            <Box sx={{ flex: 1, minWidth: 180 }}>
              <Typography fontWeight={600}>{data.memberName}</Typography>
              <Typography variant="body2" color="text.secondary">{data.cardNumber} · {data.policyName}</Typography>
            </Box>
            <Chip
              size="small"
              color={data.membershipStatus === 'ACTIVE' ? 'success' : 'error'}
              label={data.membershipStatus === 'ACTIVE' ? 'العضوية نشطة' : 'العضوية غير مؤهلة'}
            />
          </Stack>
        )}
      </Box>

      <Box sx={{ p: 2.5, overflowY: 'auto', flex: 1 }}>
        {loading && <Stack alignItems="center" sx={{ py: 8 }}><CircularProgress /></Stack>}
        {!loading && error && <Alert severity="error">{error}</Alert>}
        {!loading && data && (
          <>
            <Stack direction="row" divider={<Divider orientation="vertical" flexItem />} sx={{ mb: 2.5 }}>
              <Box sx={{ flex: 1 }}><Typography variant="caption" color="text.secondary">إجمالي السقوف المحدودة</Typography><Typography fontWeight={600}>{money(totals.limit)}</Typography></Box>
              <Box sx={{ flex: 1, px: 2 }}><Typography variant="caption" color="text.secondary">إجمالي المستخدم</Typography><Typography fontWeight={600}>{money(totals.used)}</Typography></Box>
              <Box sx={{ flex: 1, px: 2 }}><Typography variant="caption" color="text.secondary">المتبقي</Typography><Typography fontWeight={600}>{money(Math.max(0, totals.limit - totals.used))}</Typography></Box>
            </Stack>

            <Alert severity="info" sx={{ mb: 2 }}>
              اكتمال سقف منفعة واحدة لا يغيّر حالة العضوية ولا يمنع استخدام السقوف الأخرى.
            </Alert>

            <Stack spacing={0} divider={<Divider flexItem />}>
              {data.buckets.map((bucket) => {
                const state = bucketState[bucket.status] || bucketState.UNUSED;
                const percent = Number(bucket.usagePercent || 0);
                return (
                  <Box key={bucket.bucketId} sx={{ py: 2 }}>
                    <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                      <Box>
                        <Typography fontWeight={600}>{bucket.bucketName}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {bucket.groupName ? `${bucket.groupName} · ` : ''}{bucket.bucketCode}
                        </Typography>
                      </Box>
                      <Chip size="small" color={state.color} label={state.label} />
                    </Stack>
                    {bucket.amountLimit != null && (
                      <>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(100, percent)}
                          color={bucket.status === 'EXHAUSTED' ? 'warning' : 'primary'}
                          sx={{ mt: 1.5, mb: 1, height: 8, borderRadius: 4 }}
                        />
                        <Stack direction="row" justifyContent="space-between" flexWrap="wrap" gap={0.5}>
                          <Typography variant="body2">المستخدم: {money(bucket.usedAmount)} ({percent.toFixed(1)}%)</Typography>
                          <Typography variant="body2">المتبقي: {money(bucket.remainingAmount)}</Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          مرحّل: {money(bucket.openingUsedAmount)} · مطالبات النظام: {money(bucket.claimUsedAmount)}
                        </Typography>
                      </>
                    )}
                    {(bucket.timesLimit != null || bucket.daysLimit != null) && (
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.75 }}>
                        {bucket.timesLimit != null ? `المرات: ${bucket.usedTimes}/${bucket.timesLimit}` : ''}
                        {bucket.timesLimit != null && bucket.daysLimit != null ? ' · ' : ''}
                        {bucket.daysLimit != null ? `الأيام: ${bucket.usedDays}/${bucket.daysLimit}` : ''}
                      </Typography>
                    )}
                  </Box>
                );
              })}
            </Stack>
            {!data.buckets.length && <Alert severity="warning">لا توجد سقوف معرفة في وثيقة هذا المستفيد.</Alert>}
          </>
        )}
      </Box>
    </Drawer>
  );
}

MemberBenefitUsageDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  memberId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onClose: PropTypes.func.isRequired
};

