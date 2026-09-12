import { describe, expect, it } from 'vitest';
import { buildWorkbookBuffer, readWorkbookSheets } from '../excelWorkbook';

/**
 * These two primitives replaced SheetJS (xlsx), whose last npm release
 * carries a prototype-pollution and a ReDoS advisory with no fix. The
 * contract the callers were written against is the one asserted here:
 * header row from object keys in first-appearance order, one row per
 * object, and reads come back as dense arrays with '' for empty cells.
 */
// ExcelJS is ~1MB and is loaded on demand; the first import in a cold run can take seconds.
describe('excelWorkbook', { timeout: 30000 }, () => {
  it('round-trips plain objects through a real .xlsx buffer', async () => {
    const buffer = await buildWorkbookBuffer([
      {
        name: 'Pricing_Template',
        rows: [
          { service_name: 'تحليل CBC', service_code: 'SRV-001', contract_price: 25 },
          { service_name: 'رنين', contract_price: 900, notes: 'اختياري' }
        ]
      },
      { name: 'التصنيفات المتاحة', rows: [{ medical_category_id: 7, medical_category_code: 'CAT-LAB' }] }
    ]);

    const sheets = await readWorkbookSheets(buffer);

    expect(sheets.map((s) => s.name)).toEqual(['Pricing_Template', 'التصنيفات المتاحة']);
    expect(sheets[0].rows).toEqual([
      ['service_name', 'service_code', 'contract_price', 'notes'],
      ['تحليل CBC', 'SRV-001', 25, ''],
      ['رنين', '', 900, 'اختياري']
    ]);
    expect(sheets[1].rows).toEqual([
      ['medical_category_id', 'medical_category_code'],
      [7, 'CAT-LAB']
    ]);
  });

  it('reads sparse sheets as dense rows so column indexes stay stable', async () => {
    const ExcelJS = (await import('exceljs')).default;
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('الخدمات');
    sheet.getCell('A1').value = 'إقامة بغرفة فردية';
    sheet.getCell('C1').value = '$700.00';
    sheet.getCell('D1').value = 'إيواء';
    sheet.getCell('A3').value = { richText: [{ text: 'كشف ' }, { text: 'اختصاصي' }] };
    sheet.getCell('C3').value = { formula: '45*2', result: 90 };

    const [read] = await readWorkbookSheets(await workbook.xlsx.writeBuffer());

    expect(read.rows[0]).toEqual(['إقامة بغرفة فردية', '', '$700.00', 'إيواء']);
    expect(read.rows[1]).toEqual([]);
    expect(read.rows[2].slice(0, 3)).toEqual(['كشف اختصاصي', '', 90]);
  });

  it('skips a sheet with no rows rather than writing an empty header', async () => {
    const buffer = await buildWorkbookBuffer([{ name: 'فارغ', rows: [] }]);
    const [sheet] = await readWorkbookSheets(buffer);
    expect(sheet.name).toBe('فارغ');
    expect(sheet.rows).toEqual([]);
  });
});
