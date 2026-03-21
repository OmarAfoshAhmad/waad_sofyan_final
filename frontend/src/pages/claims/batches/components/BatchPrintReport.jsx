import { forwardRef } from 'react';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { formatCurrency } from 'utils/currency-formatter';

const REPORT_TEXT = {
    recipientPrefix: 'السادة:',
    intro:
        'نود إفادتكم بأن المطالبات المالية المستلمة من سيادتكم ذات قيمة إجمالية قدرها {TOTAL_GROSS} قد تمت مراجعتها وتدقيقها وفق البرامج الصحية المعتمدة بالخصوص، حيث نتج ما يلي:',
    note: 'يرجى تسوية الملاحظات والنواقص خلال مدة أقصاها أسبوعين من تاريخ الاستلام لتسوية القيمة المستحقة نهائيا.',
    closing: 'والسلام عليكم',
    closingDept: 'القسم المالي والتدقيق'
};

const fmtAmount = (value) => {
    return formatCurrency(value || 0);
};

const buildCompanyTitle = (name, type) => {
    const cleanName = String(name || 'وعد').trim();
    const cleanType = String(type || 'لادارة النفقات الطبية').replace(/\s*\(.*?\)\s*/g, '').trim();
    const prefixedName = cleanName.startsWith('شركة') ? cleanName : `شركة ${cleanName}`;
    return `${prefixedName} ${cleanType}`.trim();
};

const fmtDate = (value) => {
    if (!value) return '-';
    if (typeof value === 'string' && value.length >= 10) return value.slice(0, 10);
    try {
        return new Date(value).toISOString().slice(0, 10);
    } catch {
        return '-';
    }
};

const getClaimRef = (claim, fallbackIndex) => {
    return claim.claimNumber || claim.referenceNumber || `CLM-${String(fallbackIndex + 1).padStart(4, '0')}`;
};

const toNumber = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
};

const extractServices = (claim) => {
    const rawServices = claim.lines || claim.services || claim.claimServices || claim.items || claim.lineItems || [];
    if (Array.isArray(rawServices) && rawServices.length > 0) {
        return rawServices.map((line, idx) => {
            const gross = toNumber(line.totalPrice ?? line.totalAmount ?? line.claimedAmount ?? line.requestedAmount);
            const rejected = toNumber(line.refusedAmount ?? line.rejectedAmount);
            const approved = toNumber(line.approvedAmount ?? line.netAmount);
            const patientShare = toNumber(line.patientCoPay ?? line.copayAmount);
            const net = approved > 0 ? approved - patientShare : Math.max(gross - rejected - patientShare, 0);

            return {
                key: line.id || `${claim.id || 'c'}-${idx}`,
                serviceName: line.medicalServiceName || line.serviceName || line.name || line.description || line.medicalServiceCode || '-',
                date: fmtDate(line.serviceDate || claim.serviceDate || claim.claimDate || claim.createdAt),
                gross,
                net,
                rejected,
                rejectionReason: line.rejectionReason || line.notes || claim.rejectionReason || claim.reviewerComment || ''
            };
        });
    }

    const claimGross = toNumber(claim.requestedAmount ?? claim.totalAmount ?? claim.claimedAmount);
    const claimRejected = toNumber(
        claim.status === 'REJECTED' && !claim.refusedAmount
            ? claimGross
            : (claim.refusedAmount ?? claim.rejectedAmount)
    );
    const claimApproved = toNumber(claim.approvedAmount ?? claim.netAmount);
    const claimPatientShare = toNumber(claim.patientCoPay ?? claim.copayAmount);
    const claimNet = claimApproved > 0 ? claimApproved - claimPatientShare : Math.max(claimGross - claimRejected - claimPatientShare, 0);

    return [
        {
            key: `single-${claim.id || claim.claimNumber || 'claim'}`,
            serviceName: claim.serviceName || claim.medicalServiceName || 'Medical Services',
            date: fmtDate(claim.serviceDate || claim.claimDate || claim.createdAt),
            gross: claimGross,
            net: claimNet,
            rejected: claimRejected,
            rejectionReason: claim.rejectionReason || claim.reviewerComment || ''
        }
    ];
};

/**
 * Print report for claim batch, structured to match the physical layout in provided samples.
 */
const BatchPrintReport = forwardRef(({ claims, employer, provider, month, year, batchCode }, ref) => {
    const { getLogoSrc, settings } = useCompanySettings();
    const logoSrc = getLogoSrc();
    const fullTitle = buildCompanyTitle(settings?.companyName, settings?.businessType);

    if (!claims || claims.length === 0) return null;

    const detailedClaims = claims.map((claim, index) => {
        const services = extractServices(claim);
        const subtotalGross = services.reduce((s, row) => s + row.gross, 0);
        const subtotalNet = services.reduce((s, row) => s + row.net, 0);
        const subtotalRejected = services.reduce((s, row) => s + row.rejected, 0);

        return {
            claim,
            claimRef: getClaimRef(claim, index),
            originRef: claim.originNumber || claim.originNo || claim.visitNumber || '-',
            memberNumber: claim.memberCardNumber || claim.memberNationalNumber || claim.memberCivilId || claim.memberId || '-',
            memberName: claim.memberName || claim.memberFullName || claim.member?.fullName || claim.member?.name || '-',
            complaint: claim.complaint || claim.chiefComplaint || claim.diagnosisDescription || '-',
            diagnosis: claim.diagnosisDescription || claim.diagnosisCode || claim.primaryDiagnosis || '-',
            services,
            subtotalGross,
            subtotalNet,
            subtotalRejected
        };
    });

    let totalGross = 0;
    let totalRejected = 0;
    let totalPatientShare = 0;
    let totalNet = 0;

    detailedClaims.forEach((item) => {
        totalGross += item.subtotalGross;
        totalRejected += item.subtotalRejected;
        totalNet += item.subtotalNet;
        totalPatientShare += Math.max(item.subtotalGross - item.subtotalRejected - item.subtotalNet, 0);
    });

    const printDate = new Date().toISOString().split('T')[0];
    const totalClaimsCount = detailedClaims.length;

    const introText = REPORT_TEXT.intro.replace('{TOTAL_GROSS}', fmtAmount(totalGross));

    return (
        <div ref={ref} style={{ position: 'fixed', bottom: '-9999px', left: 0, zIndex: -1, width: '210mm', overflow: 'hidden' }} className="print-content-wrapper">
            <style type="text/css" media="print">
                {`
                    @page { size: A4 portrait; margin: 11mm; }
                    body {
                        -webkit-print-color-adjust: exact !important;
                        print-color-adjust: exact !important;
                        font-family: 'Tahoma', 'Arial', sans-serif;
                        direction: rtl;
                    }
                    .print-content-wrapper {
                        display: block !important;
                        padding: 0;
                        margin: 0;
                        background: #fff !important;
                        color: #1b2540;
                    }
                    .a4-sheet {
                        width: '6.25rem'%;
                        min-height: 0;
                    }
                    .page-break {
                        break-after: page;
                        page-break-after: always;
                    }
                    .print-header {
                        text-align: center;
                        margin-bottom: 22px;
                    }
                    .print-header img {
                        width: auto;
                        height: 54px;
                        margin-bottom: 8px;
                    }
                    .print-title {
                        font-size: 24px;
                        font-weight: 700;
                        color: #22335a;
                        margin-bottom: 4px;
                        line-height: '0.084375rem';
                    }
                    .print-batch-code {
                        font-size: 16px;
                        color: #4d5f85;
                        font-weight: 700;
                        direction: ltr;
                        display: none;
                    }
                    .meta-info {
                        display: flex;
                        justify-content: space-between;
                        font-size: 11px;
                        color: #4f5a70;
                        margin-bottom: 24px;
                    }
                    .cover-recipient {
                        font-weight: 700;
                        font-size: 14px;
                        margin: 24px 0 12px 0;
                        text-align: center;
                    }
                    .cover-body {
                        line-height: '0.125rem';
                        font-size: 13px;
                        margin-bottom: 14px;
                    }
                    .summary-section {
                        margin-top: 20px;
                        font-size: 13px;
                        line-height: '0.125rem';
                        margin-right: 10px;
                    }
                    .summary-line {
                        margin-bottom: 6px;
                    }
                    .summary-line strong {
                        margin-left: 10px;
                    }
                    .cover-note {
                        margin-top: 28px;
                        font-size: 13px;
                    }
                    .cover-closing {
                        margin-top: 26px;
                        font-size: 13px;
                        text-align: center;
                        line-height: '0.11875rem';
                    }

                    .patient-block {
                        border: 1px solid #bfc7d1;
                        margin-bottom: 14px;
                        font-size: 10.5px;
                        break-inside: avoid-page;
                    }
                    .patient-header {
                        display: flex;
                        border-bottom: 1px solid #ccd3de;
                        background: #f7f9fc;
                    }
                    .ph-col {
                        flex: 1;
                        padding: 5px 10px;
                        border-left: 1px solid #ccd3de;
                        min-height: 30px;
                    }
                    .ph-col:last-child { border-left: none; }
                    .ph-details { display: flex; border-bottom: 1px solid #ccc; }
                    .ph-details-row { flex: 1; display: flex; padding: 4px 10px; border-left: 1px solid #ccc; }
                    .ph-details-row:last-child { border-left: none; }
                    
                    table.print-table {
                        width: '6.25rem'%;
                        border-collapse: collapse;
                        font-size: 10px;
                        text-align: center;
                        direction: ltr;
                    }
                    table.print-table th {
                        padding: 6px;
                        border: 1px solid #9fa8b6;
                        font-weight: 700;
                        background: #eef2f7 !important;
                        color: #111 !important;
                    }
                    table.print-table td {
                        padding: 5px;
                        border: 1px solid #b4bdcb;
                        color: #111 !important;
                    }
                    .service-cell,
                    .reason-cell {
                        direction: rtl;
                        text-align: right;
                    }
                    table.print-table .subtotal-row td {
                        font-weight: 700;
                        border-top: 2px solid #6d778a;
                    }

                    .sub-gross { background: #6fc2ff !important; }
                    .sub-net { background: #8de4dc !important; }
                    .sub-rejected { background: #ff94bd !important; }

                    .global-total { display: flex; justify-content: space-between; margin-top: 18px; font-size: 12.5px; font-weight: 700; direction: ltr; }
                    .gt-col { flex: 1; padding: 8px; text-align: center; border: 1px solid #8f9fb3; }
                    .gt-title { background: #f2f4f8 !important; }
                    .gt-col.gross { background: #6fc2ff !important; }
                    .gt-col.net { background: #8de4dc !important; }
                    .gt-col.rejected { background: #ff94bd !important; }
                    
                    /* Hide everything else on the page during print - only show this component */
                    @media print {
                        body * { visibility: hidden; }
                        .print-content-wrapper, .print-content-wrapper * { visibility: visible; }
                        .print-content-wrapper { position: absolute; left: 0; top: 0; width: '6.25rem'%; bottom: auto; overflow: visible; }
                    }
                `}
            </style>

            <div className="a4-sheet page-break">
                <div className="print-header">
                    <img src={logoSrc} alt="logo" style={{ height: '4.0rem', width: 'auto', objectFit: 'contain' }} />
                    <div className="print-title">{fullTitle}</div>
                </div>

                <div className="meta-info">
                    <div style={{ textAlign: 'right' }}>
                        {printDate} :التاريخ<br />
                        1 :الصفحة
                    </div>
                    <div style={{ textAlign: 'left', direction: 'ltr' }}> </div>
                </div>

                <div className="cover-recipient">
                    {REPORT_TEXT.recipientPrefix} {provider?.name || '________________'}
                </div>

                <div className="cover-body">
                    {introText}
                </div>

                <div className="summary-section">
                    <div className="summary-line">• إجمالي القيمة المقدمة من المرفق: <strong>{fmtAmount(totalGross)}</strong></div>
                    <div className="summary-line">• إجمالي القيمة الغير مستحقة (المرفوضة): <strong>{fmtAmount(totalRejected)}</strong></div>
                    <div className="summary-line">• إجمالي نصيب المؤمن: <strong>{fmtAmount(totalPatientShare)}</strong></div>
                    <div className="summary-line">• صافي القيمة المستحقة للمرفق: <strong>{fmtAmount(totalNet)}</strong></div>
                    <div className="summary-line">• عدد المطالبات المستلمة: <strong>[{totalClaimsCount}]</strong></div>
                </div>

                <div className="cover-note">
                    {REPORT_TEXT.note}
                </div>

                <div className="cover-closing">
                    {REPORT_TEXT.closing}<br />
                    <strong>{REPORT_TEXT.closingDept}</strong>
                </div>
            </div>

            {detailedClaims.map((item, pIdx) => (
                <div key={pIdx} className={`a4-sheet ${pIdx < detailedClaims.length - 1 ? 'page-break' : ''}`}>
                    <div className="print-header" style={{ marginBottom: '0.75rem' }}>
                        <img src={logoSrc} alt="logo" style={{ height: '3.125rem', width: 'auto', objectFit: 'contain', marginBottom: '0.3125rem' }} />
                        <div className="print-title" style={{ fontSize: '0.875rem' }}>{fullTitle}</div>
                    </div>

                    <div className="meta-info" style={{ marginBottom: '0.75rem' }}>
                        <div>{printDate} :التاريخ</div>
                        <div style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>{provider?.name || '-'}</div>
                    </div>

                    <div className="patient-block" style={{ marginBottom: 0 }}>
                        <div className="patient-header">
                            <div className="ph-col" style={{ flex: 0.8 }}><strong>No.:</strong> <br /> {item.claimRef}</div>
                            <div className="ph-col"><strong>Origin No.:</strong> <br /> {item.originRef}</div>
                        </div>
                        <div className="ph-details">
                            <div className="ph-details-row"><strong>Insurance Number:</strong> &nbsp; {item.memberNumber}</div>
                            <div className="ph-details-row"><strong>Patient Name:</strong> &nbsp; {item.memberName}</div>
                        </div>
                        <div className="ph-details">
                            <div className="ph-details-row"><strong>Complaint:</strong> &nbsp; {item.complaint}</div>
                            <div className="ph-details-row"><strong>Diagnosis:</strong> &nbsp; {item.diagnosis}</div>
                        </div>

                        <table className="print-table">
                            <thead>
                                <tr>
                                    <th style={{ width: '35%' }}>Medical Service</th>
                                    <th style={{ width: '15%' }}>Date</th>
                                    <th style={{ width: '12%' }}>Gross</th>
                                    <th style={{ width: '12%' }}>Net</th>
                                    <th style={{ width: '12%' }}>Rejected</th>
                                    <th style={{ width: '24%' }}>Rejection Reason</th>
                                </tr>
                            </thead>
                            <tbody>
                                {item.services.map((srv, sIdx) => (
                                    <tr key={sIdx}>
                                        <td className="service-cell">{srv.serviceName}</td>
                                        <td>{srv.date}</td>
                                        <td>{fmtAmount(srv.gross)}</td>
                                        <td>{fmtAmount(srv.net)}</td>
                                        <td>{fmtAmount(srv.rejected)}</td>
                                        <td className="reason-cell" style={{ fontSize: '0.75rem' }}>{srv.rejectionReason || ''}</td>
                                    </tr>
                                ))}
                                <tr className="subtotal-row">
                                    <td colSpan={2} style={{ textAlign: 'left', paddingRight: '1.25rem' }}>SUBTOTAL</td>
                                    <td className="sub-gross">{fmtAmount(item.subtotalGross)}</td>
                                    <td className="sub-net">{fmtAmount(item.subtotalNet)}</td>
                                    <td className="sub-rejected">{fmtAmount(item.subtotalRejected)}</td>
                                    <td></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    {pIdx === detailedClaims.length - 1 && (
                        <div className="global-total">
                            <div className="gt-col gt-title">TOTAL</div>
                            <div className="gt-col gross">Gross<br />{fmtAmount(totalGross)}</div>
                            <div className="gt-col net">Net<br />{fmtAmount(totalNet)}</div>
                            <div className="gt-col rejected">Rejected<br />{fmtAmount(totalRejected)}</div>
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
});

export default BatchPrintReport;



