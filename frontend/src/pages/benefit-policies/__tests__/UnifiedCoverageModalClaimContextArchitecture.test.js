import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const modalSource = readFileSync('src/pages/benefit-policies/components/UnifiedCoverageModal.jsx', 'utf8');
const tabSource = readFileSync('src/pages/benefit-policies/BenefitPolicyRulesTab.jsx', 'utf8');

describe('unified coverage modal claim-context boundary', () => {
  it('loads business contexts and derives the technical encounter type', () => {
    expect(modalSource).toContain('queryFn: getActiveClaimContexts');
    expect(modalSource).toContain('label="سياق قرار المطالبة"');
    expect(modalSource).toContain("setEncounterType(context?.baseEncounterType || 'ANY')");
    expect(modalSource).toMatch(/label="نوع المقابلة الأساسي"[\s\S]*?disabled/);
  });

  it('sends the exact claim context on creates and updates', () => {
    expect(modalSource.match(/claimContextCode: claimContextCode/g)?.length).toBeGreaterThanOrEqual(4);
    expect(modalSource).toContain('initialData.claimContextCode || initialData.encounterType');
  });

  it('checks current rules and explains duplicates before saving', () => {
    expect(tabSource).toContain('existingRules={rules}');
    expect(modalSource).toContain('const duplicateRule = findDuplicateRule()');
    expect(modalSource).toContain('توجد قاعدة مسبقاً للتصنيف');
    expect(modalSource).not.toContain('Data integrity error');
    expect(modalSource).toContain('{ suppressGlobalError: true }');
  });

  it('loads the complete active category list with an isolated selector cache key', () => {
    expect(tabSource).toContain('queryFn: getAllMedicalCategories');
    expect(tabSource).toContain("queryKey: ['medical-categories-active-coverage-selector']");
    expect(tabSource).not.toContain('size: 500');
  });
});
