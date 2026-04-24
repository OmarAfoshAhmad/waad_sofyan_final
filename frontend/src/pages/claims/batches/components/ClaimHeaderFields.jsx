import React from 'react';
import {
    Typography, Autocomplete, TextField, Stack, FormControlLabel, Checkbox, Box, Chip, MenuItem, Alert, Button
} from '@mui/material';
import { alpha } from '@mui/material/styles';

const inlineSx = {
    '& .MuiInputBase-root': { fontSize: '0.8rem' }
};

const FULL_COVERAGE_OPTION = { id: -1, code: 'FULL_COVERAGE', name: '✦ تغطية كاملة' };

export const ClaimHeaderFields = ({
    member,
    setMember,
    memberOptions,
    searchingMember,
    memberSearchType,
    setMemberSearchType,
    memberSearchValidationError,
    memberSearchPlaceholder,
    memberSearchError,
    onRetryMemberSearch,
    setMemberInput,
    memberRef,
    diagnosis,
    setDiagnosis,
    primaryCategoryCode,
    setPrimaryCategoryCode,
    setManualCategoryEnabled,
    rootCategories,
    refetchAllLinesCoverage,
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
    t
}) => {
    return (
        <Box sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '1fr 1fr 1fr' },
            gap: 3,
            width: '100%'
        }}>
            {/* Column 1: Patient & Pre-approval */}
            <Stack spacing={2}>
                    <Box>
                        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                            {t('claimEntry.patient')} <Typography component="span" color="error.main">*</Typography>
                        </Typography>
                        <TextField
                            select
                            fullWidth
                            size="small"
                            variant="standard"
                            value={memberSearchType}
                            onChange={(e) => setMemberSearchType(e.target.value)}
                            sx={{ ...inlineSx, mb: 1 }}
                        >
                            <MenuItem value="BY_NAME">بحث بالاسم</MenuItem>
                            <MenuItem value="BY_ID">بحث بالمعرف</MenuItem>
                            <MenuItem value="BY_BARCODE">بحث بالباركود</MenuItem>
                        </TextField>
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
                                    refetchAllLinesCoverage(primaryCategoryCode, linesRef.current);
                                }
                            }}
                            onInputChange={(_, v) => setMemberInput(v)}
                            filterOptions={(x) => x}
                            getOptionLabel={o => `${o.fullName || ''} · ${o.cardNumber || o.nationalNumber || ''}`}
                            isOptionEqualToValue={(o, v) => o.id === v?.id}
                            renderInput={params => (
                                <TextField {...params} inputRef={memberRef} variant="standard" autoFocus
                                    placeholder={memberSearchPlaceholder}
                                    error={!!memberSearchValidationError}
                                    helperText={memberSearchValidationError || ' '}
                                    sx={inlineSx} />
                            )}
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
                    <Box>
                        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                            رقم الموافقة المسبقة (PA Selection)
                        </Typography>
                        <Autocomplete
                            size="small"
                            fullWidth
                            options={preAuthResults?.items || []}
                            loading={searchingPreAuth}
                            value={preAuthResults?.items?.find(pa => pa.id === parseInt(preAuthId)) || null}
                            onInputChange={(_, v) => setPreAuthSearch(v)}
                            onChange={(_, v) => {
                                setPreAuthId(v?.id || '');
                                setIsDirty(true);
                            }}
                            getOptionLabel={o => `[${o.preAuthNumber || o.id}] ${o.medicalServiceName || ''}`}
                            renderInput={params => (
                                <TextField {...params} variant="standard"
                                    placeholder="ابحث برقم الموافقة..."
                                    sx={inlineSx}
                                />
                            )}
                            noOptionsText="لا توجد موافقات مسبقة"
                        />
                    </Box>
            </Stack>

            {/* Column 2: Diagnosis & Service Date */}
            <Stack spacing={2}>
                    <Box>
                        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                            {t('claimEntry.diagnosis')}
                        </Typography>
                        <TextField fullWidth size="small" variant="standard" value={diagnosis}
                            placeholder="التشخيص الطبي..."
                            onChange={e => { setDiagnosis(e.target.value); setIsDirty(true); }} 
                            sx={inlineSx} 
                        />
                    </Box>
                    <Box>
                        <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                            تاريخ الخدمة
                        </Typography>
                        <TextField fullWidth size="small" variant="standard" type="date"
                            value={serviceDate || ''}
                            onChange={e => { setServiceDate(e.target.value); setIsDirty(true); }}
                            sx={inlineSx}
                        />
                    </Box>
            </Stack>

            {/* Column 3: Coverage Context & Annual Summary */}
            <Stack spacing={1.5}>
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
                                            const newCode = checked ? 'CAT-OP' : '';
                                            setPrimaryCategoryCode(newCode);
                                            setManualCategoryEnabled(true);
                                            setIsDirty(true);
                                            refetchAllLinesCoverage(newCode, linesRef.current);
                                        }}
                                    />
                                }
                                label={<Typography sx={{ fontSize: '0.75rem', fontWeight: 500 }}>عيادات خارجية</Typography>}
                            />
                            {primaryCategoryCode !== 'CAT-OP' && (
                                <Autocomplete
                                    size="small"
                                    sx={{ flexGrow: 1 }}
                                    options={[
                                        ...(rootCategories?.filter(c => c.code !== 'CAT-OP') || []),
                                        FULL_COVERAGE_OPTION
                                    ]}
                                    getOptionLabel={(o) => o.name || o.nameAr || ''}
                                    value={
                                        primaryCategoryCode === 'FULL_COVERAGE'
                                            ? FULL_COVERAGE_OPTION
                                            : (rootCategories?.find(c => c.code === primaryCategoryCode) || null)
                                    }
                                    isOptionEqualToValue={(o, v) => o.code === v?.code}
                                    onChange={(_, v) => {
                                        const newCode = v?.code || '';
                                        setPrimaryCategoryCode(newCode);
                                        setManualCategoryEnabled(!!v);
                                        setIsDirty(true);
                                        refetchAllLinesCoverage(newCode, linesRef.current);
                                    }}
                                    renderOption={(props, option) => (
                                        <li {...props} key={option.code}>
                                            <Typography sx={{
                                                fontSize: '0.8rem',
                                                fontWeight: option.code === 'FULL_COVERAGE' ? 700 : 400,
                                                color: option.code === 'FULL_COVERAGE' ? '#00695c' : 'inherit'
                                            }}>
                                                {option.name || option.nameAr}
                                            </Typography>
                                        </li>
                                    )}
                                    renderInput={(params) => (
                                        <TextField {...params} variant="standard" placeholder="اختر التصنيف..."
                                            sx={{
                                                ...inlineSx,
                                                ...(primaryCategoryCode === 'FULL_COVERAGE' && {
                                                    '& .MuiInputBase-input': { color: '#00695c', fontWeight: 700 }
                                                })
                                            }}
                                        />
                                    )}
                                />
                            )}
                        </Stack>
                    </Box>

                    <Box sx={{ 
                        p: 1.5, 
                        borderRadius: 1, 
                        bgcolor: alpha('#00867d', 0.05),
                        border: '1px solid',
                        borderColor: alpha('#00867d', 0.1),
                        minHeight: '65px',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                    }}>
                        <Typography variant="caption" sx={{ color: '#004d40', fontWeight: 500, display: 'block', mb: 0.5, fontSize: '0.75rem' }}>
                            التغطية السنوية المتبقية
                        </Typography>
                        {loadingSummary ? (
                            <Typography variant="body2" sx={{ fontSize: '0.8rem', fontWeight: 400, color: 'text.disabled' }}>جاري التحميل...</Typography>
                        ) : financialSummary ? (
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: '#00695c', fontSize: '1.05rem' }}>
                                    {financialSummary.remainingCoverage?.toFixed(2) || '0.00'} د.ل
                                </Typography>
                                <Chip 
                                    size="small" 
                                    label={`${financialSummary.utilizationPercent?.toFixed(1) || '0.0'}% مستهلك`}
                                    color={financialSummary.utilizationPercent > 80 ? 'error' : 'success'}
                                    sx={{ height: '1.2rem', fontSize: '0.75rem', fontWeight: 500 }}
                                />
                            </Stack>
                        ) : (
                            <Typography variant="body2" sx={{ fontSize: '0.8rem', fontWeight: 400, color: 'text.disabled' }}>— اختر مستفيداً —</Typography>
                        )}
                    </Box>
            </Stack>
        </Box>
    );
};




