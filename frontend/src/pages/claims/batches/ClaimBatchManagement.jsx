/**
 * Claim Batch Management Page
 * Main dashboard for selecting Employer -> Provider -> Monthly Batch
 */

import { useState, useMemo, useCallback } from 'react';
import {
    Box,
    Grid,
    Card,
    CardContent,
    Typography,
    Button,
    Stack,
    Autocomplete,
    TextField,
    Avatar,
    IconButton,
    Divider,
    CircularProgress,
    Select,
    MenuItem,
    FormControl,
    Collapse,
    LinearProgress,
    alpha
} from '@mui/material';

import {
    Business as BusinessIcon,
    LocalHospital as LocalHospitalIcon,
    Folder as FolderIcon,
    Search as SearchIcon,
    Receipt as ReceiptIcon,
    ViewList as ViewListIcon,
    CheckCircle as CheckCircleIcon,
    Cancel as CancelIcon,
    Assessment as AssessmentIcon,
    ExpandMore as ExpandMoreIcon,
    ExpandLess as ExpandLessIcon,
    TrendingUp as TrendingUpIcon,
    People as PeopleIcon,
    AttachMoney as AttachMoneyIcon,
    Pending as PendingIcon
} from '@mui/icons-material';

import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

import employersService from 'services/api/employers.service';
import providersService from 'services/api/providers.service';
import providerContractsService from 'services/api/provider-contracts.service';
import claimsService from 'services/api/claims.service';
import claimBatchesService from 'services/api/claim-batches.service';
import useAuth from 'hooks/useAuth';
import { formatCurrency } from 'utils/currency-formatter';

// ===========================================
// CONSTANTS
// ===========================================

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const formatLYD = (amount) => {
    return formatCurrency(amount || 0);
};

// ===========================================
// HELPERS
// ===========================================

/**
 * Generates a unique batch code based on employer, provider and date
 * Format: [Symbol][YY]-[SERIAL] (e.g., LCC25-00032)
 * Strictly English to avoid RTL issues
 */
const generateBatchCode = (employer, provider, month, year) => {
    // 1. Get Employer Symbol (Code)
    const symbol = employer?.code || 'EMP';

    // 2. Year suffix
    const yy = String(year).substring(2);

    // 3. Serial - derived from IDs for demo stability
    const pid = provider?.id || 0;
    const serialNum = (parseInt(pid) * 11 + (month * 3)) % 100000;
    const serial = String(serialNum).padStart(5, '0');

    return `${symbol}${yy}-${serial}`;
};

// ===========================================
// SUB-COMPONENTS
// ===========================================

/**
 * Provider Card for Batch Dashboard
 */
/**
 * Provider Card for Batch Dashboard - Redesigned to match reference image (Phase 5.5)
 */
const ProviderBatchCard = ({ provider, selectedEmployer, onSelectBatch, filterMonth, filterYear }) => {

    const batchCode = useMemo(() => {
        if (provider.realBatch) return provider.realBatch.batchCode;
        return generateBatchCode(selectedEmployer, provider, filterMonth, filterYear);
    }, [selectedEmployer, provider, filterMonth, filterYear]);

    // Fetch actual stats using Claims Service Financial Summary endpoint
    const { data: summaryData } = useQuery({
        queryKey: ['batch-stats', selectedEmployer?.id, provider?.id, filterMonth, filterYear],
        queryFn: () => {
            if (!selectedEmployer?.id || !provider?.id || !filterMonth || !filterYear) return null;
            const lastDay = new Date(filterYear, filterMonth, 0).getDate();
            return claimsService.getFinancialSummary({
                employerId: selectedEmployer.id,
                providerId: provider.id,
                dateFrom: `${filterYear}-${String(filterMonth).padStart(2, '0')}-01`,
                dateTo: `${filterYear}-${String(filterMonth).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
            });
        },
        enabled: !!selectedEmployer?.id && !!provider?.id,
        refetchOnWindowFocus: true,
        refetchOnMount: 'always',
        staleTime: 0
    });

    // Map fetched stats or default to 0
    // patientShare = المطلوب - المعتمد للمزود - المرفوض
    // (المكوّن المفقود الذي يجعل الأرقام تبدو غير متطابقة إذا لم يُعرض)
    const safeAmount  = summaryData?.totalClaimsAmount  || 0;
    const safeCovered = summaryData?.totalApprovedAmount || 0;
    const safeRefused = summaryData?.totalRefusedAmount  || 0;
    const stats = {
        requestsCount: summaryData?.claimsCount || 0,
        amount:       safeAmount,
        covered:      safeCovered,
        refused:      safeRefused,
        patientShare: Math.max(0, safeAmount - safeCovered - safeRefused)
    };

    return (
        <Card
            dir="ltr"
            sx={{
                position: 'relative',
                overflow: 'hidden',
                borderLeft: '6px solid',
                borderLeftColor: '#76b880',
                borderRadius: '0.1875rem',
                transition: 'all 0.2s',
                boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
                '&:hover': {
                    transform: 'translateY(-6px)',
                    boxShadow: '0 12px 30px rgba(0,0,0,0.12)',
                    cursor: 'pointer'
                },
                border: '1px solid',
                borderColor: 'divider',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                bgcolor: '#ffffff'
            }}
            onClick={() => onSelectBatch(provider, filterMonth, filterYear)}
        >
            <CardContent sx={{ p: '1.25rem', flexGrow: 1, display: 'flex', flexDirection: 'column', '&:last-child': { pb: '1.25rem' } }}>

                {/* 1. Header Row (Month | Code) */}
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: '1.0rem' }}>
                    <Typography variant="caption" sx={{ color: '#888', fontWeight: 600 }}>
                        {MONTHS_AR[filterMonth - 1]} {filterYear}
                    </Typography>
                    <Typography
                        variant="caption"
                        dir="ltr"
                        sx={{ color: '#aaa', fontWeight: 400, letterSpacing: 0.5, fontFamily: 'monospace' }}
                    >
                        {batchCode}
                    </Typography>
                </Stack>

                {/* 2. Provider Name (Centered with Building Icon) */}
                <Box>
                    <Stack
                        direction="row"
                        alignItems="center"
                        justifyContent="center"
                        spacing={2}
                        sx={{ mb: '0.75rem' }}
                    >
                        <BusinessIcon sx={{ color: '#d0d0d0', fontSize: '0.9rem' }} />
                        <Typography
                            variant="subtitle1"
                            sx={{
                                fontWeight: 400,
                                color: '#444',
                                textAlign: 'center',
                                fontSize: '0.88rem',
                                lineHeight: 1.2
                            }}
                        >
                            {provider.name}
                        </Typography>
                    </Stack>
                    <Divider sx={{ mb: '1.0rem', opacity: 0.6 }} />
                </Box>

                {/* 3. Requests Green Banner */}
                <Box sx={{
                    bgcolor: '#f5f9f5',
                    borderRadius: '0.375rem',
                    border: '1.5px solid #7cb983',
                    py: 1,
                    px: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mb: '1.5rem',
                    width: '100%',
                    mx: 'auto',
                    minHeight: '3.125rem'
                }}>
                    <Stack direction="row" spacing={2} alignItems="center" sx={{ whiteSpace: 'nowrap' }}>
                        <ViewListIcon sx={{ color: '#7cb983', fontSize: '1.6rem' }} />
                        <Typography variant="h6" sx={{ color: '#7cb983', fontWeight: 400, fontSize: '1.1rem' }}>
                            {stats.requestsCount} مطالبة
                        </Typography>
                    </Stack>
                </Box>

                {/* 4. Action Bars (Financials) - Label Left, Value Right */}
                <Stack spacing={1.2} sx={{ mt: 'auto', width: '100%', mx: 'auto', pb: 0 }}>

                    {/* Amount Block */}
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: '#92c68e',
                        color: 'white',
                        borderRadius: '0.625rem',
                        px: '1.25rem',
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.78rem' }}>المبلغ المطلوب</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.85rem' }}>{stats.amount.toFixed(2)}</Typography>
                    </Box>

                    {/* Covered Block */}
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: '#67bc72',
                        color: 'white',
                        borderRadius: '0.625rem',
                        px: '1.25rem',
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Stack direction="row" alignItems="center" spacing={1.5}>
                            <CheckCircleIcon sx={{ fontSize: '1.1rem' }} />
                            <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.78rem' }}>المعتمد</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.85rem' }}>{stats.covered.toFixed(2)}</Typography>
                    </Box>

                    {/* Patient Share Block — حصة المريض (تُعرض فقط إذا كانت > 0) */}
                    {stats.patientShare > 0.004 && (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            bgcolor: '#e0923a',
                            color: 'white',
                            borderRadius: '0.625rem',
                            px: '1.25rem',
                            py: 0.6,
                            boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                        }}>
                            <Stack direction="row" alignItems="center" spacing={1.5}>
                                <PeopleIcon sx={{ fontSize: '1.1rem' }} />
                                <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.78rem' }}>حصة المريض</Typography>
                            </Stack>
                            <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.85rem' }}>{stats.patientShare.toFixed(2)}</Typography>
                        </Box>
                    )}

                    {/* Refused Block */}
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: '#e66a6a',
                        color: 'white',
                        borderRadius: '0.625rem',
                        px: '1.25rem',
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Stack direction="row" alignItems="center" spacing={1.5}>
                            <CancelIcon sx={{ fontSize: '1.1rem' }} />
                            <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.78rem' }}>المرفوض</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.85rem' }}>{stats.refused.toFixed(2)}</Typography>
                    </Box>

                </Stack>
            </CardContent>
        </Card>
    );
};

// ===========================================
// STATISTICS PANEL
// ===========================================

const StatKpiCard = ({ title, value, subtitle, gradient, icon: Icon }) => (
    <Card sx={{
        height: '100%',
        background: gradient,
        color: '#fff',
        borderRadius: '0.25rem',
        overflow: 'hidden',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        transition: 'transform 0.2s ease',
        '&:hover': { transform: 'translateY(-2px)' }
    }}>
        <CardContent sx={{ p: '0.75rem', '&:last-child': { pb: '0.75rem' } }}>
            <Stack spacing={0.5}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" sx={{ opacity: 0.9, fontWeight: 400, textTransform: 'uppercase', fontSize: '0.75rem' }}>
                        {title}
                    </Typography>
                    <Box sx={{ p: 0.5, borderRadius: 1, bgcolor: 'rgba(255,255,255,0.2)', display: 'flex' }}>
                        <Icon sx={{ fontSize: '1.0rem' }} />
                    </Box>
                </Stack>
                <Typography variant="h5" fontWeight={500}>{value}</Typography>
                {subtitle && <Typography variant="caption" sx={{ opacity: 0.8, fontSize: '0.75rem' }}>{subtitle}</Typography>}
            </Stack>
        </CardContent>
    </Card>
);

// ===========================================
// MAIN COMPONENT
// ===========================================

export default function ClaimBatchManagement() {
    const navigate = useNavigate();

    const [selectedEmployer, setSelectedEmployer] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterMonth, setFilterMonth] = useState(new Date().getMonth() + 1);
    const [filterYear, setFilterYear] = useState(new Date().getFullYear());
    const [showStats, setShowStats] = useState(false);

    // 1. Fetch Employers for Selector
    const { data: employers, isLoading: isLoadingEmployers } = useQuery({
        queryKey: ['employers-selector'],
        queryFn: () => employersService.getEmployerSelectors()
    });

    // 2. Fetch Providers Allowed for Selected Employer (Standard-Isolation)
    const { data: allowedProviders, isLoading: isLoadingProviders } = useQuery({
        queryKey: ['providers-by-employer', selectedEmployer?.id],
        queryFn: () => providersService.getByEmployer(selectedEmployer.id),
        enabled: !!selectedEmployer
    });

    // 3. Keep Fetching Active Provider Contracts for pricing enrichment
    const { data: providerContracts } = useQuery({
        queryKey: ['provider-contracts-active'],
        queryFn: async () => {
            const response = await providerContractsService.getProviderContracts({
                status: 'ACTIVE',
                size: 1000
            });
            return response.content || [];
        },
        enabled: !!selectedEmployer
    });

    // 4. Fetch Global Financial Stats (lazy - only when showStats=true)
    const { data: globalStats, isLoading: isLoadingStats } = useQuery({
        queryKey: ['batch-global-stats', selectedEmployer?.id, filterMonth, filterYear],
        queryFn: () => {
            const lastDay = new Date(filterYear, filterMonth, 0).getDate();
            return claimsService.getFinancialSummary({
                employerId: selectedEmployer?.id,
                dateFrom: `${filterYear}-${String(filterMonth).padStart(2, '0')}-01`,
                dateTo: `${filterYear}-${String(filterMonth).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
            });
        },
        enabled: !!selectedEmployer && showStats
    });

    // 5. Fetch actual batches for the current selection
    const { data: realBatches } = useQuery({
        queryKey: ['real-claim-batches', selectedEmployer?.id, filterMonth, filterYear],
        queryFn: () => claimBatchesService.list({ 
            employerId: selectedEmployer?.id, 
            month: filterMonth, 
            year: filterYear 
        }),
        enabled: !!selectedEmployer && !!filterMonth && !!filterYear
    });

    const { user } = useAuth();
    const isProviderUser = user?.userType === 'PROVIDER_STAFF';
    const userProviderId = user?.providerId;

    const filteredProviders = useMemo(() => {
        if (!allowedProviders || !selectedEmployer) return [];

        // Map contracts for quick lookup by provider ID
        const contractByProviderId = new Map();
        if (providerContracts) {
            providerContracts.forEach((c) => {
                const pId = c.provider?.id;
                if (pId) contractByProviderId.set(pId, c.id);
            });
        }
        // Map real batches by providerId for easy access
        const batchByProviderId = new Map();
        if (realBatches) {
            realBatches.forEach(b => batchByProviderId.set(b.providerId, b));
        }

        // Final enriched list
        let list = allowedProviders.map((p) => ({
            id: p.id,
            name: p.name,
            code: p.licenseNumber || p.code || p.id,
            city: p.city || 'المنطقة',
            contractId: contractByProviderId.get(p.id) || null,
            realBatch: batchByProviderId.get(p.id) || null
        }));

        // ROLE ISOLATION: If provider user, filter to ONLY their provider ID
        if (isProviderUser && userProviderId) {
            list = list.filter(p => p.id === userProviderId);
        }

        if (searchTerm) {
            return list.filter(p =>
                p.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                String(p.code)?.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        return list;
    }, [allowedProviders, providerContracts, selectedEmployer, searchTerm, isProviderUser, userProviderId, realBatches, filterMonth, filterYear]);

    const handleSelectBatch = (provider, month, year) => {
        // Navigate to batch detail view (the list shown in your reference image)
        navigate(`/claims/batches/detail?employerId=${selectedEmployer.id}&providerId=${provider.id}&month=${month}&year=${year}`);
    };

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', px: { xs: 2, sm: 3 } }}>
            {/* 🔹 PAGE HEADER 🔹 */}
            <ModernPageHeader
                title="نظام الدفعات للمطالبات"
                subtitle="أدخل وراجع المطالبات حسب جهة العمل ومقدم الخدمة (دفعات شهرية)"
                icon={ReceiptIcon}
                breadcrumbs={[
                    { label: 'الرئيسية', path: '/' },
                    { label: 'المطالبات والموافقات', path: '/claims' },
                    { label: 'نظام الدفعات' }
                ]}
                actions={
                    <Stack direction="row" spacing={2} alignItems="center">
                        {selectedEmployer && (
                            <Button
                                variant={showStats ? 'contained' : 'outlined'}
                                color="secondary"
                                size="small"
                                startIcon={<AssessmentIcon />}
                                endIcon={showStats ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                onClick={() => setShowStats(v => !v)}
                                sx={{ borderRadius: '0.25rem', fontWeight: 400 }}
                            >
                                {showStats ? 'إخفاء الإحصائيات' : 'عرض الإحصائيات'}
                            </Button>
                        )}
                        {/* Employer Selector Directly in Header for context */}
                        <Autocomplete
                            size="small"
                            sx={{ width: '18.75rem', bgcolor: 'background.paper', borderRadius: 1 }}
                            options={employers || []}
                            getOptionLabel={(option) => `${option.label || ''} (${option.code || option.id || ''})`}
                            value={selectedEmployer}
                            onChange={(_, newValue) => setSelectedEmployer(newValue)}
                            loading={isLoadingEmployers}
                            renderInput={(params) => (
                                <TextField
                                    {...params}
                                    placeholder="اختر جهة العمل للبدء..."
                                    variant="outlined"
                                    InputProps={{
                                        ...params.InputProps,
                                        startAdornment: (
                                            <Stack direction="row" alignItems="center" sx={{ pl: 0.5 }}>
                                                <BusinessIcon color="primary" sx={{ fontSize: '1.2rem' }} />
                                                {params.InputProps.startAdornment}
                                            </Stack>
                                        )
                                    }}
                                />
                            )}
                        />
                    </Stack>
                }
            />

            {/* 🔹 STATISTICS PANEL (Collapsible) 🔹 */}
            <Collapse in={showStats && !!selectedEmployer} timeout="auto">
                <Box sx={{ mb: '1.0rem', mt: -1 }}>
                    {isLoadingStats ? (
                        <Box sx={{ py: '1.0rem' }}><LinearProgress /></Box>
                    ) : (
                        <Grid container spacing={2}>
                            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                                <StatKpiCard
                                    title="إجمالي المطالبات"
                                    value={globalStats?.claimsCount || 0}
                                    subtitle={`لشهر ${MONTHS_AR[filterMonth - 1]} ${filterYear}`}
                                    gradient="linear-gradient(135deg, #0d9488 0%, #14b8a6 100%)"
                                    icon={ReceiptIcon}
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                                <StatKpiCard
                                    title="المبلغ المطلوب"
                                    value={formatLYD(globalStats?.totalClaimsAmount)}
                                    subtitle="إجمالي مبالغ المطالبات"
                                    gradient="linear-gradient(135deg, #0891b2 0%, #38bdf8 100%)"
                                    icon={AttachMoneyIcon}
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                                <StatKpiCard
                                    title="المعتمد"
                                    value={formatLYD(globalStats?.totalApprovedAmount)}
                                    subtitle="إجمالي المبالغ المعتمدة"
                                    gradient="linear-gradient(135deg, #059669 0%, #10b981 100%)"
                                    icon={CheckCircleIcon}
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                                <StatKpiCard
                                    title="المرفوض"
                                    value={formatLYD(globalStats?.totalRefusedAmount)}
                                    subtitle="إجمالي المبالغ المرفوضة"
                                    gradient="linear-gradient(135deg, #e11d48 0%, #fb7185 100%)"
                                    icon={CancelIcon}
                                />
                            </Grid>
                        </Grid>
                    )}
                </Box>
            </Collapse>

            {/* 🔹 MAIN CONTENT 🔹 */}
            <Box sx={{ flex: 1, pt: 0, pb: '1.0rem', mt: -2 }}>
                {!selectedEmployer ? (
                    <Box sx={{
                        height: '25.0rem',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: 'rgba(var(--mui-palette-primary-mainChannel), 0.02)',
                        borderRadius: '0.1875rem',
                        border: '2px dashed',
                        borderColor: 'divider'
                    }}>
                        <Avatar sx={{ width: '5.0rem', height: '5.0rem', bgcolor: 'primary.light', mb: '1.0rem' }}>
                            <BusinessIcon sx={{ fontSize: '2.5rem', color: 'primary.main' }} />
                        </Avatar>
                        <Typography variant="h5" color="text.primary" gutterBottom>
                            يرجى اختيار جهة العمل
                        </Typography>
                        <Typography variant="body1" color="text.secondary">
                            اختر جهة العمل من القائمة أعلاه لعرض مقدمي الخدمات المتعاقدين والبدء في إدخال المطالبات.
                        </Typography>
                    </Box>
                ) : (
                    <Stack spacing={3}>
                        {/* Filtering Tools */}
                        <MainCard sx={{ p: '8px !important' }}>
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Typography variant="subtitle2" fontWeight="bold">
                                    مقدمو الخدمات لجهة عمل: <Typography component="span" color="primary.main" variant="inherit">{selectedEmployer.label || selectedEmployer.name}</Typography>
                                </Typography>

                                <Box sx={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                                    {/* Month Filter */}
                                    <FormControl size="small" sx={{ width: '8.125rem' }}>
                                        <Select
                                            value={filterMonth}
                                            onChange={(e) => setFilterMonth(e.target.value)}
                                            displayEmpty
                                            sx={{ bgcolor: 'background.default' }}
                                        >
                                            {MONTHS_AR.map((m, idx) => (
                                                <MenuItem key={idx} value={idx + 1}>{m}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>

                                    {/* Year Filter */}
                                    <FormControl size="small" sx={{ width: '6.25rem' }}>
                                        <Select
                                            value={filterYear}
                                            onChange={(e) => setFilterYear(e.target.value)}
                                            displayEmpty
                                            sx={{ bgcolor: 'background.default' }}
                                        >
                                            {[...Array(6).keys()].map(i => {
                                                const year = new Date().getFullYear() - 2 + i;
                                                return <MenuItem key={year} value={year}>{year}</MenuItem>
                                            })}
                                        </Select>
                                    </FormControl>

                                    {/* Search Box */}
                                    <Box sx={{ width: '15.625rem', ml: 1 }}>
                                        <TextField
                                            fullWidth
                                            size="small"
                                            placeholder="ابحث عن مقدم خدمة..."
                                            value={searchTerm}
                                            onChange={(e) => setSearchTerm(e.target.value)}
                                            InputProps={{
                                                startAdornment: <SearchIcon sx={{ color: 'text.disabled', mr: 1 }} fontSize="small" />
                                            }}
                                            sx={{ bgcolor: 'background.default' }}
                                        />
                                    </Box>
                                </Box>
                            </Stack>
                        </MainCard>

                        {/* Providers Grid */}
                        {isLoadingProviders ? (
                            <Box sx={{ display: 'flex', justifyContent: 'center', p: '5.0rem' }}>
                                <CircularProgress />
                            </Box>
                        ) : filteredProviders.length > 0 ? (
                            <Grid container spacing={3}>
                                {filteredProviders.map((provider) => (
                                    <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={provider.id}>
                                        <ProviderBatchCard
                                            provider={provider}
                                            selectedEmployer={selectedEmployer}
                                            onSelectBatch={handleSelectBatch}
                                            filterMonth={filterMonth}
                                            filterYear={filterYear}
                                        />
                                    </Grid>
                                ))}
                            </Grid>
                        ) : (
                            <Box sx={{ textAlign: 'center', py: '5.0rem', bgcolor: 'background.paper' }}>
                                <Typography color="text.secondary">لا يوجد مقدمو خدمات متعاقدون حالياً مع هذه الجهة أو يطابقون بحثك.</Typography>
                            </Box>
                        )}
                    </Stack>
                )}
            </Box>
        </Box>
    );
}






