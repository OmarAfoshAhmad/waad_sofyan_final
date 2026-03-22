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

      /* ═══════════════════════════════════════════════════
         اللون الرئيسي — Chip + Button + IconButton outlined
         يتبع اللون المحدد في الإعدادات عبر --tba-primary
         ═══════════════════════════════════════════════════ */

      /* Chip outlined primary */
      .MuiChip-outlined.MuiChip-colorPrimary,
      .MuiChip-outlinedPrimary {
        border-color: var(--tba-primary) !important;
        color: var(--tba-primary) !important;
      }
      .MuiChip-outlined.MuiChip-colorPrimary .MuiChip-icon,
      .MuiChip-outlinedPrimary .MuiChip-icon {
        color: var(--tba-primary) !important;
      }

      /* Chip filled primary */
      .MuiChip-filled.MuiChip-colorPrimary,
      .MuiChip-filledPrimary {
        background-color: var(--tba-primary) !important;
      }

      /* Button outlined primary */
      .MuiButton-outlined.MuiButton-colorPrimary,
      .MuiButton-outlinedPrimary {
        border-color: var(--tba-primary) !important;
        color: var(--tba-primary) !important;
      }
      .MuiButton-outlined.MuiButton-colorPrimary:hover,
      .MuiButton-outlinedPrimary:hover {
        background-color: color-mix(in srgb, var(--tba-primary) 8%, transparent) !important;
        border-color: var(--tba-primary) !important;
      }

      /* Button contained primary */
      .MuiButton-contained.MuiButton-colorPrimary,
      .MuiButton-containedPrimary {
        background-color: var(--tba-primary) !important;
      }

      /* IconButton primary */
      .MuiIconButton-colorPrimary {
        color: var(--tba-primary) !important;
      }

      /* Tab indicator & selected */
      .MuiTabs-indicator {
        background-color: var(--tba-primary) !important;
      }
      .MuiTab-root.Mui-selected {
        color: var(--tba-primary) !important;
      }

      /* TextField outlined focused */
      .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline {
        border-color: var(--tba-primary) !important;
      }
      .MuiInputLabel-root.Mui-focused {
        color: var(--tba-primary) !important;
      }

      /* Checkbox & Radio & Switch primary */
      .MuiCheckbox-colorPrimary.Mui-checked,
      .MuiRadio-colorPrimary.Mui-checked {
        color: var(--tba-primary) !important;
      }
      .MuiSwitch-colorPrimary.Mui-checked + .MuiSwitch-track {
        background-color: var(--tba-primary) !important;
      }
      .MuiSwitch-colorPrimary.Mui-checked {
        color: var(--tba-primary) !important;
      }

      /* Link primary */
      .MuiLink-root.MuiLink-colorPrimary,
      a.MuiTypography-colorPrimary {
        color: var(--tba-primary) !important;
      }

      /* Pagination selected */
      .MuiPaginationItem-root.Mui-selected {
        background-color: var(--tba-primary) !important;
      }

      /* TableSortLabel active */
      .MuiTableSortLabel-root.Mui-active,
      .MuiTableSortLabel-root.Mui-active .MuiTableSortLabel-icon {
        color: var(--tba-primary) !important;
      }

      /* ══════════════════════════════════════
         ترويسة الجداول — TableHead
         ══════════════════════════════════════ */
      .MuiTableHead-root .MuiTableCell-root {
        background-color: var(--tba-th-bg) !important;
        color: var(--tba-th-text) !important;
      }

    `;
  }, [settings]);

  return null;
}
