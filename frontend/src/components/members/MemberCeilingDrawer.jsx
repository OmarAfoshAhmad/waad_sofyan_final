import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';

import { getLimitDetail } from 'services/api/unified-members.service';

/**
 * One member's ceiling in full.
 *
 * Opens with the figures the list row already has, so it is readable before
 * the request returns and never shows an empty panel for data it is holding.
 * The request adds the buckets, and refreshes the general figures from the
 * same read the column used -- they cannot disagree, but they can be minutes
 * apart, which is what readAt is for.
 *
 * The buckets are listed beside the general ceiling and never summed with it:
 * one claim line can map to several buckets, so adding them would count the
 * same money once per category it fell into.
 */

const formatAmount = (value) => {
  if (value === null || value === undefined) {
    return 'غير متاح';
  }
  return `${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })} د.ل`;
};

const formatInstant = (value) => {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toLocaleString('en-GB');
};

function Figure({ label, value, emphasis, hint }) {
  return (
    <Stack spacing={0.25}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant={emphasis ? 'h6' : 'body1'} fontWeight={emphasis ? 'bold' : 'medium'}>
        {value}
      </Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary">
          {hint}
        </Typography>
      )}
    </Stack>
  );
}

Figure.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.node.isRequired,
  emphasis: PropTypes.bool,
  hint: PropTypes.string
};

export default function MemberCeilingDrawer({ open, member, initialSummary, onClose }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const memberId = member?.id;

  const load = useCallback(async () => {
    if (!memberId) return;
    setLoading(true);
    setError('');
    try {
      setDetail(await getLimitDetail(memberId));
    } catch (requestError) {
      console.error('Error fetching member ceiling detail:', requestError);
      setError('تعذّرت قراءة تفاصيل السقف. حاول مرة أخرى.');
    } finally {
      setLoading(false);
    }
  }, [memberId]);

  useEffect(() => {
    if (!open) {
      setDetail(null);
      setError('');
      return;
    }
    load();
  }, [open, load]);

  // The row's figures until the request lands, then the drawer's own. Both
  // come from the same reader, so this is a head start rather than a
  // second opinion.
  const general = detail?.general || initialSummary || null;
  const unavailable = !general || general.mode === 'UNAVAILABLE';
  const hasCeiling = general?.mode === 'FOUND';
  const buckets = detail?.buckets || [];

  return (
    <Drawer anchor="left" open={open} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', sm: '32rem' } } }}>
      <Box sx={{ p: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Box>
            <Typography variant="h5">{member?.fullName || 'السقف والرصيد'}</Typography>
            <Typography variant="caption" color="text.secondary" fontFamily="monospace">
              {member?.cardNumber}
            </Typography>
          </Box>
          <IconButton onClick={onClose} aria-label="إغلاق">
            <CloseIcon />
          </IconButton>
        </Stack>

        <Divider sx={{ my: 2 }} />

        {unavailable ? (
          <Alert
            severity="warning"
            action={
              <Button color="inherit" size="small" onClick={load}>
                إعادة المحاولة
              </Button>
            }
          >
            {error || 'تعذّرت قراءة الرصيد حالياً. الأرقام غير معروضة عمداً بدل عرض أصفار.'}
          </Alert>
        ) : (
          <>
            <Stack spacing={0.25} sx={{ mb: 2 }}>
              <Typography variant="subtitle2">السقف العام</Typography>
              <Typography variant="caption" color="text.secondary">
                بتاريخ {general.asOfDate}
                {formatInstant(general.readAt) ? ` · قُرئ ${formatInstant(general.readAt)}` : ''}
              </Typography>
            </Stack>

            {hasCeiling ? (
              <Stack spacing={1.5}>
                <Figure label="السقف السنوي" value={formatAmount(general.limit)} />
                <Figure label="المستهلك الفعلي" value={formatAmount(general.committed)} />
                <Figure label="المحجوز بموافقات مسبقة" value={formatAmount(general.reserved)} />
                <Figure label="المتبقي الفعلي" value={formatAmount(general.actualRemaining)} hint="محاسبياً: السقف ناقص المستهلك" />
                <Figure
                  label="المتاح لالتزام جديد"
                  value={formatAmount(general.reservableAvailable)}
                  emphasis
                  hint="المتبقي الفعلي ناقص المحجوز — الرقم الذي يُتخذ عليه القرار"
                />
                {general.alertStatus === 'EXCEEDED' && (
                  <Alert severity="error">تم تجاوز السقف السنوي</Alert>
                )}
              </Stack>
            ) : (
              <Alert severity="info">
                {general.mode === 'UNLIMITED'
                  ? 'الوثيقة لا تحدد سقفاً سنوياً، فلا توجد نسبة تُعرض.'
                  : 'لا توجد وثيقة سارية للعضو بهذا التاريخ.'}
              </Alert>
            )}

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              الأوعية
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
              أرصدة مستقلة عن السقف العام ولا تُجمع معه
            </Typography>

            {loading && !detail ? (
              <Skeleton variant="rounded" height="6rem" />
            ) : buckets.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                لا توجد أوعية معرّفة في هذه الوثيقة.
              </Typography>
            ) : (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>الوعاء</TableCell>
                      <TableCell align="center">السقف</TableCell>
                      <TableCell align="center">مستهلك</TableCell>
                      <TableCell align="center">محجوز</TableCell>
                      <TableCell align="center">المتبقي الفعلي</TableCell>
                      <TableCell align="center">المتاح لالتزام جديد</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {buckets.map((bucket) => (
                      <TableRow key={bucket.bucketId} hover>
                        <TableCell>
                          <Typography variant="body2">{bucket.name}</Typography>
                          <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                            {bucket.code}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">{formatAmount(bucket.limit)}</TableCell>
                        <TableCell align="center">{formatAmount(bucket.committed)}</TableCell>
                        <TableCell align="center">{formatAmount(bucket.reserved)}</TableCell>
                        <TableCell align="center">{formatAmount(bucket.actualRemaining)}</TableCell>
                        <TableCell align="center">
                          <Typography
                            variant="body2"
                            fontWeight="bold"
                            color={Number(bucket.reservableAvailable) < 0 ? 'error.main' : 'text.primary'}
                          >
                            {formatAmount(bucket.reservableAvailable)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}

            {error && (
              <Alert
                severity="warning"
                sx={{ mt: 2 }}
                action={
                  <Button color="inherit" size="small" onClick={load}>
                    إعادة المحاولة
                  </Button>
                }
              >
                {error}
              </Alert>
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

MemberCeilingDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  member: PropTypes.shape({
    id: PropTypes.number,
    fullName: PropTypes.string,
    cardNumber: PropTypes.string
  }),
  initialSummary: PropTypes.object,
  onClose: PropTypes.func.isRequired
};
