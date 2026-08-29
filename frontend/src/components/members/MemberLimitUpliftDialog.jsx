import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { Undo as UndoIcon, Add as AddIcon } from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import Paper from '@mui/material/Paper';
import {
  getLimitUplifts,
  grantLimitUplift,
  revokeLimitUplift
} from 'services/api/unified-members.service';
import { normalizeApiError } from 'utils/api-error';

const SOURCE_LABEL = {
  EMPLOYER_REQUEST: 'بطلب جهة العمل',
  SPECIAL_CONSIDERATION: 'اعتبارات خاصة'
};

const STATE_LABEL = {
  IN_FORCE: 'ساري',
  SCHEDULED: 'مجدول',
  EXPIRED: 'منتهٍ',
  REVOKED: 'ملغى'
};

const STATE_COLOR = {
  IN_FORCE: 'success',
  SCHEDULED: 'info',
  EXPIRED: 'default',
  REVOKED: 'default'
};

const formatAmount = (value) =>
  value === null || value === undefined
    ? '-'
    : `${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} د.ل`;

const EMPTY_FORM = { amount: '', effectiveFrom: '', effectiveTo: '', source: 'EMPLOYER_REQUEST', reason: '' };

/**
 * Managing the exceptions on one member's general ceiling.
 *
 * Both halves of the record are on screen at once on purpose: the amount and
 * its reason, and the two accounts -- who granted it and, if it ended early,
 * who ended it and why. An exception whose author is not visible beside it is
 * an exception nobody can question later, which is the whole thing this
 * feature exists to prevent.
 */
const MemberLimitUpliftDialog = ({ open, onClose, member, onChanged }) => {
  const { enqueueSnackbar } = useSnackbar();
  const [uplifts, setUplifts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [revoking, setRevoking] = useState(null);
  const [revokeReason, setRevokeReason] = useState('');

  const memberId = member?.id;

  const load = useCallback(async () => {
    if (!memberId) return;
    setLoading(true);
    try {
      const response = await getLimitUplifts(memberId);
      setUplifts(response?.data || []);
    } catch (err) {
      enqueueSnackbar(normalizeApiError(err).message || 'تعذر جلب استثناءات السقف', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [memberId, enqueueSnackbar]);

  useEffect(() => {
    if (open) {
      setForm(EMPTY_FORM);
      setRevoking(null);
      setRevokeReason('');
      load();
    }
  }, [open, load]);

  const submitGrant = async () => {
    if (!form.amount || Number(form.amount) <= 0) {
      enqueueSnackbar('أدخل مبلغاً أكبر من صفر', { variant: 'warning' });
      return;
    }
    if (!form.reason.trim()) {
      enqueueSnackbar('سبب رفع السقف إلزامي', { variant: 'warning' });
      return;
    }
    setSaving(true);
    try {
      await grantLimitUplift(memberId, {
        amount: Number(form.amount),
        effectiveFrom: form.effectiveFrom || null,
        effectiveTo: form.effectiveTo || null,
        source: form.source,
        reason: form.reason.trim()
      });
      enqueueSnackbar('تم رفع السقف استثناءً', { variant: 'success' });
      setForm(EMPTY_FORM);
      await load();
      onChanged?.();
    } catch (err) {
      enqueueSnackbar(normalizeApiError(err).message || 'تعذر رفع السقف', { variant: 'error' });
    } finally {
      setSaving(false);
    }
  };

  const submitRevoke = async () => {
    if (!revokeReason.trim()) {
      enqueueSnackbar('سبب الإلغاء إلزامي', { variant: 'warning' });
      return;
    }
    setSaving(true);
    try {
      await revokeLimitUplift(revoking.id, revokeReason.trim());
      enqueueSnackbar('تم إلغاء الاستثناء', { variant: 'success' });
      setRevoking(null);
      setRevokeReason('');
      await load();
      onChanged?.();
    } catch (err) {
      enqueueSnackbar(normalizeApiError(err).message || 'تعذر إلغاء الاستثناء', { variant: 'error' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        استثناءات السقف العام
        <Typography variant="body2" color="text.secondary">
          {member?.fullName}
        </Typography>
      </DialogTitle>

      <DialogContent dividers>
        <Stack spacing={2.5}>
          {/* ── grant ─────────────────────────────────────────────── */}
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              رفع السقف استثناءً
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
              <TextField
                size="small"
                type="number"
                label="المبلغ المضاف"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
                sx={{ minWidth: '9rem' }}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                select
                size="small"
                label="المصدر"
                value={form.source}
                onChange={(e) => setForm({ ...form, source: e.target.value })}
                sx={{ minWidth: '11rem' }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="EMPLOYER_REQUEST">بطلب جهة العمل</MenuItem>
                <MenuItem value="SPECIAL_CONSIDERATION">اعتبارات خاصة</MenuItem>
              </TextField>
              <TextField
                size="small"
                type="date"
                label="يبدأ من"
                value={form.effectiveFrom}
                onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
                sx={{ minWidth: '10rem' }}
                InputLabelProps={{ shrink: true }}
                helperText="اتركه فارغاً ليبدأ اليوم"
              />
              <TextField
                size="small"
                type="date"
                label="ينتهي في"
                value={form.effectiveTo}
                onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })}
                sx={{ minWidth: '10rem' }}
                InputLabelProps={{ shrink: true }}
                helperText="اتركه فارغاً بلا نهاية"
              />
            </Stack>
            <TextField
              fullWidth
              size="small"
              label="سبب رفع السقف"
              value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
              multiline
              minRows={2}
              sx={{ mt: 1.5 }}
              InputLabelProps={{ shrink: true }}
              placeholder="مثال: بطلب جهة العمل لحالة علاجية مستمرة، خطاب رقم ..."
            />
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={submitGrant}
              disabled={saving}
              sx={{ mt: 1.5 }}
            >
              رفع السقف
            </Button>
          </Box>

          <Divider />

          {/* ── history ───────────────────────────────────────────── */}
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              السجل الكامل
            </Typography>
            {loading ? (
              <Stack alignItems="center" sx={{ py: 3 }}>
                <CircularProgress size={28} />
              </Stack>
            ) : uplifts.length === 0 ? (
              <Alert severity="info">لا توجد استثناءات مسجَّلة على سقف هذا المستفيد</Alert>
            ) : (
              <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell align="center">المبلغ</TableCell>
                      <TableCell align="center">المدة</TableCell>
                      <TableCell align="center">المصدر</TableCell>
                      <TableCell align="right">السبب</TableCell>
                      <TableCell align="center">مَن منحه</TableCell>
                      <TableCell align="center">الحالة</TableCell>
                      <TableCell align="center">إجراء</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {uplifts.map((uplift) => (
                      <TableRow key={uplift.id} hover>
                        <TableCell align="center">{formatAmount(uplift.amount)}</TableCell>
                        <TableCell align="center">
                          <Typography variant="caption" display="block">
                            {uplift.effectiveFrom}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" display="block">
                            {uplift.effectiveTo ? `حتى ${uplift.effectiveTo}` : 'بلا نهاية'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">
                          <Typography variant="caption">{SOURCE_LABEL[uplift.source] || uplift.source}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">{uplift.reason}</Typography>
                          {/* The revocation reason sits under the grant reason
                              rather than replacing it: two decisions were made
                              about this row and both are on the record. */}
                          {uplift.revokedReason && (
                            <Typography variant="caption" color="text.secondary" display="block">
                              سبب الإلغاء: {uplift.revokedReason}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="center">
                          <Typography variant="caption" display="block">
                            {uplift.grantedByUsername || '-'}
                          </Typography>
                          {uplift.revokedByUsername && (
                            <Typography variant="caption" color="text.secondary" display="block">
                              ألغاه: {uplift.revokedByUsername}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="center">
                          <Chip
                            size="small"
                            label={STATE_LABEL[uplift.state] || uplift.state}
                            color={STATE_COLOR[uplift.state] || 'default'}
                            variant={uplift.state === 'IN_FORCE' ? 'filled' : 'outlined'}
                          />
                        </TableCell>
                        <TableCell align="center">
                          {(uplift.state === 'IN_FORCE' || uplift.state === 'SCHEDULED') && (
                            <Tooltip title="إلغاء هذا الاستثناء">
                              <Button
                                size="small"
                                color="warning"
                                startIcon={<UndoIcon fontSize="small" />}
                                onClick={() => {
                                  setRevoking(uplift);
                                  setRevokeReason('');
                                }}
                              >
                                إلغاء
                              </Button>
                            </Tooltip>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>

          {/* ── revoke ────────────────────────────────────────────── */}
          {revoking && (
            <Alert severity="warning" sx={{ alignItems: 'flex-start' }}>
              <Typography variant="body2" sx={{ mb: 1 }}>
                إلغاء استثناء بمبلغ {formatAmount(revoking.amount)}. إن كان قد أُدخل اليوم بالخطأ فلن يكون قد رفع
                السقف في أي لحظة؛ وإن كان سارياً منذ تاريخ أسبق فسيتوقف من اليوم دون المساس بالماضي.
              </Typography>
              <TextField
                fullWidth
                size="small"
                label="سبب الإلغاء"
                value={revokeReason}
                onChange={(e) => setRevokeReason(e.target.value)}
                InputLabelProps={{ shrink: true }}
                placeholder="مثال: أُدخل بالخطأ، أو انتهت الحالة العلاجية"
              />
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                <Button size="small" variant="contained" color="warning" onClick={submitRevoke} disabled={saving}>
                  تأكيد الإلغاء
                </Button>
                <Button size="small" onClick={() => setRevoking(null)} disabled={saving}>
                  تراجع
                </Button>
              </Stack>
            </Alert>
          )}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          إغلاق
        </Button>
      </DialogActions>
    </Dialog>
  );
};

MemberLimitUpliftDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  member: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    fullName: PropTypes.string
  }),
  onChanged: PropTypes.func
};

export default MemberLimitUpliftDialog;
