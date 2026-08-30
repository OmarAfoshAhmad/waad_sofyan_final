import { Typography, Autocomplete, TextField, Stack, FormControlLabel, Checkbox, Box, Alert, Button, Chip, Select, MenuItem } from '@mui/material';
import dayjs from 'dayjs';
import DatePicker from 'components/common/SystemDatePicker';

const inlineSx = {
  '& .MuiInputBase-root': { fontSize: '0.9rem' },
  '& .MuiInput-input': { fontSize: '0.9rem' }
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
  doctorName,
  setDoctorName,
  encounterType,
  setEncounterType,
  fullCoverage,
  setFullCoverage,
  onRefetchAll,
  preAuthResults,
  searchingPreAuth,
  preAuthId,
  setPreAuthId,
  setPreAuthSearch,
  serviceDate,
  setServiceDate,
  setIsDirty,
  financialSummary,
  currentCompanyCommitment = 0,
  editingApprovedAmount = 0,
  t,
  showValidationErrors
}) => {
  const isOutpatient = encounterType === 'OUTPATIENT' && !fullCoverage;
  const alternativeContext = fullCoverage ? 'FULL_COVERAGE' : 'INPATIENT';
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
        gridTemplateColumns: { xs: '1fr', md: '1.05fr 1.15fr 0.72fr minmax(430px, 1.55fr)' },
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
            setPreAuthSearch('');
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

      {/* Column 2: Diagnosis */}
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
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mt: 1.25, mb: 0.5, fontSize: '0.75rem' }}>
          اسم الطبيب{' '}
          <Typography component="span" color="error.main">*</Typography>
        </Typography>
        <TextField
          fullWidth
          size="small"
          variant="standard"
          value={doctorName}
          placeholder="اسم الطبيب المعالج..."
          onChange={(e) => {
            setDoctorName(e.target.value);
            setIsDirty(true);
          }}
          error={showValidationErrors && !doctorName?.trim()}
          sx={inlineSx}
        />
      </Box>

      {/* Column 3: Service Date */}
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
            setPreAuthSearch('');
            setServiceDate(value?.isValid() ? value.format('YYYY-MM-DD') : '');
            setIsDirty(true);
          }}
          slotProps={{
            textField: {
              fullWidth: true,
              size: 'small',
              variant: 'standard',
              error: showValidationErrors && !serviceDate,
              sx: { ...inlineSx, '& input': { fontSize: '0.9rem' } }
            }
          }}
        />
      </Box>

      {/* Column 4: Coverage Context */}
      <Box sx={{ position: 'relative', minHeight: 58 }}>
        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
          سياق المطالبة
        </Typography>
        <Stack
          direction="row"
          spacing={1.1}
          alignItems="flex-start"
          flexWrap="nowrap"
          justifyContent="space-between"
          sx={{
            whiteSpace: 'nowrap',
            minWidth: 0,
            width: '100%',
            '& .MuiFormControlLabel-root': { mr: 0, ml: 0.25 },
            '& .MuiFormControlLabel-label': { lineHeight: 1 }
          }}
        >
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={isOutpatient}
                onChange={(e) => {
                  const newEncounterType = e.target.checked ? 'OUTPATIENT' : 'INPATIENT';
                  setFullCoverage(false);
                  setEncounterType(newEncounterType);
                  setIsDirty(true);
                  onRefetchAll(newEncounterType, false);
                }}
                sx={{ p: 0.5 }}
              />
            }
            label={<Typography sx={{ fontSize: '0.75rem', fontWeight: 600 }}>عيادات خارجية</Typography>}
          />
          {!isOutpatient && (
            <Select
              size="small"
              variant="standard"
              value={alternativeContext}
              onChange={(e) => {
                const isFullCoverage = e.target.value === 'FULL_COVERAGE';
                const newEncounterType = isFullCoverage ? 'OUTPATIENT' : e.target.value;
                setEncounterType(newEncounterType);
                setFullCoverage(isFullCoverage);
                setIsDirty(true);
                onRefetchAll(newEncounterType, isFullCoverage);
              }}
              sx={{ minWidth: 96, fontSize: '0.72rem', mt: 0 }}
            >
              <MenuItem value="INPATIENT">إيواء</MenuItem>
              <MenuItem value="FULL_COVERAGE">تغطية كاملة</MenuItem>
            </Select>
          )}
          {hasCeiling && amountLimit > 0 && (
            <Stack
              direction="column"
              spacing={0.35}
              alignItems="stretch"
              flexWrap="nowrap"
              sx={{ flexShrink: 0 }}
            >
              <Chip
                size="small"
                variant="outlined"
                label={`السقف العام: ${amountLimit.toFixed(2)} د.ل`}
                sx={{ height: 24, '& .MuiChip-label': { px: 0.7, fontSize: '0.68rem' } }}
              />
              <Chip
                size="small"
                color={remainingAmount <= 0 ? 'error' : 'success'}
                variant="outlined"
                label={`المتاح لالتزام جديد: ${remainingAmount.toFixed(2)} د.ل`}
                sx={{ height: 24, '& .MuiChip-label': { px: 0.7, fontSize: '0.68rem' } }}
              />
            </Stack>
          )}
        </Stack>
        <Autocomplete
          size="small"
          sx={{ mt: 1 }}
          options={Array.isArray(preAuthResults) ? preAuthResults : []}
          loading={searchingPreAuth}
          value={(Array.isArray(preAuthResults) ? preAuthResults : []).find((item) => String(item.id) === String(preAuthId)) || null}
          onChange={(_, value) => {
            setPreAuthId(value?.id || '');
            setIsDirty(true);
          }}
          onInputChange={(_, value) => setPreAuthSearch(value)}
          getOptionLabel={(item) => `${item.number || item.id} · ${item.serviceName || 'خدمة معتمدة'} · ${item.approvedAmount ?? '-'} د.ل`}
          isOptionEqualToValue={(option, value) => option.id === value?.id}
          renderInput={(params) => (
            <TextField {...params} variant="standard" placeholder="ربط موافقة مسبقة صالحة (اختياري)" sx={inlineSx} />
          )}
          noOptionsText="لا توجد موافقة صالحة لهذا المستفيد والمزود في تاريخ الخدمة"
        />
      </Box>
    </Box>
  );
};

