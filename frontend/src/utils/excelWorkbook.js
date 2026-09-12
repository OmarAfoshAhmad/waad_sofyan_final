/**
 * Plain workbook I/O on top of ExcelJS -- the one spreadsheet library this
 * app ships. Two primitives only:
 *
 *   readWorkbookSheets(arrayBuffer)  -> [{ name, rows: any[][] }]
 *   downloadWorkbook(sheets, filename) with sheets = [{ name, rows: object[] }]
 *
 * Styled, branded report exports live in exportUtils.js and friends; this
 * module is for raw data in and raw data out (imports, templates, lookups).
 *
 * ExcelJS is loaded on demand: it is the single largest chunk in the build
 * and only import/export flows need it.
 */

const loadExcelJS = async () => (await import('exceljs')).default;

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/**
 * Collapse ExcelJS's cell value shapes (rich text, formulas, hyperlinks,
 * error cells) to the plain value a row-scanning parser expects. Empty cells
 * become '' so column indexes stay stable across sparse rows.
 */
const plainCellValue = (value) => {
  if (value == null) return '';
  if (typeof value !== 'object' || value instanceof Date) return value;
  if (Array.isArray(value.richText)) return value.richText.map((part) => part.text ?? '').join('');
  if ('result' in value) return plainCellValue(value.result);
  if ('text' in value) return plainCellValue(value.text);
  if ('error' in value) return '';
  return String(value);
};

/**
 * Every sheet as a dense array-of-arrays (row-major, 0-indexed), the same
 * shape the price-list detectors were written against.
 */
export const readWorkbookSheets = async (arrayBuffer) => {
  const ExcelJS = await loadExcelJS();
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(arrayBuffer);

  const sheets = [];
  workbook.eachSheet((worksheet) => {
    const rows = [];
    worksheet.eachRow({ includeEmpty: true }, (row, rowNumber) => {
      // row.values is 1-indexed and sparse; slice the leading hole and
      // fill the rest so row[i] always addresses column i.
      const values = Array.from(row.values).slice(1);
      rows[rowNumber - 1] = values.map(plainCellValue);
    });
    for (let i = 0; i < rows.length; i += 1) if (!rows[i]) rows[i] = [];
    sheets.push({ name: worksheet.name, rows });
  });
  return sheets;
};

/** Header order = keys in order of first appearance across all rows. */
const headersOf = (rows) => {
  const keys = [];
  const seen = new Set();
  rows.forEach((row) => {
    Object.keys(row || {}).forEach((key) => {
      if (!seen.has(key)) {
        seen.add(key);
        keys.push(key);
      }
    });
  });
  return keys;
};

const triggerDownload = (buffer, filename) => {
  const blob = new Blob([buffer], { type: XLSX_MIME });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename.endsWith('.xlsx') ? filename : `${filename}.xlsx`;
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/**
 * One or more sheets of plain objects as an .xlsx buffer. Each sheet gets a
 * header row from its objects' keys, then one row per object; RTL view
 * because the workbook is for Arabic-speaking users.
 */
export const buildWorkbookBuffer = async (sheets) => {
  const ExcelJS = await loadExcelJS();
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'TBA WAAD System';
  workbook.created = new Date();

  sheets.forEach(({ name, rows = [] }) => {
    const worksheet = workbook.addWorksheet(name, { views: [{ rightToLeft: true }] });
    const headers = headersOf(rows);
    if (headers.length === 0) return;
    worksheet.addRow(headers);
    rows.forEach((row) => worksheet.addRow(headers.map((key) => row?.[key] ?? '')));
  });

  return workbook.xlsx.writeBuffer();
};

/** buildWorkbookBuffer, then hand the file to the browser. */
export const downloadWorkbook = async (sheets, filename) => {
  triggerDownload(await buildWorkbookBuffer(sheets), filename);
};
