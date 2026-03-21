import { forwardRef } from 'react';
import { Box, Typography } from '@mui/material';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { formatCurrency } from 'utils/currency-formatter';

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

/**
 * Rejected Batch Print Report
 * Specifically showing claims with full or partial rejections
 */
const RejectedBatchPrintReport = forwardRef(({ claims, employer, provider, month, year, batchCode }, ref) => {
    const { getLogoSrc, settings } = useCompanySettings();
    const logoSrc = getLogoSrc();
    const displayName = settings?.companyName || 'وعد';

    // Filter only rejected or partially rejected claims
    const rejectedClaims = (claims || []).filter(c =>
        c.status === 'REJECTED' || (c.refusedAmount && c.refusedAmount > 0)
    );

    if (rejectedClaims.length === 0) {
        return (
            <div ref={ref} style={{ position: 'fixed', bottom: '-9999px', left: 0, zIndex: -1, overflow: 'hidden' }} className="print-content-wrapper-rejected">
                <Typography variant="h6" sx={{ textAlign: 'center', p: '2.5rem' }}>لا توجد مطالبات مرفوضة في هذه الدفعة</Typography>
            </div>
        );
    }

    // Calculate Global Stats for rejected only
    let totalRejected = 0;
    rejectedClaims.forEach(c => {
        const req = c.requestedAmount || 0;
        const rejectAmt = c.status === 'REJECTED' && (c.refusedAmount === null || c.refusedAmount === 0)
            ? req : (c.refusedAmount || 0);
        totalRejected += rejectAmt;
    });

    // Group claims by member (patient)
    const groupedByPatient = {};

    rejectedClaims.forEach(c => {
        const memberKey = c.memberNationalNumber || c.memberCardNumber || c.memberId || 'UNKNOWN';
        if (!groupedByPatient[memberKey]) {
            groupedByPatient[memberKey] = {
                memberNumber: memberKey,
                memberName: c.memberName || c.memberFullName || 'غير معروف',
                diagnosis: c.diagnosisDescription || c.diagnosisCode || 'غير محدد',
                services: [],
                subGross: 0,
                subRejected: 0
            };
        }

        const g = groupedByPatient[memberKey];
        const req = c.requestedAmount || 0;
        const rejectAmt = c.status === 'REJECTED' && (c.refusedAmount === null || c.refusedAmount === 0)
            ? req : (c.refusedAmount || 0);

        if (c.lines && c.lines.length > 0) {
            c.lines.forEach(line => {
                const lineRej = line.refusedAmount || 0;
                if (lineRej > 0 || c.status === 'REJECTED') {
                    const lineGross = line.totalPrice || 0;
                    const finalLineRej = c.status === 'REJECTED' ? lineGross : lineRej;

                    g.services.push({
                        name: line.medicalServiceName || line.serviceName || line.medicalServiceCode,
                        date: c.serviceDate,
                        gross: lineGross,
                        rejected: finalLineRej,
                        reason: line.notes || line.rejectionReason || c.reviewerComment || 'مرفوض'
                    });
                    g.subGross += lineGross;
                    g.subRejected += finalLineRej;
                }
            });
        } else {
            g.services.push({
                name: 'مطالبة مجمعة',
                date: c.serviceDate,
                gross: req,
                rejected: rejectAmt,
                reason: c.reviewerComment || 'مرفوض'
            });
            g.subGross += req;
            g.subRejected += rejectAmt;
        }
    });

    const formatLYD = (val) => formatCurrency(val || 0);
    const printDate = new Date().toISOString().split('T')[0];

    return (
        <div ref={ref} style={{ position: 'fixed', bottom: '-9999px', left: 0, zIndex: -1, width: '210mm', overflow: 'hidden' }} className="print-content-wrapper-rejected">
            <style type="text/css" media="print">
                {`
                    @page { size: A4 portrait; margin: 15mm; }
                    body { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif !important; direction: rtl; }
                    .print-content-wrapper-rejected { display: block !important; padding: 0; margin: 0; background: #fff !important; }
                    .page-break { page-break-after: always; }
                    .print-header { text-align: center; margin-bottom: 20px; }
                    .print-title { font-size: 18px; font-weight: bold; }
                    .meta-info { display: flex; justify-content: space-between; font-size: 11px; margin-bottom: 20px; }
                    
                    table.print-table { width: '6.25rem'%; border-collapse: collapse; font-size: 10px; text-align: center; }
                    table.print-table th { padding: 6px; border: 1px solid #000; font-weight: bold; background: #eee !important; }
                    table.print-table td { padding: 5px; border: 1px solid #000; }
                    .subtotal-row td { font-weight: bold; background: #f5f5f5 !important; }
                    
                    .patient-block { border: 1px solid #ccc; margin-bottom: 15px; border-radius: 4px; overflow: hidden; }
                    .patient-info-bar { display: flex; background: #f0f0f0; padding: 5px 10px; font-size: 11px; border-bottom: 1px solid #ccc; }
                    .pi-item { flex: 1; }

                    @media print {
                        body * { visibility: hidden; }
                        .print-content-wrapper-rejected, .print-content-wrapper-rejected * { visibility: visible; }
                        .print-content-wrapper-rejected { position: absolute; left: 0; top: 0; width: '6.25rem'%; }
                    }
                `}
            </style>

            <div className="print-header">
                <img src={logoSrc} alt="logo" style={{ height: '4.0625rem', width: 'auto', objectFit: 'contain', marginBottom: '0.375rem' }} />
                <Typography className="print-title">{displayName}</Typography>
                <Typography className="print-title">تقرير المطالبات المرفوضة (كلياً وجزئياً)</Typography>
                <Typography variant="subtitle2">{provider?.name} - {employer?.name}</Typography>
                <Typography variant="body2">{MONTHS_AR[month - 1]} {year}</Typography>
            </div>

            <div className="meta-info">
                <div>تاريخ التقرير: {printDate}</div>
                <div>كود الدفعة: {batchCode}</div>
            </div>

            {Object.values(groupedByPatient).map((patient, pIdx) => (
                <div key={pIdx} className="patient-block">
                    <div className="patient-info-bar">
                        <div className="pi-item"><strong>المستفيد:</strong> {patient.memberName}</div>
                        <div className="pi-item"><strong>رقم التأمين:</strong> {patient.memberNumber}</div>
                    </div>

                    <table className="print-table">
                        <thead>
                            <tr>
                                <th style={{ width: '40%' }}>الخدمة الطبية</th>
                                <th style={{ width: '15%' }}>القيمة الإجمالية</th>
                                <th style={{ width: '15%' }}>القيمة المرفوضة</th>
                                <th style={{ width: '30%' }}>سبب الرفض</th>
                            </tr>
                        </thead>
                        <tbody>
                            {patient.services.map((srv, sIdx) => (
                                <tr key={sIdx}>
                                    <td style={{ textAlign: 'right' }}>{srv.name}</td>
                                    <td>{formatLYD(srv.gross)}</td>
                                    <td style={{ color: 'red', fontWeight: 'bold' }}>{formatLYD(srv.rejected)}</td>
                                    <td style={{ textAlign: 'right' }}>{srv.reason}</td>
                                </tr>
                            ))}
                            <tr className="subtotal-row">
                                <td style={{ textAlign: 'left' }}>إجمالي المرفوض للمستفيد:</td>
                                <td>{formatLYD(patient.subGross)}</td>
                                <td style={{ color: 'red' }}>{formatLYD(patient.subRejected)}</td>
                                <td></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            ))}

            <Box sx={{ mt: '1.5rem', p: '1.0rem', border: '2px solid #000', textAlign: 'center', bgcolor: '#fdf2f2' }}>
                <Typography variant="h6" fontWeight="bold">
                    إجمالي القيمة المرفوضة في هذه الدفعة: {formatLYD(totalRejected)}
                </Typography>
            </Box>
        </div>
    );
});

export default RejectedBatchPrintReport;


