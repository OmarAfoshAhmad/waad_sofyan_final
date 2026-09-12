import { describe, expect, it } from 'vitest';
import { extractRowsFromWorkbook } from '../price-list-workbook.mjs';

// One sheet of literal rows -- the same shape readWorkbookSheets() yields.
const workbook = (rows, sheetName = 'الخدمات') => [{ name: sheetName, rows }];

describe('provider price-list workbook extraction', () => {
  it('preserves the classification in the headerless Benghazi layout', () => {
    const rows = extractRowsFromWorkbook(workbook([
      ['إقامة بغرفة فردية', 'ACC-44', '$700.00', 'إيواء', 'وصف'],
      ['كشف اختصاصي', 'OP-1', '$90.00', 'عيادات خارجية', 'وصف']
    ]));

    expect(rows).toMatchObject([
      { serviceCode: 'ACC-44', serviceName: 'إقامة بغرفة فردية', price: 700, sourceClassification: 'إيواء' },
      { serviceCode: 'OP-1', serviceName: 'كشف اختصاصي', price: 90, sourceClassification: 'عيادات خارجية' }
    ]);
  });

  it('detects Dar Al-Hikma Arabic statement, code, average price and classification columns', () => {
    const rows = extractRowsFromWorkbook(workbook([
      ['اسم المرفق', 'كود الخدمة', 'البيان بالعربي', 'البيان باللاتيني', 'السعر', 'التصنيف '],
      ['دار الحكمة', 'ACC-43', 'إقامة بغرفة زوجية', 'Room', '$600.00', 'إيواء']
    ]));

    expect(rows).toEqual([expect.objectContaining({
      serviceCode: 'ACC-43', serviceName: 'إقامة بغرفة زوجية', price: 600, sourceClassification: 'إيواء'
    })]);
  });

  it('detects Dar Al-Shifa insurance classification and separates its embedded provider code', () => {
    const rows = extractRowsFromWorkbook(workbook([
      ['اسم المرفق', 'الخدمه', 'السعر', 'التصنيف التأميني'],
      ['دار الشفاء', 'WE-007 جلسة علاج طبيعي', '50', 'علاج طبيعي']
    ], 'Sheet'));

    expect(rows).toEqual([expect.objectContaining({
      serviceCode: 'WE-007', serviceName: 'جلسة علاج طبيعي', price: 50, sourceClassification: 'علاج طبيعي'
    })]);
  });

  it('does not inherit a classification across an explicit classification change', () => {
    const rows = extractRowsFromWorkbook(workbook([
      ['اسم الخدمة', 'السعر', 'التصنيف'],
      ['تحليل دم', 20, 'أشعة تحاليل رسوم أطباء'],
      ['صورة أشعة', 30, 'أشعة تحاليل رسوم أطباء'],
      ['إقامة', 100, 'إيواء']
    ]));

    expect(rows.map((row) => row.sourceClassification)).toEqual([
      'أشعة تحاليل رسوم أطباء', 'أشعة تحاليل رسوم أطباء', 'إيواء'
    ]);
  });
});
