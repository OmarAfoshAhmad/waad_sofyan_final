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

/**
 * Label and figure in a two-column table rather than a stack of captions.
 *
 * Loose pairs down a panel make the reader match each number to its label by
 * position, and the moment two of them sit close together the eye pairs the
 * wrong ones. A table row does that matching for them.
 */
function FigureTable({ caption, rows }) {
  return (
    <Box>
      {caption && (
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
          {caption}
        </Typography>
      )}
      <Table size="small">
        <TableBody>
          {rows.map(([label, value, hint, emphasis]) => (
            <TableRow key={label}>
              <TableCell sx={{ border: 0, py: 0.75, width: '55%' }}>
                <Typography variant="body2" color="text.secondary">
                  {label}
                </Typography>
                {hint && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    {hint}
                  </Typography>
                )}
              </TableCell>
              <TableCell align="left" sx={{ border: 0, py: 0.75 }}>
                <Typography
                  variant={emphasis ? 'h6' : 'body1'}
                  fontWeight={emphasis ? 'bold' : 'medium'}
                >
                  {value}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}

FigureTable.propTypes = {
  caption: PropTypes.string,
  rows: PropTypes.arrayOf(PropTypes.array).isRequired
};

/**
 * @param onManageUplifts optional. When supplied, the drawer offers a way into
 *   the exceptions on this ceiling; the caller passes it only for a user who
 *   holds MEMBER_LIMIT_UPLIFT_MANAGE, so a user without the grant sees no
 *   control rather than a control that will be refused.
 */
export default function MemberCeilingDrawer({ open, member, initialSummary, onClose, onManageUplifts }) {
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
              <Stack spacing={2}>
                {/* What the ledger holds. Three facts, read not derived. */}
                <FigureTable
                  rows={[
                    // When an exception is raising this member's ceiling, the
                    // ceiling is shown decomposed. A single raised figure
                    // cannot be checked against the policy, and the whole
                    // point of an exception is that someone can later ask why
                    // this member's ceiling differs from their colleague's.
                    ...(general.uplift > 0
                      ? [
                          ['سقف الوثيقة', formatAmount(general.policyLimit)],
                          ['استثناء مضاف', `+ ${formatAmount(general.uplift)}`, 'زيادة استثنائية لهذا المستفيد وحده'],
                          ['السقف السنوي المطبَّق', formatAmount(general.limit), 'سقف الوثيقة + الاستثناء']
                        ]
                      : [['السقف السنوي', formatAmount(general.limit)]]),
                    ['المستهلك الفعلي', formatAmount(general.committed)],
                    ['المحجوز بموافقات مسبقة', formatAmount(general.reserved)]
                  ]}
                />

                {/* What follows from them. Kept in their own table because
                    they are conclusions, not readings, and reading them in a
                    single list with the three above invites adding a figure
                    to something it was already subtracted from. */}
                {onManageUplifts && (
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => onManageUplifts(member)}
                    sx={{ alignSelf: 'flex-start' }}
                  >
                    {general.uplift > 0 ? 'إدارة استثناءات السقف' : 'رفع السقف استثناءً'}
                  </Button>
                )}

                <FigureTable
                  caption="المحسوب منها"
                  rows={[
                    [
                      'المتبقي محاسبياً',
                      formatAmount(general.actualRemaining),
                      'السقف ناقص المستهلك'
                    ],
                    [
                      'المتاح لالتزام جديد',
                      formatAmount(general.reservableAvailable),
                      'المتبقي محاسبياً ناقص المحجوز — الرقم الذي يُتخذ عليه القرار',
                      true
                    ]
                  ]}
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
                      {/* Two figures in one column, stacked. Six columns in a
                          drawer this wide left every number cramped, and the
                          two that belong together were the ones split apart. */}
                      <TableCell align="center">محجوز / المتبقي</TableCell>
                      <TableCell align="center">المتاح لالتزام جديد</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {buckets.map((bucket) => {
                      const name = (
                        <TableCell>
                          <Typography variant="body2">{bucket.name}</Typography>
                          <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                            {bucket.code}
                          </Typography>
                        </TableCell>
                      );

                      // A bucket that limits occurrences and not money has no
                      // monetary figures at all. Laying it out as five
                      // "غير متاح" cells read as a broken row rather than as a
                      // benefit measured in visits.
                      if (bucket.limit === null || bucket.limit === undefined) {
                        return (
                          <TableRow key={bucket.bucketId} hover>
                            {name}
                            <TableCell colSpan={4}>
                              <Typography variant="body2" color="text.secondary">
                                {bucket.timesLimit !== null && bucket.timesLimit !== undefined
                                  ? `يحدّ عدد المرات (${bucket.timesLimit}) ولا يحدّ مبلغاً`
                                  : 'لا يحدّ مبلغاً'}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        );
                      }

                      return (
                        <TableRow key={bucket.bucketId} hover>
                          {name}
                          <TableCell align="center">{formatAmount(bucket.limit)}</TableCell>
                          <TableCell align="center">{formatAmount(bucket.committed)}</TableCell>
                          <TableCell align="center">
                            <Typography variant="body2">{formatAmount(bucket.reserved)}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              متبقٍ {formatAmount(bucket.actualRemaining)}
                            </Typography>
                          </TableCell>
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
                      );
                    })}
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
  onManageUplifts: PropTypes.func,
  open: PropTypes.bool.isRequired,
  member: PropTypes.shape({
    id: PropTypes.number,
    fullName: PropTypes.string,
    cardNumber: PropTypes.string
  }),
  initialSummary: PropTypes.object,
  onClose: PropTypes.func.isRequired
};
