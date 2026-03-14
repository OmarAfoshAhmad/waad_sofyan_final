import ExcelJS from 'exceljs';

const downloadWorkbook = async (workbook, filename) => {
  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  });

  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename}.xlsx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};

export const exportProviderAccountTransactionsToExcel = async ({ providerName, transactions, exportDate }) => {
  const rows = Array.isArray(transactions) ? transactions : [];

  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'TBA WAAD System';
  workbook.created = new Date();

  const worksheet = workbook.addWorksheet('حركات الحساب', {
    views: [{ rightToLeft: true }],
    properties: { defaultRowHeight: 22 }
  });

  worksheet.columns = [
    { header: 'التاريخ', key: 'createdAt', width: '1.5rem' },
    { header: 'نوع الحركة', key: 'transactionTypeLabel', width: '1.0rem' },
    { header: 'الدائن', key: 'creditAmount', width: '0.875rem' },
    { header: 'المدين', key: 'debitAmount', width: '0.875rem' },
    { header: 'رصيد الحركة', key: 'runningBalanceAfter', width: '1.0rem' },
    { header: 'المرجع', key: 'reference', width: '1.125rem' },
    { header: 'الوصف', key: 'description', width: '4.375rem' }
  ];

  worksheet.mergeCells('A1:G1');
  worksheet.getCell('A1').value = `حركات حساب مقدم الخدمة - ${providerName || '-'}`;
  worksheet.getCell('A1').font = { bold: true, size: 14 };
  worksheet.getCell('A1').alignment = { horizontal: 'center', vertical: 'middle' };

  worksheet.mergeCells('A2:G2');
  worksheet.getCell('A2').value = `تاريخ التصدير: ${exportDate || new Date().toLocaleString('ar-LY')}`;
  worksheet.getCell('A2').font = { size: 10, color: { argb: 'FF666666' } };
  worksheet.getCell('A2').alignment = { horizontal: 'center', vertical: 'middle' };

  const headerRowNumber = 4;
  const headerRow = worksheet.getRow(headerRowNumber);
  headerRow.eachCell((cell) => {
    cell.font = { bold: true, color: { argb: 'FFFFFFFF' } };
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0D9488' } };
    cell.alignment = { horizontal: 'center', vertical: 'middle' };
    cell.border = {
      top: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      left: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      bottom: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      right: { style: 'thin', color: { argb: 'FFCBD5E1' } }
    };
  });

  rows.forEach((tx) => {
    const row = worksheet.addRow({
      createdAt: tx.createdAt,
      transactionTypeLabel: tx.transactionTypeLabel,
      creditAmount: tx.creditAmount,
      debitAmount: tx.debitAmount,
      runningBalanceAfter: tx.runningBalanceAfter,
      reference: tx.reference,
      description: tx.description
    });

    row.eachCell((cell, colNumber) => {
      cell.border = {
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
      };
      if (colNumber === 3 || colNumber === 4 || colNumber === 5) {
        cell.numFmt = '#,##0.00';
        cell.alignment = { horizontal: 'right', vertical: 'middle' };
      } else {
        cell.alignment = { horizontal: colNumber === 7 ? 'right' : 'center', vertical: 'middle', wrapText: colNumber === 7 };
      }
    });
  });

  const totals = rows.reduce(
    (acc, tx) => {
      acc.credit += Number(tx.creditAmount) || 0;
      acc.debit += Number(tx.debitAmount) || 0;
      return acc;
    },
    { credit: 0, debit: 0 }
  );

  const totalsRow = worksheet.addRow({
    createdAt: '',
    transactionTypeLabel: '',
    creditAmount: totals.credit,
    debitAmount: totals.debit,
    runningBalanceAfter: totals.credit - totals.debit,
    reference: '',
    description: ''
  });

  totalsRow.eachCell((cell, colNumber) => {
    cell.font = { bold: true };
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF1F5F9' } };
    cell.border = {
      top: { style: 'thin', color: { argb: 'FF94A3B8' } },
      left: { style: 'thin', color: { argb: 'FF94A3B8' } },
      bottom: { style: 'thin', color: { argb: 'FF94A3B8' } },
      right: { style: 'thin', color: { argb: 'FF94A3B8' } }
    };
    if (colNumber === 3 || colNumber === 4 || colNumber === 5) {
      cell.numFmt = '#,##0.00';
      cell.alignment = { horizontal: 'right', vertical: 'middle' };
    }
  });

  worksheet.views = [{ state: 'frozen', xSplit: 0, ySplit: headerRowNumber }];

  const datePart = new Date().toISOString().slice(0, 10);
  await downloadWorkbook(workbook, `حركات_${providerName || 'provider'}_${datePart}`);
};

export const exportAccountsListToExcel = async ({ accounts = [] }) => {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'TBA WAAD System';
  workbook.created = new Date();

  const worksheet = workbook.addWorksheet('قائمة الحسابات', {
    views: [{ rightToLeft: true }],
    properties: { defaultRowHeight: 22 }
  });

  worksheet.columns = [
    { header: 'مقدم الخدمة', key: 'providerName', width: '1.875rem' },
    { header: 'النوع', key: 'providerType', width: '1.0rem' },
    { header: 'الرصيد الحالي', key: 'runningBalance', width: '1.125rem' },
    { header: 'إجمالي المعتمد', key: 'totalApproved', width: '1.125rem' },
    { header: 'إجمالي المدفوع', key: 'totalPaid', width: '1.125rem' },
    { header: 'فجوة السداد', key: 'gapAmount', width: '1.0rem' },
    { header: 'نسبة السداد %', key: 'coveragePercent', width: '0.875rem' },
    { header: 'آخر حركة', key: 'lastActivityAt', width: '1.0rem' },
    { header: 'مطالبات معلقة', key: 'pendingClaimsCount', width: '0.875rem' }
  ];

  worksheet.mergeCells('A1:I1');
  worksheet.getCell('A1').value = 'الدفعات المالية لمقدمي الخدمة';
  worksheet.getCell('A1').font = { bold: true, size: 14 };
  worksheet.getCell('A1').alignment = { horizontal: 'center', vertical: 'middle' };

  worksheet.mergeCells('A2:I2');
  worksheet.getCell('A2').value = `تاريخ التصدير: ${new Date().toLocaleString('ar-LY')}`;
  worksheet.getCell('A2').font = { size: 10, color: { argb: 'FF666666' } };
  worksheet.getCell('A2').alignment = { horizontal: 'center', vertical: 'middle' };

  const headerRowNumber = 4;
  const headerRow = worksheet.getRow(headerRowNumber);
  headerRow.eachCell((cell) => {
    cell.font = { bold: true, color: { argb: 'FFFFFFFF' } };
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0A4D8C' } };
    cell.alignment = { horizontal: 'center', vertical: 'middle' };
    cell.border = {
      top: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      left: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      bottom: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      right: { style: 'thin', color: { argb: 'FFCBD5E1' } }
    };
  });

  accounts.forEach((acc) => {
    const row = worksheet.addRow({
      providerName: acc.providerName || `مقدم خدمة #${acc.providerId}`,
      providerType: acc.providerType || '-',
      runningBalance: Number(acc.runningBalance) || 0,
      totalApproved: Number(acc.totalApproved) || 0,
      totalPaid: Number(acc.totalPaid) || 0,
      gapAmount: Number(acc.gapAmount) || Math.max((Number(acc.totalApproved) || 0) - (Number(acc.totalPaid) || 0), 0),
      coveragePercent:
        Number(acc.coveragePercent) ||
        ((Number(acc.totalApproved) || 0) > 0 ? ((Number(acc.totalPaid) || 0) / (Number(acc.totalApproved) || 0)) * 100 : 0),
      lastActivityAt: acc.lastActivityAt || acc.updatedAt || acc.createdAt || '-',
      pendingClaimsCount: acc.pendingClaimsCount ?? acc.transactionCount ?? 0
    });

    row.eachCell((cell, colNumber) => {
      cell.border = {
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
      };
      if (colNumber >= 3 && colNumber <= 6) {
        cell.numFmt = '#,##0.00';
        cell.alignment = { horizontal: 'right', vertical: 'middle' };
      } else if (colNumber === 7) {
        cell.numFmt = '0.0';
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      } else {
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      }
    });
  });

  worksheet.views = [{ state: 'frozen', xSplit: 0, ySplit: headerRowNumber }];

  const datePart = new Date().toISOString().slice(0, 10);
  await downloadWorkbook(workbook, `الدفعات_المالية_${datePart}`);
};

export default {
  exportProviderAccountTransactionsToExcel,
  exportAccountsListToExcel
};
