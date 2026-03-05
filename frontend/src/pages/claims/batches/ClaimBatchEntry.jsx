/**
 * Claim Batch Entry Page
 * Rapid data entry for a specific (Employer, Provider, Month, Year) batch.
 */

import { useState, useMemo, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Box,
    Grid,
    Stack,
    Typography,
    Button,
    TextField,
    Autocomplete,
    Divider,
    CircularProgress,
    IconButton,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Alert,
    alpha
} from '@mui/material';

import {
    ArrowBack as ArrowBackIcon,
    Save as SaveIcon,
    Add as AddIcon,
    Delete as DeleteIcon,
    Receipt as ReceiptIcon,
    CheckCircle as CheckIcon,
    Search as SearchIcon
} from '@mui/icons-material';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Services
import membersService from 'services/api/members.service';
import providersService from 'services/api/providers.service';
import employersService from 'services/api/employers.service';
import claimsService from 'services/api/claims.service';
import backlogService from 'services/api/backlog.service';
import providerContractsService from 'services/api/provider-contracts.service';

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const MONTHS_EN = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];

export default function ClaimBatchEntry() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { enqueueSnackbar } = useSnackbar();

    const employerId = searchParams.get('employerId');
    const providerId = searchParams.get('providerId');
    const month = parseInt(searchParams.get('month'));
    const year = parseInt(searchParams.get('year'));

    // Form State
    const [selectedMember, setSelectedMember] = useState(null);
    const [memberSearchTerm, setMemberSearchTerm] = useState('');
    const [serviceLines, setServiceLines] = useState([{ service: null, quantity: 1, price: 0 }]);
    const [serviceDate, setServiceDate] = useState(`${year}-${String(month).padStart(2, '0')}-01`);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Ref for auto-focusing member search after save
    const memberSearchRef = useRef(null);

    // 1. Fetch Context Data
    const { data: employer } = useQuery({
        queryKey: ['employer', employerId],
        queryFn: () => employersService.getById(employerId),
        enabled: !!employerId
    });

    const { data: provider } = useQuery({
        queryKey: ['provider', providerId],
        queryFn: () => providersService.getById(providerId),
        enabled: !!providerId
    });

    // 2. Fetch Existing Claims in this Batch
    const { data: batchClaims, isLoading: isLoadingClaims } = useQuery({
        queryKey: ['batch-claims', employerId, providerId, month, year],
        queryFn: async () => {
            const dateFrom = `${year}-${String(month).padStart(2, '0')}-01`;
            const dateTo = `${year}-${String(month).padStart(2, '0')}-31`;
            return await claimsService.list({
                employerId,
                providerId,
                dateFrom,
                dateTo,
                size: 50,
                sortBy: 'createdAt',
                sortDir: 'desc'
            });
        }
    });

    // 3. Fetch Contracted Services for this Provider
    const { data: contractedServices, isLoading: isLoadingServices } = useQuery({
        queryKey: ['provider-contracted-services', providerId],
        queryFn: () => providerContractsService.getAllContractedServices(providerId),
        enabled: !!providerId
    });

    // 4. Member Search Logic
    const { data: memberSearchResults, isFetching: isSearchingMembers } = useQuery({
        queryKey: ['member-search-batch', memberSearchTerm, employerId],
        queryFn: () => membersService.advancedSearchMembers({
            q: memberSearchTerm,
            employerId,
            status: 'ACTIVE'
        }),
        enabled: memberSearchTerm.length >= 3,
        staleTime: 5000
    });

    const memberOptions = useMemo(() => {
        if (!memberSearchResults) return [];
        return Array.isArray(memberSearchResults.content) ? memberSearchResults.content : [];
    }, [memberSearchResults]);

    const serviceOptions = useMemo(() => {
        if (!contractedServices) return [];
        return contractedServices.map(s => ({
            ...s,
            label: `[${s.serviceCode}] ${s.serviceName} - ${s.price} AED`
        }));
    }, [contractedServices]);

    // Handlers
    const handleAddLine = () => {
        setServiceLines([...serviceLines, { service: null, quantity: 1, price: 0 }]);
    };

    const handleRemoveLine = (index) => {
        const newLines = serviceLines.filter((_, i) => i !== index);
        setServiceLines(newLines.length ? newLines : [{ service: null, quantity: 1, price: 0 }]);
    };

    const handleLineChange = (index, field, value) => {
        const newLines = [...serviceLines];
        newLines[index][field] = value;
        if (field === 'service' && value) {
            newLines[index].price = value.price;
        }
        setServiceLines(newLines);
    };

    const calculateTotal = () => {
        return serviceLines.reduce((sum, line) => sum + (line.price * line.quantity), 0);
    };

    const handleSaveClaim = async () => {
        if (!selectedMember) {
            enqueueSnackbar('يرجى اختيار المستفيد', { variant: 'error' });
            return;
        }

        if (serviceLines.some(l => !l.service)) {
            enqueueSnackbar('يرجى اختيار الخدمة لجميع الأسطر', { variant: 'error' });
            return;
        }

        try {
            setIsSubmitting(true);

            const payload = {
                memberId: selectedMember.id,
                providerId: parseInt(providerId),
                serviceDate: serviceDate,
                lines: serviceLines.map(l => ({
                    serviceId: l.service.serviceId,
                    quantity: l.quantity,
                    requestedAmount: l.price * l.quantity
                })),
                notes: `Batch Entry: ${MONTHS_AR[month]} ${year}`
            };

            await backlogService.createManual(payload);

            enqueueSnackbar('تم حفظ المطالبة بنجاح', { variant: 'success' });

            // Reset Form
            setSelectedMember(null);
            setMemberSearchTerm('');
            setServiceLines([{ service: null, quantity: 1, price: 0 }]);

            // Refresh list
            queryClient.invalidateQueries({ queryKey: ['batch-claims'] });

            // Focus back
            if (memberSearchRef.current) {
                memberSearchRef.current.focus();
            }
        } catch (error) {
            enqueueSnackbar(error.message || 'فشل حفظ المطالبة', { variant: 'error' });
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Box sx={{ pb: 5 }}>
            <ModernPageHeader
                title={`Entry: ${MONTHS_EN[month - 1]} ${year}`}
                subtitle={`${provider?.name || '...'} (Employer: ${employer?.name || '...'})`}
                icon={ReceiptIcon}
                breadcrumbs={[
                    { label: 'Home', path: '/' },
                    { label: 'Batch System', path: '/claims/batches' },
                    { label: 'Data Entry' }
                ]}
                actions={
                    <Stack direction="row" spacing={1}>
                        <Button
                            variant="contained"
                            color="success"
                            startIcon={<CheckIcon />}
                            onClick={() => {
                                enqueueSnackbar('تم إرسال الدفعة للمراجعة بنجاح', { variant: 'success' });
                                navigate(`/claims/batches/detail?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`);
                            }}
                            disabled={!batchClaims?.content?.length}
                        >
                            إنهاء وإرسال الدفعة
                        </Button>
                        <Button
                            variant="outlined"
                            startIcon={<ArrowBackIcon />}
                            onClick={() => navigate(`/claims/batches/detail?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`)}
                        >
                            رجوع
                        </Button>
                    </Stack>
                }
            />

            <Grid container spacing={3} sx={{ px: 3, mt: -2 }}>
                {/* 🔹 LEFT: ENTRY FORM 🔹 */}
                <Grid item xs={12} md={7}>
                    <MainCard border={false} shadow="0 8px 30px rgba(0,0,0,0.12)" sx={{ borderRadius: 4 }}>
                        <Stack spacing={3}>
                            <Typography variant="h5" fontWeight="bold" color="primary">إضافة مطالبة جديدة</Typography>

                            <Grid container spacing={2}>
                                <Grid item xs={12} sm={8}>
                                    <Typography variant="subtitle2" gutterBottom fontWeight="600">المستفيد</Typography>
                                    <Autocomplete
                                        fullWidth
                                        size="small"
                                        options={memberOptions}
                                        loading={isSearchingMembers}
                                        value={selectedMember}
                                        onChange={(_, val) => setSelectedMember(val)}
                                        onInputChange={(_, val) => setMemberSearchTerm(val)}
                                        getOptionLabel={(opt) => `${opt.fullName} (${opt.memberCardNumber || opt.id})`}
                                        renderInput={(params) => (
                                            <TextField
                                                {...params}
                                                inputRef={memberSearchRef}
                                                placeholder="ابحث برقم البطاقة أو الاسم (3 حروف ع الأقل)..."
                                                variant="outlined"
                                                autoFocus
                                                InputProps={{
                                                    ...params.InputProps,
                                                    startAdornment: <SearchIcon sx={{ color: 'text.disabled', mr: 1 }} fontSize="small" />
                                                }}
                                            />
                                        )}
                                        noOptionsText={memberSearchTerm.length < 3 ? "يرجى كتابة 3 أحرف للبحث..." : "لا يوجد نتائج"}
                                    />
                                </Grid>
                                <Grid item xs={12} sm={4}>
                                    <Typography variant="subtitle2" gutterBottom fontWeight="600">تاريخ الخدمة</Typography>
                                    <TextField
                                        fullWidth
                                        size="small"
                                        type="date"
                                        value={serviceDate}
                                        onChange={(e) => setServiceDate(e.target.value)}
                                    />
                                </Grid>
                            </Grid>

                            <Divider />

                            <Box>
                                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                                    <Typography variant="subtitle2" fontWeight="600">الخدمات الطبية المتعاقد عليها</Typography>
                                    <Button
                                        size="small"
                                        variant="soft"
                                        color="primary"
                                        startIcon={<AddIcon />}
                                        onClick={handleAddLine}
                                        sx={{ borderRadius: 2 }}
                                    >
                                        أضف سطر
                                    </Button>
                                </Stack>

                                {serviceLines.map((line, idx) => (
                                    <Grid container spacing={1} key={idx} sx={{ mb: 1.5 }} alignItems="flex-end">
                                        <Grid item xs={12} sm={7}>
                                            <Autocomplete
                                                size="small"
                                                options={serviceOptions}
                                                loading={isLoadingServices}
                                                value={line.service}
                                                onChange={(_, val) => handleLineChange(idx, 'service', val)}
                                                getOptionLabel={(opt) => opt.label || ''}
                                                renderInput={(params) => <TextField {...params} placeholder="اختر الخدمة..." />}
                                            />
                                        </Grid>
                                        <Grid item xs={4} sm={2}>
                                            <TextField
                                                size="small"
                                                type="number"
                                                label="الكمية"
                                                value={line.quantity}
                                                onChange={(e) => handleLineChange(idx, 'quantity', parseInt(e.target.value) || 1)}
                                            />
                                        </Grid>
                                        <Grid item xs={6} sm={2}>
                                            <TextField
                                                size="small"
                                                label="السعر"
                                                value={line.price}
                                                disabled
                                                InputProps={{
                                                    endAdornment: <Typography variant="caption" sx={{ color: 'text.secondary' }}>AED</Typography>,
                                                    sx: { bgcolor: 'action.hover' }
                                                }}
                                            />
                                        </Grid>
                                        <Grid item xs={2} sm={1}>
                                            <IconButton color="error" size="small" onClick={() => handleRemoveLine(idx)} disabled={serviceLines.length === 1}>
                                                <DeleteIcon fontSize="small" />
                                            </IconButton>
                                        </Grid>
                                    </Grid>
                                ))}
                            </Box>

                            <Box sx={{
                                background: (theme) => `linear-gradient(45deg, ${alpha(theme.palette.primary.main, 0.05)} 0%, ${alpha(theme.palette.primary.main, 0.1)} 100%)`,
                                p: 3,
                                borderRadius: 4,
                                border: '1px solid',
                                borderColor: 'primary.light'
                            }}>
                                <Stack direction="row" justifyContent="space-between" alignItems="center">
                                    <Box>
                                        <Typography variant="h4" fontWeight="bold" color="primary.main">
                                            {calculateTotal().toFixed(2)} <Typography component="span" variant="h6">AED</Typography>
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">الإجمالي المستحق لهذه المطالبة</Typography>
                                    </Box>
                                    <Button
                                        variant="contained"
                                        size="large"
                                        startIcon={isSubmitting ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
                                        onClick={handleSaveClaim}
                                        disabled={isSubmitting || !selectedMember}
                                        sx={{
                                            px: 5,
                                            py: 1.5,
                                            borderRadius: 3,
                                            boxShadow: (theme) => `0 10px 20px -5px ${alpha(theme.palette.primary.main, 0.4)}`,
                                            '&:hover': { boxShadow: (theme) => `0 15px 25px -5px ${alpha(theme.palette.primary.main, 0.5)}` }
                                        }}
                                    >
                                        {isSubmitting ? 'جاري الحفظ...' : 'حفظ وإضافة مستفيد'}
                                    </Button>
                                </Stack>
                            </Box>
                        </Stack>
                    </MainCard>
                </Grid>

                {/* 🔹 RIGHT: BATCH LIST 🔹 */}
                <Grid item xs={12} md={5}>
                    <MainCard border={false} shadow="0 4px 20px rgba(0,0,0,0.08)" title={`المطالبات المدخلة (${batchClaims?.content?.length || 0})`} sx={{ borderRadius: 4 }}>
                        {isLoadingClaims ? (
                            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress size={24} /></Box>
                        ) : batchClaims?.content?.length > 0 ? (
                            <TableContainer sx={{ maxHeight: 600 }}>
                                <Table size="small" stickyHeader>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell sx={{ fontWeight: 'bold' }}>المستفيد</TableCell>
                                            <TableCell align="center" sx={{ fontWeight: 'bold' }}>التاريخ</TableCell>
                                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>المبلغ</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {batchClaims.content.map((claim) => (
                                            <TableRow key={claim.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                                                <TableCell>
                                                    <Typography variant="body2" fontWeight={600}>{claim.memberName}</Typography>
                                                    <Typography variant="caption" color="text.secondary">{claim.memberCardNumber}</Typography>
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Typography variant="caption" sx={{ bgcolor: 'action.selected', px: 1, py: 0.2, borderRadius: 1 }}>
                                                        {claim.serviceDate}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="right">
                                                    <Typography variant="body2" fontWeight="bold" color="secondary.main">{claim.requestedAmount?.toFixed(2)}</Typography>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        ) : (
                            <Box sx={{ textAlign: 'center', py: 10 }}>
                                <ReceiptIcon sx={{ fontSize: 60, color: 'action.disabled', mb: 2, opacity: 0.3 }} />
                                <Typography variant="body2" color="text.secondary">لا يوجد مطالبات مدخلة بعد في هذه الدفعة.</Typography>
                            </Box>
                        )}

                        <Box sx={{ mt: 3 }}>
                            <Alert
                                icon={<CheckIcon fontSize="inherit" />}
                                severity="info"
                                sx={{ borderRadius: 2, '& .MuiAlert-message': { width: '100%' } }}
                            >
                                <Typography variant="caption" display="block">
                                    يتم حفظ هذه البيانات كمسودات (Backlog) للمراجعة.
                                </Typography>
                            </Alert>
                        </Box>
                    </MainCard>
                </Grid>
            </Grid>
        </Box>
    );
}
