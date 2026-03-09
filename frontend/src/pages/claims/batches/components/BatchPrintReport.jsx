import { forwardRef } from 'react';
import { Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Stack } from '@mui/material';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

/**
 * Batch Print Report (matches the exact paper provided by Waad TPA)
 */
const BatchPrintReport = forwardRef(({ claims, employer, provider, month, year, batchCode }, ref) => {
    const { getLogoSrc, settings } = useCompanySettings();
    const logoSrc = getLogoSrc();
    // Full company title to match physical report: "شركة وعد لإدارة النفقات الطبية"
    const companyName = settings?.companyName || 'وعد';
    const businessType = (settings?.businessType || 'لإدارة النفقات الطبية').replace(/\s*\(.*?\)\s*/g, '').trim();
    const fullTitle = `شركة ${companyName} ${businessType}`;

    if (!claims || claims.length === 0) return null;

    // Calculate Global Stats
    let totalGross = 0;
    let totalRejected = 0;
    let totalPatientShare = 0;
    let totalNet = 0;

    // Group claims by member (patient)
    const groupedByPatient = {};

    claims.forEach(c => {
        // Stats
        const req = c.requestedAmount || 0;
        const rejectAmt = c.status === 'REJECTED' && (c.refusedAmount === null || c.refusedAmount === 0)
            ? req : (c.refusedAmount || 0);
        const app = c.approvedAmount || 0;
        const patShare = c.patientCoPay || 0;
        // In some statuses netProviderAmount might be available, otherwise assume approvedAmount.
        const net = c.netProviderAmount || app;

        totalGross += req;
        totalRejected += rejectAmt;
        totalPatientShare += patShare;
        // The net to the provider should be Gross - Rejected - PatientShare. 
        // We calculate based on the actual lines or claim header.
        totalNet += net;

        // Group
        const memberKey = c.memberNationalNumber || c.memberCardNumber || c.memberId || 'UNKNOWN';
        if (!groupedByPatient[memberKey]) {
            groupedByPatient[memberKey] = {
                memberNumber: memberKey,
                memberName: c.memberName || c.memberFullName || 'غير معروف',
                diagnosis: c.diagnosisDescription || c.diagnosisCode || 'غير محدد',
                complaint: c.diagnosisDescription || '-', // usually from visit
                services: [],
                subGross: 0,
                subNet: 0,
                subRejected: 0
            };
        }

        const g = groupedByPatient[memberKey];

        // Process lines if available, otherwise just use the claim as a single summary line
        if (c.lines && c.lines.length > 0) {
            c.lines.forEach(line => {
                const lineGross = line.totalPrice || 0;
                const lineRej = line.refusedAmount || 0;
                const lineNet = lineGross - lineRej; // Simple assumption if no detailed net is provided per line

                g.services.push({
                    name: line.medicalServiceName || line.serviceName || line.medicalServiceCode,
                    date: c.serviceDate,
                    gross: lineGross,
                    net: lineNet,
                    rejected: lineRej,
                    reason: line.notes || line.rejectionReason || ''
                });

                g.subGross += lineGross;
                g.subNet += lineNet;
                g.subRejected += lineRej;
            });
        } else {
            g.services.push({
                name: 'مطالبة مجمعة بدون تفاصيل',
                date: c.serviceDate,
                gross: req,
                net: net,
                rejected: rejectAmt,
                reason: c.reviewerComment || ''
            });
            g.subGross += req;
            g.subNet += net;
            g.subRejected += rejectAmt;
        }
    });

    const formatLYD = (val) => `${(val || 0).toLocaleString('en-US', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} د.ل`;

    const printDate = new Date().toISOString().split('T')[0];

    return (
        <div ref={ref} style={{ position: 'fixed', bottom: '-9999px', left: 0, zIndex: -1, width: '210mm', overflow: 'hidden' }} className="print-content-wrapper">
            <style type="text/css" media="print">
                {`
                    @page { size: A4 portrait; margin: 15mm; }
                    body { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif !important; direction: rtl; }
                    .print-content-wrapper { display: block !important; padding: 0; margin: 0; background: #fff !important; }
                    .page-break { page-break-after: always; }
                    .print-header { text-align: center; margin-bottom: 30px; }
                    .print-header img { width: 80px; margin-bottom: 10px; }
                    .print-title { font-size: 18px; font-weight: bold; color: #3c5e88; }
                    .meta-info { display: flex; justify-content: space-between; font-size: 11px; color: #555; margin-bottom: 40px; }
                    .summary-section { margin-top: 40px; font-size: 13px; line-height: 2; margin-right: 20px; }
                    .summary-line { margin-bottom: 8px; }
                    .summary-line strong { margin-left: 10px; }
                    .patient-block { border: 1px solid #ccc; margin-bottom: 20px; font-size: 11px; }
                    .patient-header { display: flex; border-bottom: 1px solid #ccc; background: #f9f9f9; }
                    .ph-col { flex: 1; padding: 6px 10px; border-left: 1px solid #ccc; }
                    .ph-col:last-child { border-left: none; }
                    .ph-details { display: flex; border-bottom: 1px solid #ccc; }
                    .ph-details-row { flex: 1; display: flex; padding: 4px 10px; border-left: 1px solid #ccc; }
                    .ph-details-row:last-child { border-left: none; }
                    
                    table.print-table { width: 100%; border-collapse: collapse; font-size: 10px; text-align: center; }
                    table.print-table th { padding: 6px; border: 1px solid #000; font-weight: bold; background: #fff !important; color: #000 !important; }
                    table.print-table td { padding: 5px; border: 1px solid #000; color: #000 !important; }
                    table.print-table .subtotal-row td { background: #fff !important; font-weight: bold; border-top: 2px solid #000; }
                    
                    .global-total { display: flex; justify-content: space-between; margin-top: 40px; font-size: 12px; font-weight: bold; }
                    .gt-col { flex: 1; padding: 10px; text-align: center; border: 1px solid #000; }
                    .gt-col.gross { background: #fff !important; border-right: none; }
                    .gt-col.net { background: #fff !important; border-right: none; border-left: none; }
                    .gt-col.rejected { background: #fff !important; color: #000 !important; border-left: none; }
                    
                    /* Hide everything else on the page during print - only show this component */
                    @media print {
                        body * { visibility: hidden; }
                        .print-content-wrapper, .print-content-wrapper * { visibility: visible; }
                        .print-content-wrapper { position: absolute; left: 0; top: 0; width: 100%; bottom: auto; overflow: visible; }
                    }
                `}
            </style>

            {/* PAGE 1: COVER / SUMMARY — matches physical reference */}
            <div className="page-break">
                {/* Header: logo centered + company name */}
                <div className="print-header">
                    <img src={logoSrc} alt="logo" style={{ height: 70, width: 'auto', objectFit: 'contain', marginBottom: 6 }} />
                    <div className="print-title">{fullTitle}</div>
                </div>

                {/* Meta row: date+page on RIGHT (RTL start), batch code on LEFT */}
                <div className="meta-info">
                    <div style={{ textAlign: 'left', direction: 'ltr' }}>{batchCode}</div>
                    <div style={{ textAlign: 'right' }}>
                        {printDate} :التاريخ<br />
                        1 :الصفحة
                    </div>
                </div>

                {/* Recipient */}
                <div style={{ fontWeight: 'bold', fontSize: '14px', margin: '32px 0 16px 0', textAlign: 'center' }}>
                    السادة: {provider?.name || '________________'}
                </div>

                {/* Body text */}
                <div style={{ lineHeight: 2, fontSize: '13px', marginBottom: '24px' }}>
                    نود إفادتكم بأن المطالبات المالية المستلمة من سيادتكم بتاريخ ضمن إيصال رقم <strong>{batchCode}</strong> ذات قيمة إجمالية قدرها <strong>{formatLYD(totalGross)}</strong> قد تمت مراجعتها وتدقيقها وفق البرامج الصحية المعتمدة بالخصوص، حيث نتج ما يلي:
                </div>

                {/* Summary list */}
                <div className="summary-section">
                    <div className="summary-line">• إجمالي القيمة المقدمة من المرفق: <strong>{formatLYD(totalGross)}</strong></div>
                    <div className="summary-line">• إجمالي القيمة الغير مستحقة (المرفوضة): <strong>{formatLYD(totalRejected)}</strong></div>
                    <div className="summary-line">• إجمالي القيمة المدفوعة من المؤمن: <strong>{formatLYD(totalPatientShare)}</strong></div>
                    <div className="summary-line">• صافي القيمة المستحق للمرفق: <strong>{formatLYD(totalNet)}</strong></div>
                    <div className="summary-line">• عدد المطالبات المستلمة: <strong>[{claims.length}]</strong></div>
                </div>

                {/* Footer note */}
                <div style={{ marginTop: '48px', fontSize: '13px' }}>
                    كم تسوية الملاحظات والنواقص خلال مدة أقصاها أسبوعين من تاريخ الاستلام لتسوية القيمة المستحقة نهائياً.
                </div>

                {/* Sign-off — NO logo repeat, just text */}
                <div style={{ marginTop: '40px', fontSize: '13px', textAlign: 'center' }}>
                    والسلام عليكم<br />
                    <strong>القسم المالي والتدقيق</strong>
                </div>
            </div>

            {/* PAGE 2+: DETAILED LISTING */}
            <div>
                <div className="print-header" style={{ marginBottom: '15px' }}>
                    <img src={logoSrc} alt="logo" style={{ height: 55, width: 'auto', objectFit: 'contain', marginBottom: 6 }} />
                    <div className="print-title" style={{ fontSize: '15px' }}>{fullTitle}</div>
                </div>

                <div className="meta-info" style={{ marginBottom: '15px' }}>
                    <div>
                        التاريخ: {printDate} <br />
                        الصفحة 2
                    </div>
                    <div style={{ fontSize: '12px', fontWeight: 'bold' }}>
                        {batchCode}
                    </div>
                </div>

                {Object.values(groupedByPatient).map((patient, pIdx) => (
                    <div key={pIdx} className="patient-block">
                        <div className="patient-header">
                            <div className="ph-col" style={{ flex: 0.5 }}><strong>No.:</strong> <br /> {batchCode}/{String(pIdx + 1).padStart(3, '0')}</div>
                            <div className="ph-col"><strong>Originator No.:</strong> <br /> - </div>
                        </div>
                        <div className="ph-details">
                            <div className="ph-details-row"><strong>Insurance Number:</strong> &nbsp; {patient.memberNumber}</div>
                            <div className="ph-details-row"><strong>Patient Name:</strong> &nbsp; {patient.memberName}</div>
                        </div>
                        <div className="ph-details">
                            <div className="ph-details-row"><strong>Complaint:</strong> &nbsp; {patient.complaint}</div>
                            <div className="ph-details-row"><strong>Diagnosis:</strong> &nbsp; {patient.diagnosis}</div>
                        </div>

                        <table className="print-table">
                            <thead>
                                <tr>
                                    <th style={{ width: '40%' }}>Medical Service</th>
                                    <th style={{ width: '15%' }}>Date</th>
                                    <th style={{ width: '12%' }}>Gross</th>
                                    <th style={{ width: '12%' }}>Net</th>
                                    <th style={{ width: '12%' }}>Rejected</th>
                                    <th style={{ width: '20%' }}>Rejection Reason</th>
                                </tr>
                            </thead>
                            <tbody>
                                {patient.services.map((srv, sIdx) => (
                                    <tr key={sIdx}>
                                        <td style={{ textAlign: 'right' }}>{srv.name}</td>
                                        <td>{srv.date}</td>
                                        <td>{formatLYD(srv.gross)}</td>
                                        <td>{formatLYD(srv.net)}</td>
                                        <td>{formatLYD(srv.rejected)}</td>
                                        <td style={{ textAlign: 'right', fontSize: '9px' }}>{srv.reason}</td>
                                    </tr>
                                ))}
                                <tr className="subtotal-row">
                                    <td colSpan={2} style={{ textAlign: 'left', paddingRight: '20px' }}>SUBTOTAL</td>
                                    <td>{formatLYD(patient.subGross)}</td>
                                    <td>{formatLYD(patient.subNet)}</td>
                                    <td>{formatLYD(patient.subRejected)}</td>
                                    <td></td>
                                </tr>

                            </tbody>
                        </table>
                    </div>
                ))}

                <div className="global-total">
                    <div style={{ flex: 1, padding: '10px', textAlign: 'center', border: '1px solid #000', borderRight: 'none' }}>TOTAL</div>
                    <div className="gt-col gross">{formatLYD(totalGross)}</div>
                    <div className="gt-col net">{formatLYD(totalNet)}</div>
                    <div className="gt-col rejected">{formatLYD(totalRejected)}</div>
                </div>
            </div>
        </div>
    );
});

export default BatchPrintReport;
