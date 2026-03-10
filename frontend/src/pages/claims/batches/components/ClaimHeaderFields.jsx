import React from 'react';
import {
    Grid, Typography, Autocomplete, TextField, Stack, FormControlLabel, Checkbox
} from '@mui/material';

const inlineSx = {
    '& .MuiInputBase-root': { fontSize: '0.8rem' }
};

export const ClaimHeaderFields = ({
    member,
    setMember,
    memberOptions,
    searchingMember,
    setMemberInput,
    memberRef,
    diagnosis,
    setDiagnosis,
    primaryCategoryCode,
    setPrimaryCategoryCode,
    setManualCategoryEnabled,
    rootCategories,
    refetchAllLinesCoverage,
    preAuthResults,
    searchingPreAuth,
    preAuthId,
    setPreAuthId,
    setPreAuthSearch,
    complaint,
    setComplaint,
    setIsDirty,
    t
}) => {
    return (
        <Grid container spacing={2}>
            {/* Row 1: Patient, Diagnosis, Context */}
            <Grid size={{ xs: 12, sm: 3 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                    {t('claimEntry.patient')} <Typography component="span" color="error.main">*</Typography>
                </Typography>
                <Autocomplete size="small" fullWidth options={memberOptions} loading={searchingMember}
                    value={member}
                    onChange={(_, v) => { 
                        setMember(v); 
                        setIsDirty(true); 
                        if (v?.id) {
                            // Fetch all lines coverage for THE NEW MEMBER
                            refetchAllLinesCoverage(primaryCategoryCode);
                        }
                    }}
                    onInputChange={(_, v) => setMemberInput(v)}
                    getOptionLabel={o => `${o.fullName || ''} · ${o.cardNumber || o.nationalNumber || ''}`}
                    isOptionEqualToValue={(o, v) => o.id === v?.id}
                    renderInput={params => (
                        <TextField {...params} inputRef={memberRef} variant="standard" autoFocus
                            placeholder={t('claimEntry.searchPatient')} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                    )}
                />
            </Grid>

            <Grid size={{ xs: 12, sm: 3 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                    {t('claimEntry.diagnosis')}
                </Typography>
                <TextField fullWidth size="small" variant="standard" value={diagnosis}
                    onChange={e => { setDiagnosis(e.target.value); setIsDirty(true); }} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 900, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                    سياق التغطية (Context)
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                        control={
                            <Checkbox
                                size="small"
                                checked={primaryCategoryCode === 'CAT-OUTPAT'}
                                onChange={(e) => {
                                    const checked = e.target.checked;
                                    const newCode = checked ? 'CAT-OUTPAT' : '';
                                    setPrimaryCategoryCode(newCode);
                                    setManualCategoryEnabled(true);
                                    setIsDirty(true);
                                    refetchAllLinesCoverage(newCode);
                                }}
                            />
                        }
                        label={<Typography sx={{ fontSize: '0.75rem', fontWeight: 900, whiteSpace: 'nowrap' }}>عيادات خارجية</Typography>}
                    />
                    {primaryCategoryCode !== 'CAT-OUTPAT' && (
                        <Autocomplete
                            size="small"
                            sx={{ flexGrow: 1 }}
                            options={rootCategories?.filter(c => c.code !== 'CAT-OUTPAT') || []}
                            getOptionLabel={(o) => o.name || o.nameAr || ''}
                            value={rootCategories?.find(c => c.code === primaryCategoryCode) || null}
                            onChange={(_, v) => {
                                const newCode = v?.code || '';
                                setPrimaryCategoryCode(newCode);
                                setManualCategoryEnabled(!!v);
                                setIsDirty(true);
                                refetchAllLinesCoverage(newCode);
                            }}
                            renderInput={(params) => (
                                <TextField {...params} variant="standard" placeholder="اختر التصنيف..."
                                    sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                            )}
                        />
                    )}
                </Stack>
            </Grid>

            {/* Row 2: Pre-approval, Complaint, Notes */}
            <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
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
                    getOptionLabel={o => `[${o.preAuthNumber || o.referenceNumber || o.id}] ${o.medicalServiceName || ''}`}
                    renderInput={params => (
                        <TextField {...params} variant="standard"
                            placeholder="ابحث برقم الموافقة..."
                            sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
                        />
                    )}
                    noOptionsText="لا توجد موافقات مسبقة"
                />
            </Grid>

            <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                    {t('claimEntry.complaint')}
                </Typography>
                <TextField fullWidth size="small" variant="standard" value={complaint}
                    onChange={e => { setComplaint(e.target.value); setIsDirty(true); }} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
            </Grid>
        </Grid>
    );
};
