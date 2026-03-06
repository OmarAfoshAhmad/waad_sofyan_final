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
    Chip,
    Avatar,
    IconButton,
    Tooltip,
    Divider,
    CircularProgress,
    Badge,
    Select,
    MenuItem,
    FormControl
} from '@mui/material';

import {
    Business as BusinessIcon,
    LocalHospital as LocalHospitalIcon,
    Folder as FolderIcon,
    Search as SearchIcon,
    Add as AddIcon,
    Timeline as TimelineIcon,
    Receipt as ReceiptIcon,
    ArrowForward as ArrowForwardIcon,
    CalendarMonth as CalendarIcon,
    ViewList as ViewListIcon,
    CheckCircle as CheckCircleIcon,
    Cancel as CancelIcon,
    CalendarToday as CalendarTodayIcon
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

// ===========================================
// CONSTANTS
// ===========================================

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const MONTHS_EN = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];

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

    const batchCode = useMemo(() =>
        generateBatchCode(selectedEmployer, provider, filterMonth, filterYear),
        [selectedEmployer, provider, filterMonth, filterYear]
    );

    // Fetch actual stats using Claims Service Financial Summary endpoint
    const { data: summaryData } = useQuery({
        queryKey: ['batch-stats', selectedEmployer?.id, provider?.id, filterMonth, filterYear],
        queryFn: () => {
            if (!selectedEmployer?.id || !provider?.id || !filterMonth || !filterYear) return null;
            return claimsService.getFinancialSummary({
                employerId: selectedEmployer.id,
                providerId: provider.id,
                dateFrom: `${filterYear}-${String(filterMonth).padStart(2, '0')}-01`,
                dateTo: `${filterYear}-${String(filterMonth).padStart(2, '0')}-31`
            });
        },
        enabled: !!selectedEmployer?.id && !!provider?.id
    });

    // Map fetched stats or default to 0
    const stats = {
        requestsCount: summaryData?.claimsCount || 0,
        amount: summaryData?.totalClaimsAmount || 0,
        covered: summaryData?.totalApprovedAmount || 0,
        refused: summaryData?.totalRefusedAmount || 0
    };

    return (
        <Card
            dir="ltr"
            sx={{
                position: 'relative',
                overflow: 'hidden',
                borderLeft: '6px solid',
                borderLeftColor: '#76b880', // Exact green from image
                borderRadius: 3,
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
            <CardContent sx={{ p: 2.5, flexGrow: 1, display: 'flex', flexDirection: 'column', '&:last-child': { pb: 2.5 } }}>

                {/* 1. Header Row (Month | Code) */}
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                    <Typography variant="caption" sx={{ color: '#888', fontWeight: 600 }}>
                        {MONTHS_EN[filterMonth - 1]} {filterYear}
                    </Typography>
                    <Typography
                        variant="caption"
                        dir="ltr"
                        sx={{ color: '#aaa', fontWeight: 700, letterSpacing: 0.5, fontFamily: 'monospace' }}
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
                        spacing={1.5}
                        sx={{ mb: 1.5 }}
                    >
                        <BusinessIcon sx={{ color: '#d0d0d0', fontSize: '1rem' }} />
                        <Typography
                            variant="subtitle1"
                            sx={{
                                fontWeight: 700,
                                color: '#444',
                                textAlign: 'center',
                                fontSize: '1rem',
                                lineHeight: 1.2
                            }}
                        >
                            {provider.name}
                        </Typography>
                    </Stack>
                    <Divider sx={{ mb: 2, opacity: 0.6 }} />
                </Box>

                {/* 3. Requests Green Banner */}
                <Box sx={{
                    bgcolor: '#f5f9f5',
                    borderRadius: 4,
                    border: '1.5px solid #7cb983',
                    py: 1,
                    px: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mb: 3,
                    width: '100%',
                    mx: 'auto',
                    minHeight: 50
                }}>
                    <Stack direction="row" spacing={1.5} alignItems="center" sx={{ whiteSpace: 'nowrap' }}>
                        <ViewListIcon sx={{ color: '#7cb983', fontSize: '2rem' }} />
                        <Typography variant="h5" sx={{ color: '#7cb983', fontWeight: 700 }}>
                            {stats.requestsCount} Requests
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
                        borderRadius: 10,
                        px: 2.5,
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>Amount</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{stats.amount.toFixed(2)}</Typography>
                    </Box>

                    {/* Covered Block */}
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: '#67bc72',
                        color: 'white',
                        borderRadius: 10,
                        px: 2.5,
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <CheckCircleIcon sx={{ fontSize: '1.2rem' }} />
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>Covered</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{stats.covered.toFixed(2)}</Typography>
                    </Box>

                    {/* Refused Block */}
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: '#e66a6a',
                        color: 'white',
                        borderRadius: 10,
                        px: 2.5,
                        py: 0.6,
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)'
                    }}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <CancelIcon sx={{ fontSize: '1.2rem' }} />
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>Refused</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{stats.refused.toFixed(2)}</Typography>
                    </Box>

                </Stack>
            </CardContent>
        </Card>
    );
};

// ===========================================
// MAIN COMPONENT
// ===========================================

export default function ClaimBatchManagement() {
    const navigate = useNavigate();

    const [selectedEmployer, setSelectedEmployer] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterMonth, setFilterMonth] = useState(new Date().getMonth() + 1);
    const [filterYear, setFilterYear] = useState(new Date().getFullYear());

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

        // Final enriched list
        const list = allowedProviders.map((p) => ({
            id: p.id,
            name: p.name,
            code: p.licenseNumber || p.code || p.id,
            city: p.city || 'المنطقة',
            contractId: contractByProviderId.get(p.id) || null
        }));

        if (searchTerm) {
            return list.filter(p =>
                p.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                String(p.code)?.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        return list;
    }, [allowedProviders, providerContracts, selectedEmployer, searchTerm]);

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
                        {/* Employer Selector Directly in Header for context */}
                        <Autocomplete
                            size="small"
                            sx={{ width: 300, bgcolor: 'background.paper', borderRadius: 1 }}
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

            {/* 🔹 MAIN CONTENT 🔹 */}
            <Box sx={{ flex: 1, pt: 0, pb: 2, mt: -2 }}>
                {!selectedEmployer ? (
                    <Box sx={{
                        height: '400px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: 'rgba(var(--mui-palette-primary-mainChannel), 0.02)',
                        borderRadius: 3,
                        border: '2px dashed',
                        borderColor: 'divider'
                    }}>
                        <Avatar sx={{ width: 80, height: 80, bgcolor: 'primary.light', mb: 2 }}>
                            <BusinessIcon sx={{ fontSize: 40, color: 'primary.main' }} />
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

                                <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                                    {/* Month Filter */}
                                    <FormControl size="small" sx={{ width: 130 }}>
                                        <Select
                                            value={filterMonth}
                                            onChange={(e) => setFilterMonth(e.target.value)}
                                            displayEmpty
                                            sx={{ bgcolor: 'background.default' }}
                                        >
                                            {MONTHS_EN.map((m, idx) => (
                                                <MenuItem key={idx} value={idx + 1}>{m}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>

                                    {/* Year Filter */}
                                    <FormControl size="small" sx={{ width: 100 }}>
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
                                    <Box sx={{ width: 250, ml: 1 }}>
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
                            <Box sx={{ display: 'flex', justifyContent: 'center', p: 10 }}>
                                <CircularProgress />
                            </Box>
                        ) : filteredProviders.length > 0 ? (
                            <Grid container spacing={3}>
                                {filteredProviders.map((provider) => (
                                    <Grid item xs={12} sm={12} md={6} lg={4} key={provider.id}>
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
                            <Box sx={{ textAlign: 'center', py: 10, bgcolor: 'background.paper', borderRadius: 2 }}>
                                <Typography color="text.secondary">لا يوجد مقدمو خدمات متعاقدون حالياً مع هذه الجهة أو يطابقون بحثك.</Typography>
                            </Box>
                        )}
                    </Stack>
                )}
            </Box>
        </Box>
    );
}
