/**
 * AppearanceInjector — يحقن متغيرات CSS الديناميكية للمظهر
 *
 * يقرأ إعدادات المظهر من CompanySettingsContext (localStorage) ويطبّقها مباشرة
 * على متغيرات CSS في عنصر <html> (inline style → أعلى أولوية).
 *
 * المتغيرات المُحقَنة:
 *   --tba-th-bg        → خلفية ترويسة الجداول
 *   --tba-th-text      → لون نص الترويسة
 *   --tba-row-even     → خلفية الصفوف الزوجية
 *   --tba-selection    → لون تحديد الصفوف
 *   --tba-primary      → اللون الرئيسي (أزرار، أيقونات)
 *   --palette-primary-main → يُغيِّر لون MUI primary عند وقت التشغيل
 */

import { useEffect } from 'react';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

const STYLE_ID = 'tba-appearance-rules';

const DEFAULTS = {
  tableHeaderBg: '#E0F2F1',
  tableHeaderText: '#004D50',
  tableRowEven: 'rgba(224,242,241,0.45)',
  selectionColor: 'rgba(0,131,143,0.08)',
  primaryColor: '#00838F'
};

export function AppearanceInjector() {
  const { settings } = useCompanySettings();

  useEffect(() => {
    const root = document.documentElement;
    const s = settings || {};

    const thBg    = s.tableHeaderBg  || DEFAULTS.tableHeaderBg;
    const thText  = s.tableHeaderText || DEFAULTS.tableHeaderText;
    const rowEven = s.tableRowEven   || DEFAULTS.tableRowEven;
    const sel     = s.selectionColor || DEFAULTS.selectionColor;
    const primary = s.primaryColor   || DEFAULTS.primaryColor;

    // إعداد متغيرات CSS كـ inline style على <html> (أعلى أولوية من أي selector)
    root.style.setProperty('--tba-th-bg',   thBg);
    root.style.setProperty('--tba-th-text', thText);
    root.style.setProperty('--tba-row-even', rowEven);
    root.style.setProperty('--tba-selection', sel);
    root.style.setProperty('--tba-primary', primary);
    // تجاوز لون MUI primary في وقت التشغيل (يؤثر على الأزرار والأيقونات)
    root.style.setProperty('--palette-primary-main', primary);

    // حقن قواعد CSS عالمية للصفوف والتحديد
    let styleEl = document.getElementById(STYLE_ID);
    if (!styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = STYLE_ID;
      document.head.appendChild(styleEl);
    }
    styleEl.textContent = `
      /* تلوين الصفوف الزوجية في جميع الجداول */
      .MuiTableBody-root .MuiTableRow-root:nth-of-type(even) {
        background-color: var(--tba-row-even);
      }
      /* لون التحديد */
      .MuiTableBody-root .MuiTableRow-root.Mui-selected,
      .MuiTableBody-root .MuiTableRow-root.Mui-selected:hover {
        background-color: var(--tba-selection) !important;
      }
    `;
  }, [settings]);

  return null;
}
