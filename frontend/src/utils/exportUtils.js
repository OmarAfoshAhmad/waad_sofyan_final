import ExcelJS from 'exceljs';

/**
 * Export Utilities
 * Unified export functions for Excel and PDF
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * FINANCIAL CLOSURE RULE - EXPORT = VIEW = API RESPONSE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 🚫 FORBIDDEN: Calculating totals or aggregates in this file
 * 🚫 FORBIDDEN: Transforming financial amounts before export
 * 🚫 FORBIDDEN: Creating new financial fields not in source data
 *
 * ✅ REQUIRED: Export uses EXACT data from API response
 * ✅ REQUIRED: No .reduce(), .sum(), or manual aggregation
 * ✅ REQUIRED: What user sees = What user exports
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPANY BRANDING - SINGLE SOURCE OF TRUTH
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * All exports include company branding (name, logo) from CompanySettingsContext.
 * To use branding, pass companySettings object to export functions.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */

/**
 * Export data to Excel (XLSX) file with company branding
 *
 * @param {Array<Object>} data - Array of objects to export
 * @param {string} filename - Filename without extension
 * @param {Object} options - Export options
 * @param {string} options.companyName - Company name for header
 * @param {string} options.reportTitle - Report title
 * @param {Array<{key:string, header:string, width?:number, type?:'string'|'number'|'currency'|'date'}>} options.columns - Explicit columns
 * @param {Object.<string, string>} options.columnLabels - Key to header label mapping
 * @param {Array<{label:string,value:string,bgColor?:string,textColor?:string}>} options.summaryCards - Optional colored summary cards section
 * @param {string[]} options.footerNotes - Optional footer notes appended at end of sheet
 */
const DEFAULT_COLUMN_LABELS = {
  id: 'المعرف',
  claimNumber: 'رقم المطالبة',
  memberName: 'اسم المؤمن عليه',
  employerName: 'الشريك',
  providerName: 'مقدم الخدمة',
  status: 'الحالة',
  requestedAmount: 'المبلغ المطلوب',
  approvedAmount: 'المبلغ المعتمد',
  settledAmount: 'المبلغ المسدد',
  settlementDate: 'تاريخ التسوية',
  paymentReference: 'مرجع الدفع',
  runningBalance: 'الرصيد الحالي',
  totalApproved: 'إجمالي المعتمد',
  totalPaid: 'إجمالي المدفوع',
  createdAt: 'تاريخ الإنشاء',
  updatedAt: 'آخر تحديث',
  visitDate: 'تاريخ الزيارة',
  requestDate: 'تاريخ الطلب',
  validUntil: 'صالح حتى'
};

const isNumericValue = (value) => typeof value === 'number' || (typeof value === 'string' && value !== '' && !Number.isNaN(Number(value)));

const humanizeKey = (key) => {
  const normalized = String(key || '')
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim();
  return normalized || String(key || '-');
};

const inferColumnType = (key, sampleRows) => {
  const keyLower = String(key).toLowerCase();
  if (keyLower.includes('date') || keyLower.endsWith('at')) return 'date';
  if (keyLower.includes('amount') || keyLower.includes('balance') || keyLower.includes('total') || keyLower.includes('price')) return 'currency';

  const firstDefined = sampleRows.map((row) => row?.[key]).find((value) => value !== null && value !== undefined && value !== '');
  if (isNumericValue(firstDefined)) return 'number';
  return 'string';
};

const getColumnWidth = (header, key, rows) => {
  const values = rows.slice(0, 100).map((row) => row?.[key]);
  const longestValueLength = values.reduce((maxLen, value) => {
    const text = value === null || value === undefined ? '' : String(value);
    return Math.max(maxLen, text.length);
  }, 0);
  return Math.min(Math.max(Math.max(String(header || '').length, longestValueLength) + 4, 14), 60);
};

const toArgb = (hexColor, fallback = 'FF0D9488') => {
  const normalized = String(hexColor || '').trim().replace('#', '');
  if (!normalized) return fallback;
  if (normalized.length === 6) return `FF${normalized.toUpperCase()}`;
  if (normalized.length === 8) return normalized.toUpperCase();
  return fallback;
};

const normalizeColumns = (data, options) => {
  const firstRow = data[0] || {};
  const keys = Object.keys(firstRow);

  if (Array.isArray(options.columns) && options.columns.length > 0) {
    return options.columns;
  }

  const customLabels = options.columnLabels || {};

  return keys.map((key) => {
    const header = customLabels[key] || DEFAULT_COLUMN_LABELS[key] || humanizeKey(key);
    const type = inferColumnType(key, data);
    const width = getColumnWidth(header, key, data);
    return { key, header, type, width };
  });
};

export const exportToExcel = async (data, filename = 'export', options = {}) => {
  if (!data || data.length === 0) {
    console.warn('No data to export');
    return;
  }

  const { companyName, reportTitle, summaryCards = [], footerNotes = [] } = options;
  const columns = normalizeColumns(data, options);

  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'TBA WAAD System';
  workbook.created = new Date();

  const worksheet = workbook.addWorksheet('البيانات', {
    views: [{ rightToLeft: true }],
    properties: { defaultRowHeight: 22 }
  });

  worksheet.columns = columns.map((column) => ({
    key: column.key,
    width: column.width || 16
  }));

  let currentRow = 1;
  const totalColumns = columns.length;

  if (companyName) {
    worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
    const companyCell = worksheet.getCell(currentRow, 1);
    companyCell.value = companyName;
    companyCell.font = { bold: true, size: 14, color: { argb: 'FF0F172A' } };
    companyCell.alignment = { horizontal: 'center', vertical: 'middle' };
    currentRow += 1;
  }

  if (reportTitle) {
    worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
    const titleCell = worksheet.getCell(currentRow, 1);
    titleCell.value = reportTitle;
    titleCell.font = { bold: true, size: 12, color: { argb: 'FF334155' } };
    titleCell.alignment = { horizontal: 'center', vertical: 'middle' };
    currentRow += 1;
  }

  worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
  const dateCell = worksheet.getCell(currentRow, 1);
  dateCell.value = `تاريخ التصدير: ${new Date().toLocaleString('ar-LY')}`;
  dateCell.font = { size: 10, color: { argb: 'FF64748B' } };
  dateCell.alignment = { horizontal: 'center', vertical: 'middle' };
  currentRow += 2;

  if (Array.isArray(summaryCards) && summaryCards.length > 0) {
    const cardCount = summaryCards.length;
    const span = Math.max(1, Math.floor(totalColumns / cardCount));
    let startCol = 1;

    summaryCards.forEach((card, index) => {
      const endCol = index === cardCount - 1 ? totalColumns : Math.min(totalColumns, startCol + span - 1);

      worksheet.mergeCells(currentRow, startCol, currentRow, endCol);
      const labelCell = worksheet.getCell(currentRow, startCol);
      labelCell.value = card?.label || '-';
      labelCell.font = { bold: true, size: 10, color: { argb: toArgb(card?.textColor, 'FF0F172A') } };
      labelCell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      labelCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: toArgb(card?.bgColor, 'FFE2E8F0') } };

      worksheet.mergeCells(currentRow + 1, startCol, currentRow + 1, endCol);
      const valueCell = worksheet.getCell(currentRow + 1, startCol);
      valueCell.value = card?.value || '-';
      valueCell.font = { bold: true, size: 12, color: { argb: toArgb(card?.textColor, 'FF0F172A') } };
      valueCell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      valueCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: toArgb(card?.bgColor, 'FFE2E8F0') } };

      for (let col = startCol; col <= endCol; col += 1) {
        const topCell = worksheet.getCell(currentRow, col);
        const bottomCell = worksheet.getCell(currentRow + 1, col);
        topCell.border = {
          top: { style: 'thin', color: { argb: 'FF94A3B8' } },
          left: { style: 'thin', color: { argb: 'FF94A3B8' } },
          bottom: { style: 'thin', color: { argb: 'FF94A3B8' } },
          right: { style: 'thin', color: { argb: 'FF94A3B8' } }
        };
        bottomCell.border = {
          top: { style: 'thin', color: { argb: 'FF94A3B8' } },
          left: { style: 'thin', color: { argb: 'FF94A3B8' } },
          bottom: { style: 'thin', color: { argb: 'FF94A3B8' } },
          right: { style: 'thin', color: { argb: 'FF94A3B8' } }
        };
      }

      startCol = endCol + 1;
    });

    currentRow += 3;
  }

  const headerRowNumber = currentRow;
  const headerRow = worksheet.getRow(headerRowNumber);
  columns.forEach((column, index) => {
    const cell = headerRow.getCell(index + 1);
    cell.value = column.header;
    cell.font = { bold: true, color: { argb: 'FFFFFFFF' } };
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0D9488' } };
    cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
    cell.border = {
      top: { style: 'thin', color: { argb: 'FF94A3B8' } },
      left: { style: 'thin', color: { argb: 'FF94A3B8' } },
      bottom: { style: 'thin', color: { argb: 'FF94A3B8' } },
      right: { style: 'thin', color: { argb: 'FF94A3B8' } }
    };
  });

  data.forEach((rowData) => {
    const row = worksheet.addRow(
      columns.reduce((acc, column) => {
        const value = rowData?.[column.key];
        acc[column.key] = value === undefined || value === null ? '' : value;
        return acc;
      }, {})
    );

    row.eachCell((cell, colNumber) => {
      const column = columns[colNumber - 1];
      if (column?.type === 'currency' && isNumericValue(cell.value)) {
        cell.value = Number(cell.value);
        cell.numFmt = '#,##0.00';
        cell.alignment = { horizontal: 'right', vertical: 'middle' };
      } else if (column?.type === 'number' && isNumericValue(cell.value)) {
        cell.value = Number(cell.value);
        cell.numFmt = '#,##0.00';
        cell.alignment = { horizontal: 'right', vertical: 'middle' };
      } else {
        if (typeof cell.value === 'object') {
          cell.value = JSON.stringify(cell.value);
        }
        cell.alignment = { horizontal: 'right', vertical: 'middle', wrapText: true };
      }

      cell.border = {
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
      };
    });
  });

  if (Array.isArray(footerNotes) && footerNotes.length > 0) {
    currentRow = worksheet.lastRow?.number ? worksheet.lastRow.number + 2 : currentRow + 2;
    footerNotes.forEach((note) => {
      worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
      const noteCell = worksheet.getCell(currentRow, 1);
      noteCell.value = note;
      noteCell.font = { bold: false, size: 10, color: { argb: 'FF0F172A' } };
      noteCell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      noteCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF8FAFC' } };
      currentRow += 1;
    });
  }

  worksheet.views = [{ state: 'frozen', xSplit: 0, ySplit: headerRowNumber }];

  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  });

  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.setAttribute('href', url);
  link.setAttribute('download', `${filename}.xlsx`);
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/**
 * Export data to PDF file
 * @param {Array<string>} columns - Column headers
 * @param {Array<Array>} rows - Row data
 * @param {string} title - Document title
 * @param {string} filename - Filename without extension
 */
/**
 * Export data to PDF file with company branding
 *
 * Supports two signatures:
 * 1. (data, title, filename, options) - Auto-extract columns from object keys
 * 2. (columns, rows, title, filename, options) - Explicit columns and rows
 *
 * @param {Object} options - Export options
 * @param {string} options.companyName - Company name for header
 * @param {string} options.logoBase64 - Base64 logo for header
 * @param {string} options.footerText - Footer text
 * @param {string} options.primaryColor - Primary color for styling
 */
export const exportToPDF = (arg1, arg2, arg3, arg4, arg5) => {
  let columns, rows, title, filename, options;

  // Determine signature: if arg1 is array of objects (not array of arrays)
  if (Array.isArray(arg1) && arg1.length > 0 && typeof arg1[0] === 'object' && !Array.isArray(arg1[0])) {
    // Signature 1: (data, title, filename, options)
    const data = arg1;
    columns = Object.keys(data[0]);
    rows = data.map((obj) => Object.values(obj));
    title = arg2 || 'Export';
    filename = arg3 || 'export';
    options = arg4 || {};
  } else {
    // Signature 2: (columns, rows, title, filename, options)
    columns = arg1 || [];
    rows = arg2 || [];
    title = arg3 || 'Export';
    filename = arg4 || 'export';
    options = arg5 || {};
  }

  if (!rows || rows.length === 0) {
    console.warn('No data to export');
    return;
  }

  // Extract branding options
  const { companyName, logoBase64, footerText, primaryColor = '#1976d2' } = options;

  // Ensure columns is defined
  if (!columns || (columns.length === 0 && rows.length > 0)) {
    columns = Array.from({ length: rows[0].length }, (_, i) => `Column ${i + 1}`);
  }

  // Build header with company branding
  const headerHTML =
    companyName || logoBase64
      ? `
    <div class="header">
      ${logoBase64 ? `<img src="${logoBase64}" alt="${companyName || 'Logo'}" class="logo" />` : ''}
      ${companyName ? `<div class="company-name">${companyName}</div>` : ''}
    </div>
  `
      : '';

  const tableHTML = `
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        * { box-sizing: border-box; }
        body {
          font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
          margin: 20px;
          direction: rtl;
        }
        .header {
          text-align: center;
          margin-bottom: 20px;
          padding-bottom: 15px;
          border-bottom: 2px solid ${primaryColor};
        }
        .logo {
          max-height: 60px;
          max-width: 200px;
          object-fit: contain;
        }
        .company-name {
          font-size: 18px;
          font-weight: bold;
          color: ${primaryColor};
          margin-top: 8px;
        }
        h1 {
          text-align: center;
          color: #333;
          margin-bottom: 20px;
        }
        table {
          width: '6.25rem'%;
          border-collapse: collapse;
          margin-bottom: 20px;
        }
        th, td {
          border: 1px solid #ddd;
          padding: 10px 8px;
          text-align: right;
        }
        th {
          background-color: ${primaryColor};
          color: white;
          font-weight: bold;
        }
        tr:nth-child(even) { background-color: #f9f9f9; }
        tr:hover { background-color: #f5f5f5; }
        .footer {
          text-align: center;
          color: #666;
          font-size: 12px;
          margin-top: 20px;
          padding-top: 15px;
          border-top: 1px solid #ddd;
        }
        @media print {
          body { margin: 0; }
          .no-print { display: none; }
        }
      </style>
    </head>
    <body>
      ${headerHTML}
      <h1>${title}</h1>
      <table>
        <thead>
          <tr>${columns.map((col) => `<th>${col}</th>`).join('')}</tr>
        </thead>
        <tbody>
          ${rows.map((row) => `<tr>${row.map((cell) => `<td>${cell ?? '-'}</td>`).join('')}</tr>`).join('')}
        </tbody>
      </table>
      <div class="footer">
        ${footerText || (companyName ? `${companyName} - ` : '')}تم الإنشاء: ${new Date().toLocaleDateString('en-US')} ${new Date().toLocaleTimeString('en-US')}
      </div>
    </body>
    </html>
  `;

  const printWindow = window.open('', '_blank');
  if (printWindow) {
    printWindow.document.write(tableHTML);
    printWindow.document.close();
    printWindow.focus();
    setTimeout(() => printWindow.print(), 250);
  } else {
    console.error('Could not open print window');
  }
};

export default { exportToExcel, exportToPDF };
