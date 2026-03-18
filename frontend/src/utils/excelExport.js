/**
 * Professional Excel Export Utility
 * تصدير Excel احترافي مع هيدر وفوتر شركة
 *
 * Features:
 * - Company header with logo info
 * - Styled table headers
 * - Professional footer with company contact
 * - RTL support for Arabic
 * - Auto column width
 *
 * @since 2026-01-18
 */

import ExcelJS from 'exceljs';



/**
 * Get company settings from localStorage (same key as CompanySettingsContext)
 */
const getCompanySettings = () => {
  try {
    const stored = localStorage.getItem('companySettings');
    if (stored) {
      return JSON.parse(stored);
    }
  } catch (e) {
    console.warn('[ExcelExport] Failed to load company settings:', e);
  }
  return {
    companyName: 'نظام وعد للتأمين الصحي',
    address: '',
    phone: '',
    email: '',
    website: '',
    footerText: ''
  };
};

// ============================================================================
// STYLE DEFINITIONS
// ============================================================================

const STYLES = {
  // Company name style
  companyName: {
    font: { bold: true, size: 18, color: { argb: 'FF1976D2' } },
    alignment: { horizontal: 'center', vertical: 'middle' }
  },
  // Report title style
  reportTitle: {
    font: { bold: true, size: 14, color: { argb: 'FF333333' } },
    alignment: { horizontal: 'center', vertical: 'middle' }
  },
  // Report info (date, count)
  reportInfo: {
    font: { size: 10, color: { argb: 'FF666666' } },
    alignment: { horizontal: 'center', vertical: 'middle' }
  },
  // Table header
  tableHeader: {
    font: { bold: true, size: 11, color: { argb: 'FFFFFFFF' } },
    fill: { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF1976D2' } },
    alignment: { horizontal: 'center', vertical: 'middle', wrapText: true },
    border: {
      top: { style: 'thin', color: { argb: 'FF1565C0' } },
      left: { style: 'thin', color: { argb: 'FF1565C0' } },
      bottom: { style: 'thin', color: { argb: 'FF1565C0' } },
      right: { style: 'thin', color: { argb: 'FF1565C0' } }
    }
  },
  // Table cell (alternating colors)
  tableCell: {
    font: { size: 10 },
    alignment: { horizontal: 'right', vertical: 'middle', wrapText: true },
    border: {
      top: { style: 'thin', color: { argb: 'FFE0E0E0' } },
      left: { style: 'thin', color: { argb: 'FFE0E0E0' } },
      bottom: { style: 'thin', color: { argb: 'FFE0E0E0' } },
      right: { style: 'thin', color: { argb: 'FFE0E0E0' } }
    }
  },
  tableCellEven: {
    fill: { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF5F5F5' } }
  },
  // Footer style
  footer: {
    font: { size: 9, color: { argb: 'FF888888' }, italic: true },
    alignment: { horizontal: 'center', vertical: 'middle' }
  }
};

// ============================================================================
// MAIN EXPORT FUNCTION
// ============================================================================

/**
 * Export data to professional Excel file with company branding
 *
 * @param {Object} options Export options
 * @param {string} options.title - Report title (Arabic)
 * @param {string} options.titleEn - Report title (English, optional)
 * @param {string} options.filename - Output filename (without extension)
 * @param {Array} options.columns - Column definitions [{key, header, width, type}]
 * @param {Array} options.data - Data rows
 * @param {string} options.sheetName - Worksheet name
 */
export const exportToExcel = async ({ title, titleEn = '', filename, columns, data, sheetName = 'البيانات' }) => {
  // Fetch company settings
  const company = await getCompanySettings();

  // Create workbook
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'نظام وعد للتأمين الصحي';
  workbook.created = new Date();

  // Create worksheet
  const worksheet = workbook.addWorksheet(sheetName, {
    views: [{ rightToLeft: true }], // RTL for Arabic
    properties: { defaultRowHeight: 20 }
  });

  const totalColumns = columns.length;

  // ========================================
  // HEADER SECTION
  // ========================================

  let currentRow = 1;

  // Row 1: Company Name
  worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
  const companyNameCell = worksheet.getCell(currentRow, 1);
  companyNameCell.value = company.companyName || 'نظام وعد للتأمين الصحي';
  Object.assign(companyNameCell, STYLES.companyName);
  worksheet.getRow(currentRow).height = 30;
  currentRow++;

  // Row 2: Company Address (if available)
  if (company.address) {
    worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
    const addressCell = worksheet.getCell(currentRow, 1);
    addressCell.value = company.address;
    addressCell.font = { size: 10, color: { argb: 'FF666666' } };
    addressCell.alignment = { horizontal: 'center', vertical: 'middle' };
    worksheet.getRow(currentRow).height = 18;
    currentRow++;
  }

  // Row 3: Contact Info (phone, email)
  const contactParts = [];
  if (company.phone) contactParts.push(`هاتف: ${company.phone}`);
  if (company.email) contactParts.push(`بريد: ${company.email}`);
  if (company.website) contactParts.push(`موقع: ${company.website}`);

  if (contactParts.length > 0) {
    worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
    const contactCell = worksheet.getCell(currentRow, 1);
    contactCell.value = contactParts.join('  |  ');
    contactCell.font = { size: 9, color: { argb: 'FF888888' } };
    contactCell.alignment = { horizontal: 'center', vertical: 'middle' };
    worksheet.getRow(currentRow).height = 18;
    currentRow++;
  }

  // Empty row separator
  currentRow++;

  // Row: Report Title
  worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
  const titleCell = worksheet.getCell(currentRow, 1);
  titleCell.value = titleEn ? `${title} / ${titleEn}` : title;
  Object.assign(titleCell, STYLES.reportTitle);
  worksheet.getRow(currentRow).height = 25;
  currentRow++;

  // Row: Report Info (date, count)
  worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
  const infoCell = worksheet.getCell(currentRow, 1);
  const reportDate = new Date().toLocaleDateString('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
  infoCell.value = `تاريخ التقرير: ${reportDate}  |  عدد السجلات: ${data.length}`;
  Object.assign(infoCell, STYLES.reportInfo);
  worksheet.getRow(currentRow).height = 20;
  currentRow++;

  // Empty row before table
  currentRow++;

  // ========================================
  // TABLE HEADERS
  // ========================================

  const headerRow = currentRow;

  // Set column definitions
  worksheet.columns = columns.map((col, index) => ({
    key: col.key,
    width: col.width || 15
  }));

  // Add header row
  const headerRowObj = worksheet.getRow(headerRow);
  columns.forEach((col, index) => {
    const cell = headerRowObj.getCell(index + 1);
    cell.value = col.header;
    Object.assign(cell, STYLES.tableHeader);
  });
  headerRowObj.height = 28;
  currentRow++;

  // ========================================
  // DATA ROWS
  // ========================================

  data.forEach((row, rowIndex) => {
    const dataRowObj = worksheet.getRow(currentRow);

    columns.forEach((col, colIndex) => {
      const cell = dataRowObj.getCell(colIndex + 1);
      let value = row[col.key];

      // Format value based on type
      if (col.type === 'number' && value !== null && value !== undefined) {
        cell.value = Number(value);
        cell.numFmt = col.format || '#,##0.00';
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      } else if (col.type === 'currency' && value !== null && value !== undefined) {
        cell.value = Number(value);
        cell.numFmt = '#,##0.00 "د.ل"';
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      } else if (col.type === 'boolean') {
        cell.value = value ? 'نعم' : 'لا';
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      } else {
        cell.value = value ?? '-';
        cell.alignment = { horizontal: col.align || 'right', vertical: 'middle', wrapText: true };
      }

      // Apply cell styles
      Object.assign(cell, STYLES.tableCell);

      // Alternating row colors
      if (rowIndex % 2 === 1) {
        cell.fill = STYLES.tableCellEven.fill;
      }
    });

    dataRowObj.height = 22;
    currentRow++;
  });

  // ========================================
  // FOOTER SECTION
  // ========================================

  // Empty row before footer
  currentRow++;
  currentRow++;

  // Footer: Company footer text
  if (company.footerText) {
    worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
    const footerTextCell = worksheet.getCell(currentRow, 1);
    footerTextCell.value = company.footerText;
    Object.assign(footerTextCell, STYLES.footer);
    currentRow++;
  }

  // Footer: Generated info
  worksheet.mergeCells(currentRow, 1, currentRow, totalColumns);
  const generatedCell = worksheet.getCell(currentRow, 1);
  generatedCell.value = `تم إنشاء هذا التقرير بواسطة نظام وعد للتأمين الصحي - ${new Date().toLocaleDateString('en-US')}`;
  Object.assign(generatedCell, STYLES.footer);

  // ========================================
  // DOWNLOAD FILE
  // ========================================

  // Generate buffer
  const buffer = await workbook.xlsx.writeBuffer();

  // Create blob and download
  const blob = new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  });

  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename}-${new Date().toISOString().split('T')[0]}.xlsx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);

  return true;
};

// ============================================================================
// MEDICAL SERVICES SPECIFIC EXPORT
// ============================================================================

/**
 * Export Medical Services to Excel
 * تصدير الخدمات الطبية إلى Excel
 *
 * @param {Array} data - Medical services data
 */
export const exportMedicalServicesToExcel = async (data) => {
  const columns = [
    { key: 'code', header: 'الرمز', width: '0.9375rem', align: 'center' },
    { key: 'name', header: 'الاسم (عربي)', width: '2.1875rem', align: 'right' },
    { key: 'name', header: 'الاسم (إنجليزي)', width: '2.1875rem', align: 'left' },
    { key: 'categoryName', header: 'التصنيف', width: '1.25rem', align: 'right' },
    { key: 'basePrice', header: 'السعر (د.ل)', width: '0.9375rem', type: 'currency' },
    { key: 'requiresPA', header: 'موافقة مسبقة', width: '0.75rem', type: 'boolean' },
    { key: 'active', header: 'الحالة', width: '0.625rem', type: 'boolean' }
  ];

  // Transform data - handle name display logic
  const transformedData = data.map((item) => ({
    ...item,
    name: !item.name || item.name === item.name ? '-' : item.name,
    active: item.active ? 'نشط' : 'غير نشط',
    requiresPA: item.requiresPA ? 'نعم' : 'لا'
  }));

  return exportToExcel({
    title: 'قائمة الخدمات الطبية',
    titleEn: 'Medical Services List',
    filename: 'medical-services',
    columns,
    data: transformedData,
    sheetName: 'الخدمات الطبية'
  });
};

// ============================================================================
// MEDICAL CATEGORIES SPECIFIC EXPORT
// ============================================================================

/**
 * Export Medical Categories to Excel
 * تصدير التصنيفات الطبية إلى Excel
 *
 * @param {Array} data - Medical categories data
 */
export const exportMedicalCategoriesToExcel = async (data) => {
  const columns = [
    { key: 'code',       header: 'الرمز',         width: 18,  align: 'center' },
    { key: 'name',       header: 'الاسم',          width: 35,  align: 'right' },
    { key: 'parentName', header: 'التصنيف الأب',   width: 28,  align: 'right' },
    { key: 'active',     header: 'الحالة',         width: 14,  align: 'center' }
  ];

  const transformedData = data.map((item) => ({
    ...item,
    name: item.name || '-',
    parentName: item.parentName || '-',
    active: item.active ? 'نشط' : 'غير نشط'
  }));

  return exportToExcel({
    title: 'قائمة التصنيفات الطبية',
    titleEn: 'Medical Categories List',
    filename: 'medical-categories',
    columns,
    data: transformedData,
    sheetName: 'التصنيفات الطبية'
  });
};

export default exportToExcel;


