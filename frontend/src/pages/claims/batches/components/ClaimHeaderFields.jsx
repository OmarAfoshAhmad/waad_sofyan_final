import { Typography, Autocomplete, TextField, Stack, Box, Alert, Button, Chip, Select, MenuItem } from '@mui/material';
import dayjs from 'dayjs';
import DatePicker from 'components/common/SystemDatePicker';
import { resolveClaimContextSelection } from '../claim-context.mjs';

const inlineSx = {
  '& .MuiInputBase-root': { fontSize: '0.9rem' },
  '& .MuiInput-input': { fontSize: '0.9rem' }
};

// The date field sat lower than every other field in the row. The cause is its
// calendar button: a default IconButton is 40px tall, so the input row it lives
// in is taller than a plain standard input and its underline lands further
// down. Shrinking the button to the height of the text it sits beside puts the
// four underlines back on one line -- the field is not moved, the button stops
// inflating it.
const dateFieldSx = {
  ...inlineSx,
  '& input': { fontSize: '0.9rem' },
  '& .MuiInputAdornment-root': { ml: 0, mr: -0.5 },
  '& .MuiIconButton-root': { p: 0.25 },
  '& .MuiIconButton-root .MuiSvgIcon-root': { fontSize: '1.15rem' }
};

export const ClaimHeaderFields = ({
  member,
  setMember,
  memberOptions,
  searchingMember,
  memberSearchError,
  onRetryMemberSearch,
  setMemberInput,
  memberRef,
  diagnosis,
  setDiagnosis,
  encounterType,
  claimContextCode,
  claimContexts = [],
  setClaimContextCode,
  setEncounterType,
  fullCoverage,
  setFullCoverage,
  onRefetchAll,
  // The pre-auth picker itself moved to ClaimAdditionalDetails, but the setter
  // stays here: changing member or service date must still clear a linked
  // approval, and both of those fields live in this row.
  setPreAuthId,
  serviceDate,
  setServiceDate,
  setIsDirty,
  financialSummary,
  currentCompanyCommitment = 0,
  editingApprovedAmount = 0,
  t,
  showValidationErrors
}) => {
  // Header badges are policy-wide totals. Service/category ceilings belong only
  // in the corresponding claim-line column.
  const amountLimit = Number(financialSummary?.annualLimit || 0);
  // reservableAvailable, not the remaining-consumption figure: this badge sits
  // on a screen where a NEW commitment is being entered, and money already
  // held by an approved pre-authorization is not available to commit again.
  // Null, not zero, whenever there is no ceiling to report -- unlimited, not
  // configured, or a failed read. The badges below are hidden in that case
  // rather than showing a fabricated 0 د.ل to someone entering a claim.
  const hasCeiling =
    financialSummary?.reservableAvailable !== null && financialSummary?.reservableAvailable !== undefined;
  const persistedAvailable = hasCeiling ? Number(financialSummary.reservableAvailable) : 0;
  // The persisted summary includes an existing claim being edited, while the
  // coverage engine excludes that claim before recalculation. Add its old
  // approved amount back, then subtract the current draft commitment so the
  // badge and engine describe the same state.
  const availableBeforeDraft = Math.min(amountLimit, persistedAvailable + Number(editingApprovedAmount || 0));
  const remainingAmount = availableBeforeDraft - Number(currentCompanyCommitment || 0);
  return (
    <Box
      sx={{
        display: 'grid',
        // Order: who, when, what was treated, under which context, and the
        // ceilings that context resolves to. The context sits directly beside
        // the figures it governs, so the cause and the number it changes are
        // read together rather than at opposite ends of the row.
        //
        // Every column starts on the same line -- labels aligned, inputs
        // aligned -- because the row is top-aligned; the ceilings column is
        // taller than an input and must not drag the fields down with it.
        //
        // One row, each track sized to what it actually holds: a member name
        // with a card number is long, a date is a fixed ten characters, a
        // context is one of a short list, a diagnosis is free text. The
        // ceilings close the row on `auto` -- they are read, not filled, and
        // they take exactly their own width, collapsing when absent.
        //
        // minmax(0, …) on every flexible track is what keeps a long member name
        // from widening its column and pushing the rest of the row out of
        // alignment; without it a grid track refuses to shrink below its
        // content.
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, minmax(0, 1fr))',
          md: 'minmax(0, 1.3fr) 150px minmax(0, 1.3fr) minmax(0, 0.95fr) auto'
        },
        gap: 2,
        alignItems: 'flex-start',
        width: '100%',
        mt: 2
      }}
    >
      {/* Column 1: Patient */}
      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          {t('claimEntry.patient')}{' '}
          <Typography component="span" color="error.main">
            *
          </Typography>
        </Typography>

        <Autocomplete
          size="small"
          fullWidth
          options={memberOptions}
          loading={searchingMember}
          value={member}
          onChange={(_, v) => {
            // A pre-authorization is eligible for one member in one dated
            // context. Keeping its id after changing the member leaves a
            // hidden stale value because the Autocomplete can no longer
            // render it, while the submit payload would still send it.
            setPreAuthId('');
            setMember(v);
            setIsDirty(true);
            if (v?.id) {
              onRefetchAll(encounterType, fullCoverage);
            }
          }}
          onInputChange={(_, v) => setMemberInput(v)}
          filterOptions={(x) => x}
          getOptionLabel={(o) => `${o.fullName || ''} · ${o.cardNumber || o.nationalNumber || ''}`}
          isOptionEqualToValue={(o, v) => o.id === v?.id}
          renderInput={(params) => (
            <TextField
              {...params}
              inputRef={memberRef}
              variant="standard"
              placeholder="ابحث بالاسم، المعرف، أو رقم البطاقة..."
              error={!!memberSearchError || (showValidationErrors && !member)}
              helperText={showValidationErrors && !member ? 'يرجى اختيار المستفيد' : null}
              sx={inlineSx}
            />
          )}
          noOptionsText="لا توجد نتائج لمطابقة بحثك"
        />
        {memberSearchError && (
          <Alert
            severity="error"
            sx={{ mt: 1, py: 0.5, '& .MuiAlert-message': { width: '100%' } }}
            action={
              <Button color="inherit" size="small" onClick={onRetryMemberSearch}>
                إعادة المحاولة
              </Button>
            }
          >
            فشل تحميل نتائج البحث. حاول مرة أخرى.
          </Alert>
        )}
      </Box>

      {/* Column 2: Service Date */}
      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          تاريخ الخدمة{' '}
          <Typography component="span" color="error.main">
            *
          </Typography>
        </Typography>
        <DatePicker
          value={serviceDate ? dayjs(serviceDate) : null}
          onChange={(value) => {
            setPreAuthId('');
            setServiceDate(value?.isValid() ? value.format('YYYY-MM-DD') : '');
            setIsDirty(true);
          }}
          slotProps={{
            textField: {
              fullWidth: true,
              size: 'small',
              variant: 'standard',
              error: showValidationErrors && !serviceDate,
              sx: dateFieldSx
            }
          }}
        />
      </Box>

      {/* Column 3: Clinical description */}
      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          {t('claimEntry.diagnosis')}{' '}
          <Typography component="span" color="error.main">
            *
          </Typography>
        </Typography>
        <TextField
          fullWidth
          size="small"
          variant="standard"
          value={diagnosis}
          placeholder="التشخيص الطبي..."
          onChange={(e) => {
            setDiagnosis(e.target.value);
            setIsDirty(true);
          }}
          error={showValidationErrors && !diagnosis?.trim()}
          sx={inlineSx}
        />
      </Box>

      {/* Column 4: Coverage Context -- immediately before the ceilings it
          governs, since changing it is what re-reads them. */}
      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          سياق المطالبة
        </Typography>
        <Select
          size="small"
          fullWidth
          variant="standard"
          value={claimContextCode}
          onChange={(e) => {
            const selection = resolveClaimContextSelection(claimContexts, e.target.value);
            if (!selection) return;
            setClaimContextCode(selection.claimContextCode);
            setEncounterType(selection.encounterType);
            setFullCoverage(selection.fullCoverage);
            setIsDirty(true);
            onRefetchAll(selection.encounterType, selection.fullCoverage, selection.claimContextCode);
          }}
          sx={{ ...inlineSx, fontSize: '0.9rem' }}
        >
          {claimContexts.map((context) => (
            <MenuItem key={context.code} value={context.code}>{context.nameAr}</MenuItem>
          ))}
        </Select>
      </Box>

      {/* Column 5: Ceilings -- last, and sized to their own content.
          They are a reading, not an input: nothing here is typed, so they end
          the row rather than interrupting the fields that are filled in order.
          The column takes only the width its two figures need, which is why the
          track is `auto` and collapses entirely when there is no ceiling to
          report. */}
      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          {hasCeiling && amountLimit > 0 ? 'السقف والمتاح' : ' '}
        </Typography>
        {hasCeiling && amountLimit > 0 && (
          <Stack direction="column" spacing={0.5} alignItems="stretch" flexWrap="nowrap">
            {/* These two carry the money a clerk decides against, and sat at
                0.68rem -- smaller than every form label around them. The
                remaining figure is filled rather than outlined because it is
                the one that moves as lines are entered, and tabular-nums stops
                the digits shifting while it does. */}
            <Chip
              size="small"
              variant="outlined"
              label={`السقف العام: ${amountLimit.toFixed(2)} د.ل`}
              sx={{
                height: 28,
                '& .MuiChip-label': {
                  px: 1, fontSize: '0.82rem', fontWeight: 600,
                  fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap'
                }
              }}
            />
            <Chip
              size="small"
              color={remainingAmount <= 0 ? 'error' : 'success'}
              variant="filled"
              label={`المتاح لالتزام جديد: ${remainingAmount.toFixed(2)} د.ل`}
              sx={{
                height: 28,
                '& .MuiChip-label': {
                  px: 1, fontSize: '0.82rem', fontWeight: 700,
                  fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap'
                }
              }}
            />
          </Stack>
        )}
      </Box>
    </Box>
  );
};

