import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Stack,
  Divider,
  Chip,
  Button,
  Alert,
  CircularProgress,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Collapse,
  TextField,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions
} from '@mui/material';
import {
  Close as CloseIcon,
  Add as AddIcon,
  CheckCircle as PostIcon,
  Undo as ReverseIcon,
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  Build as AdjustIcon
} from '@mui/icons-material';

import ActionConfirmDialog from 'components/tba/ActionConfirmDialog';
import { formatCurrency } from 'utils/currency-formatter';
import useSystemConfig from 'hooks/useSystemConfig';
import { providerPaymentsV2Service, reconciliationService, providerAccountsService } from 'services/api/settlement.service';

import NewProviderPaymentDialog from './NewProviderPaymentDialog';

const STATUS_LABELS = { DRAFT: 'مسودة', POSTED: 'مرحّلة', REVERSED: 'معكوسة' };
const STATUS_COLORS = { DRAFT: 'default', POSTED: 'success', REVERSED: 'warning' };

function PaymentRow({ payment, accountVersion, writeEnabled, onChanged }) {
  const [expanded, setExpanded] = useState(false);
  const [confirmPost, setConfirmPost] = useState(false);
  const [confirmReverse, setConfirmReverse] = useState(false);
  const [reverseReason, setReverseReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const doPost = async () => {
    setBusy(true);
    setErrorMsg('');
    try {
      await providerPaymentsV2Service.post(payment.id, {
        expectedPaymentVersion: payment.version,
        expectedAccountVersion: accountVersion
      });
      setConfirmPost(false);
      onChanged();
    } catch (e) {
      setErrorMsg(e?.message || 'تعذّر ترحيل الدفعة');
    } finally {
      setBusy(false);
    }
  };

  const doReverse = async () => {
    setBusy(true);
    setErrorMsg('');
    try {
      await providerPaymentsV2Service.reverse(payment.id, {
        reason: reverseReason,
        expectedPaymentVersion: payment.version,
        expectedAccountVersion: accountVersion
      });
      setConfirmReverse(false);
      onChanged();
    } catch (e) {
      setErrorMsg(e?.message || 'تعذّر عكس الدفعة');
    } finally {
      setBusy(false);
    }
  };

  const allocationCount = payment.allocations?.length || 0;
  const allocatedAmount = payment.allocatedAmount || 0;

  return (
    <>
      <TableRow hover>
        <TableCell>
          <IconButton size="small" onClick={() => setExpanded((v) => !v)}>
            {expanded ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
          </IconButton>
        </TableCell>
        <TableCell>{payment.paymentDate}</TableCell>
        <TableCell align="right">{formatCurrency(payment.amount)}</TableCell>
        <TableCell align="right">{formatCurrency(allocatedAmount)}</TableCell>
        <TableCell>
          <Chip size="small" label={STATUS_LABELS[payment.status] || payment.status} color={STATUS_COLORS[payment.status]} />
        </TableCell>
        <TableCell>{payment.paymentMethod}</TableCell>
        <TableCell align="left">
          {payment.status === 'DRAFT' && (
            <Button size="small" startIcon={<PostIcon />} disabled={!writeEnabled} onClick={() => setConfirmPost(true)}>
              ترحيل
            </Button>
          )}
          {payment.status === 'POSTED' && (
            <Button
              size="small"
              color="warning"
              startIcon={<ReverseIcon />}
              disabled={!writeEnabled}
              onClick={() => setConfirmReverse(true)}
            >
              عكس
            </Button>
          )}
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={7} sx={{ py: 0, borderBottom: expanded ? undefined : 'none' }}>
          <Collapse in={expanded} unmountOnExit>
            <Box sx={{ py: 1, pl: 4 }}>
              {allocationCount === 0 ? (
                <Typography variant="caption" color="text.secondary">
                  لا يوجد تخصيص بعد لهذه الدفعة
                </Typography>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>جهة العمل</TableCell>
                      <TableCell>الفترة</TableCell>
                      <TableCell align="right">المبلغ المخصَّص</TableCell>
                      <TableCell align="right">المستحق عند التخصيص</TableCell>
                      <TableCell>الطريقة</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {payment.allocations.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell>{a.employerId}</TableCell>
                        <TableCell>
                          {a.targetYear}/{a.targetMonth}
                        </TableCell>
                        <TableCell align="right">{formatCurrency(a.amount)}</TableCell>
                        <TableCell align="right">{formatCurrency(a.outstandingAtAllocation)}</TableCell>
                        <TableCell>{a.allocationMethod}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              {payment.unallocatedAmount > 0 && (
                <Alert severity="info" sx={{ mt: 1 }}>
                  مبلغ غير مخصَّص: {formatCurrency(payment.unallocatedAmount)}
                </Alert>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>

      <ActionConfirmDialog
        open={confirmPost}
        title="ترحيل الدفعة"
        message={`سيتم ترحيل دفعة بقيمة ${formatCurrency(payment.amount)} وخصم حساب مقدم الخدمة (نسخة الدفعة: ${payment.version}). لا يمكن التراجع إلا بالعكس.`}
        onClose={() => setConfirmPost(false)}
        onConfirm={doPost}
        confirmText={busy ? '...جارٍ' : 'ترحيل'}
        confirmColor="primary"
        icon={<PostIcon color="primary" />}
      />

      <Dialog open={confirmReverse} onClose={() => setConfirmReverse(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <ReverseIcon color="warning" />
          عكس الدفعة
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            سيُعاد فتح <b>{allocationCount}</b> تخصيص بقيمة إجمالية <b>{formatCurrency(allocatedAmount)}</b> لهذه الدفعة، وسيُقيَّد رصيد
            مقدم الخدمة دائناً بمقدار <b>{formatCurrency(payment.amount)}</b>. هذا إجراء محاسبي حقيقي ولا يُخفى أثره.
          </Alert>
          <TextField
            label="سبب العكس"
            value={reverseReason}
            onChange={(e) => setReverseReason(e.target.value)}
            fullWidth
            required
            multiline
            minRows={2}
            helperText="السبب هو السجل التدقيقي الوحيد لهذا العكس"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmReverse(false)}>إلغاء</Button>
          <Button color="warning" variant="contained" disabled={busy || !reverseReason.trim()} onClick={doReverse}>
            {busy ? '...جارٍ' : 'تأكيد العكس'}
          </Button>
        </DialogActions>
      </Dialog>
      {errorMsg && (
        <TableRow>
          <TableCell colSpan={7}>
            <Alert severity="error" sx={{ mt: 1 }} onClose={() => setErrorMsg('')}>
              {errorMsg}
            </Alert>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

export default function ProviderPaymentDetailDrawer({ providerId, open, onClose, onChanged }) {
  const { flags } = useSystemConfig();
  const writeEnabled = Boolean(flags?.PROVIDER_PAYMENT_POSTING_ENABLED);
  const queryClient = useQueryClient();

  const [newPaymentOpen, setNewPaymentOpen] = useState(false);
  const [adjustReason, setAdjustReason] = useState('');
  const [adjustBusy, setAdjustBusy] = useState(false);
  const [adjustError, setAdjustError] = useState('');
  const [showAdjustForm, setShowAdjustForm] = useState(false);

  const reconciliationQuery = useQuery({
    queryKey: ['provider-reconciliation', providerId],
    queryFn: () => reconciliationService.reconcileByProvider(providerId),
    enabled: open && Boolean(providerId)
  });

  const paymentsQuery = useQuery({
    queryKey: ['provider-payments-v2', providerId],
    queryFn: () => providerPaymentsV2Service.listByProvider(providerId),
    enabled: open && Boolean(providerId)
  });

  const ledgerQuery = useQuery({
    queryKey: ['provider-ledger', providerId],
    queryFn: () => providerAccountsService.getTransactions(providerId, { size: 50 }),
    enabled: open && Boolean(providerId)
  });

  // Post/reverse/adjust all require the account's current optimistic-lock
  // version — the reconciliation DTO doesn't carry it, so it's read from the
  // account endpoint directly (a read, unaffected by the frozen legacy writes).
  const accountQuery = useQuery({
    queryKey: ['provider-account', providerId],
    queryFn: () => providerAccountsService.getByProviderId(providerId),
    enabled: open && Boolean(providerId)
  });
  const accountVersion = accountQuery.data?.version;

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: ['provider-reconciliation', providerId] });
    queryClient.invalidateQueries({ queryKey: ['provider-payments-v2', providerId] });
    queryClient.invalidateQueries({ queryKey: ['provider-ledger', providerId] });
    queryClient.invalidateQueries({ queryKey: ['provider-account', providerId] });
    onChanged?.();
  };

  const reconciliation = reconciliationQuery.data;
  const payments = Array.isArray(paymentsQuery.data) ? paymentsQuery.data : [];
  const needsAdjustment = reconciliation?.requiresApprovedAdjustment;
  const isCredit = Number(reconciliation?.accountRunningBalance) < 0;

  const doAdjust = async () => {
    setAdjustBusy(true);
    setAdjustError('');
    try {
      await reconciliationService.adjust(providerId, {
        reason: adjustReason,
        expectedAccountVersion: accountVersion
      });
      setShowAdjustForm(false);
      setAdjustReason('');
      refreshAll();
    } catch (e) {
      setAdjustError(e?.message || 'تعذّرت التسوية');
    } finally {
      setAdjustBusy(false);
    }
  };

  return (
    <Drawer anchor="left" open={open} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 720 } } }}>
      <Box sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h5">{reconciliation?.providerName || `مقدم خدمة #${providerId}`}</Typography>
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Stack>

        {reconciliationQuery.isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : reconciliation ? (
          <Stack spacing={1} sx={{ mb: 3 }}>
            <Stack direction="row" spacing={3} flexWrap="wrap">
              <Typography variant="body2">
                المستحق: <b>{formatCurrency(reconciliation.accountTotalApproved)}</b>
              </Typography>
              <Typography variant="body2">
                المدفوع (دفتر): <b>{formatCurrency(reconciliation.ledgerNet)}</b>
              </Typography>
              <Typography variant="body2">
                الرصيد:{' '}
                <b>
                  {isCredit
                    ? `رصيد دائن ${formatCurrency(reconciliation.creditBalance)}`
                    : formatCurrency(reconciliation.accountRunningBalance)}
                </b>
              </Typography>
            </Stack>

            {needsAdjustment && (
              <Alert
                severity="error"
                action={
                  writeEnabled && (
                    <Button color="inherit" size="small" startIcon={<AdjustIcon />} onClick={() => setShowAdjustForm((v) => !v)}>
                      تسوية معتمدة
                    </Button>
                  )
                }
              >
                يوجد انحراف بين الدفتر وحساب المزود ({formatCurrency(reconciliation.ledgerVsAccountDrift)}) — حالة مستقلة عن الرصيد المستحق،
                لا تُخلط به.
              </Alert>
            )}

            <Collapse in={showAdjustForm}>
              <Stack spacing={1} sx={{ mt: 1 }}>
                <TextField
                  size="small"
                  label="سبب التسوية"
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                  fullWidth
                />
                {adjustError && <Alert severity="error">{adjustError}</Alert>}
                <Button variant="contained" color="error" disabled={adjustBusy || !adjustReason.trim()} onClick={doAdjust}>
                  {adjustBusy ? '...جارٍ' : `تسوية بمقدار ${formatCurrency(reconciliation.ledgerVsAccountDrift)}`}
                </Button>
              </Stack>
            </Collapse>
          </Stack>
        ) : null}

        <Divider sx={{ mb: 2 }} />

        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">الدفعات</Typography>
          <Button variant="outlined" size="small" startIcon={<AddIcon />} disabled={!writeEnabled} onClick={() => setNewPaymentOpen(true)}>
            دفعة جديدة
          </Button>
        </Stack>
        {!writeEnabled && (
          <Alert severity="info" sx={{ mb: 1 }}>
            مسار الدفعات الجديد قيد المراجعة — أزرار الإنشاء/الترحيل/العكس/التسوية معطّلة حتى التفعيل الرسمي.
          </Alert>
        )}

        {paymentsQuery.isLoading ? (
          <CircularProgress size={24} />
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell />
                <TableCell>التاريخ</TableCell>
                <TableCell align="right">المبلغ</TableCell>
                <TableCell align="right">المخصَّص</TableCell>
                <TableCell>الحالة</TableCell>
                <TableCell>الطريقة</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {payments.map((p) => (
                <PaymentRow
                  key={p.id}
                  payment={p}
                  accountVersion={accountVersion}
                  writeEnabled={writeEnabled && accountVersion !== undefined}
                  onChanged={refreshAll}
                />
              ))}
              {payments.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 2 }}>
                      لا توجد دفعات مسجّلة لهذا المزود بعد
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}

        <Divider sx={{ my: 2 }} />
        <Typography variant="h6" sx={{ mb: 1 }}>
          قيود الدفتر
        </Typography>
        {ledgerQuery.isLoading ? (
          <CircularProgress size={24} />
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>التاريخ</TableCell>
                <TableCell>النوع</TableCell>
                <TableCell align="right">المبلغ</TableCell>
                <TableCell align="right">الرصيد بعد</TableCell>
                <TableCell>الوصف</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(ledgerQuery.data?.content || ledgerQuery.data?.items || []).map((t) => (
                <TableRow key={t.id}>
                  <TableCell>{t.transactionDate || t.createdAt}</TableCell>
                  <TableCell>
                    <Chip size="small" label={t.transactionType} color={t.transactionType === 'CREDIT' ? 'success' : 'default'} />
                  </TableCell>
                  <TableCell align="right">{formatCurrency(t.amount)}</TableCell>
                  <TableCell align="right">{formatCurrency(t.balanceAfter)}</TableCell>
                  <TableCell>{t.description}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Box>

      <NewProviderPaymentDialog
        providerId={providerId}
        open={newPaymentOpen}
        onClose={() => setNewPaymentOpen(false)}
        onCreated={() => {
          setNewPaymentOpen(false);
          refreshAll();
        }}
      />
    </Drawer>
  );
}
