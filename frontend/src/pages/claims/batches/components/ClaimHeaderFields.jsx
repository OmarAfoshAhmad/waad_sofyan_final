import React from 'react';
import {
    Typography, Autocomplete, TextField, Stack, FormControlLabel, Checkbox, Box, Chip, MenuItem, Alert, Button
} from '@mui/material';
import { alpha } from '@mui/material/styles';

const inlineSx = {
    '& .MuiInputBase-root': { fontSize: '0.9rem' },
    '& .MuiInput-input': { fontSize: '0.9rem' }
};

const FULL_COVERAGE_OPTION = { id: -1, code: 'FULL_COVERAGE', name: '✦ تغطية كاملة' };

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
    primaryCategoryCode,
    setPrimaryCategoryCode,
    fullCoverage,
    setFullCoverage,
    setManualCategoryEnabled,
    rootCategories,
    onRefetchAll,
    linesRef,
    preAuthResults,
    searchingPreAuth,
    preAuthId,
    setPreAuthId,
    setPreAuthSearch,
    serviceDate,
    setServiceDate,
    setIsDirty,
    financialSummary,
    loadingSummary,
    t,
    showValidationErrors
}) => {
    return (
        <Box sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1.2fr 1fr 0.8fr 1fr' },
            gap: 2,
            alignItems: 'flex-start',
            width: '100%',
            mt: 2
        }}>
            {/* Column 1: Patient */}
            <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                    {t('claimEntry.patient')} <Typography component="span" color="error.main">*</Typography>
                </Typography>
                
                <Autocomplete
                    size="small"
                    fullWidth
                    options={memberOptions}
                    loading={searchingMember}
                    value={member}
                    onChange={(_, v) => {
                        setMember(v);
                        setIsDirty(true);
                        if (v?.id) {
                            onRefetchAll(primaryCategoryCode, fullCoverage);
                        }
                    }}
                    onInputChange={(_, v) => setMemberInput(v)}
                    filterOptions={(x) => x}
                    getOptionLabel={o => `${o.fullName || ''} · ${o.cardNumber || o.nationalNumber || ''}`}
                    isOptionEqualToValue={(o, v) => o.id === v?.id}
                    renderInput={params => (
                        <TextField {...params} inputRef={memberRef} variant="standard"
                            placeholder="ابحث بالاسم، المعرف، أو رقم البطاقة..."
                            error={!!memberSearchError || (showValidationErrors && !member)}
                            helperText={showValidationErrors && !member ? 'يرجى اختيار المستفيد' : null}
                            sx={inlineSx} />
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
                    {t('claimEntry.diagnosis')} <Typography component="span" color="error.main">*</Typography>
                </Typography>
                <TextField fullWidth size="small" variant="standard" value={diagnosis}
                    placeholder="التشخيص الطبي..."
                    onChange={e => { setDiagnosis(e.target.value); setIsDirty(true); }}
                    error={showValidationErrors && !diagnosis?.trim()}
                    sx={inlineSx}
                />
            </Box>

            {/* Column 3: Service Date */}
            <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                    تاريخ الخدمة <Typography component="span" color="error.main">*</Typography>
                </Typography>
                <TextField fullWidth size="small" variant="standard" type="date"
                    value={serviceDate || ''}
                    onChange={e => { setServiceDate(e.target.value); setIsDirty(true); }}
                    error={showValidationErrors && !serviceDate}
                    sx={{ 
                        ...inlineSx,
                        '& input': { fontSize: '0.9rem' } // Force date input font size
                    }}
                />
            </Box>

            {/* Column 4: Coverage Context */}
            <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                    سياق التغطية (Context)
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                        control={
                            <Checkbox
                                size="small"
                                checked={primaryCategoryCode === 'CAT-OP'}
                                onChange={(e) => {
                                    const checked = e.target.checked;
                                    const newCode = checked ? 'CAT-OP' : 'CAT-IP';
                                    setPrimaryCategoryCode(newCode);
                                    if (checked) setFullCoverage(false);
                                    setIsDirty(true);
                                    onRefetchAll(newCode, checked ? false : fullCoverage);
                                }}
                                sx={{ p: 0.5 }}
                            />
                        }
                        label={<Typography sx={{ fontSize: '0.75rem', fontWeight: 500 }}>عيادات خارجية</Typography>}
                    />


                    {primaryCategoryCode !== 'CAT-OP' && (
                        <Autocomplete
                            size="small"
                            sx={{ flexGrow: 1 }}
                            options={[
                                // ✅ Ensure Housing (CAT-IP) comes FIRST, then Full Coverage
                                ...(rootCategories?.filter(c => c.code === 'CAT-IP') || []),
                                FULL_COVERAGE_OPTION
                            ].filter(Boolean)}
                            getOptionLabel={(o) => o.label || o.name || o.nameAr || ''}
                            value={
                                fullCoverage
                                    ? FULL_COVERAGE_OPTION
                                    : (rootCategories?.find(c => c.code === primaryCategoryCode) || null)
                            }
                            isOptionEqualToValue={(o, v) => o?.code === v?.code}
                            onChange={(_, v) => {
                                const isFull = v?.code === 'FULL_COVERAGE';
                                const newCode = isFull ? 'CAT-IP' : (v?.code || '');

                                setFullCoverage(isFull);
                                setPrimaryCategoryCode(newCode);
                                setManualCategoryEnabled(!!v);
                                setIsDirty(true);

                                // ✅ Fix: Only pass 2 arguments to the callback wrapper
                                onRefetchAll?.(newCode, isFull);
                            }}
                            renderOption={(props, option) => (
                                <li {...props} key={option.code}>
                                    <Typography sx={{
                                        fontSize: '0.8rem',
                                        fontWeight: option.code === 'FULL_COVERAGE' ? 700 : 400,
                                        color: option.code === 'FULL_COVERAGE' ? 'success.main' : 'inherit'
                                    }}>
                                        {option.name || option.nameAr || option.label}
                                    </Typography>
                                </li>
                            )}
                            renderInput={(params) => (
                                <TextField {...params} variant="standard" placeholder="اختر التصنيف..."
                                    sx={{
                                        ...inlineSx,
                                        ...(fullCoverage && {
                                            '& .MuiInputBase-input': { color: '#00695c', fontWeight: 700 }
                                        })
                                    }}
                                />
                            )}
                        />
                    )}
                </Stack>
            </Box>
        </Box >
    );
};




