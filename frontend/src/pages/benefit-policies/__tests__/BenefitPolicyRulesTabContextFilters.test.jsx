import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SnackbarProvider } from 'notistack';

import BenefitPolicyRulesTab from '../BenefitPolicyRulesTab';

// This is a real render + interaction test, not a source-string check --
// the earlier P1 pass added filter/badge behavior whose only coverage was
// grepping BenefitPolicyRulesTab.jsx's own source. It never proved a click
// on the context filter or the gap chip actually changes what is on screen.

vi.mock('services/api/benefit-policy-rules.service', () => ({
  getPolicyRules: vi.fn(),
  createPolicyRule: vi.fn(),
  updatePolicyRule: vi.fn(),
  togglePolicyRuleActive: vi.fn(),
  restorePolicyRule: vi.fn(),
  deletePolicyRule: vi.fn(),
  hardDeletePolicyRule: vi.fn(),
  copyPolicyRules: vi.fn(),
  downloadPolicyRulesTemplate: vi.fn(),
  importPolicyRulesFromExcel: vi.fn()
}));

vi.mock('services/api/benefit-structure.service', () => ({
  getBenefitStructure: vi.fn(),
  upsertIndividualBenefitLimit: vi.fn(),
  deleteBenefitGroup: vi.fn(),
  downloadBenefitStructureTemplate: vi.fn(),
  importBenefitStructure: vi.fn()
}));

vi.mock('services/api/medical-categories.service', () => ({
  getAllMedicalCategories: vi.fn().mockResolvedValue([])
}));

vi.mock('services/api/medical-services.service', () => ({
  lookupMedicalServices: vi.fn().mockResolvedValue([])
}));

vi.mock('services/api/benefit-policies.service', () => ({
  getBenefitPoliciesSelector: vi.fn().mockResolvedValue([]),
  checkPolicyEditability: vi.fn().mockResolvedValue(true),
  getBenefitPolicyGapReport: vi.fn()
}));

vi.mock('services/api/claim-contexts.service', () => ({
  getActiveClaimContexts: vi.fn()
}));

import { getPolicyRules } from 'services/api/benefit-policy-rules.service';
import { getBenefitStructure } from 'services/api/benefit-structure.service';
import { getBenefitPolicyGapReport } from 'services/api/benefit-policies.service';
import { getActiveClaimContexts } from 'services/api/claim-contexts.service';

const OUTPATIENT_RULE = {
  id: 1,
  ruleType: 'CATEGORY',
  medicalCategoryId: 10,
  medicalCategoryCode: 'CAT-PHYSIO',
  medicalCategoryName: 'علاج طبيعي',
  active: true,
  deleted: false,
  encounterType: 'OUTPATIENT',
  // OUTPATIENT is itself a valid, selectable context. Equality with the
  // technical encounter type is not provenance and must not mark it legacy.
  claimContextCode: 'OUTPATIENT',
  coveragePercent: 80
};

const PREGNANCY_RULE = {
  id: 2,
  ruleType: 'CATEGORY',
  medicalCategoryId: 20,
  medicalCategoryCode: 'CAT-MAT-COMP',
  // Deliberately distinct from the claim context's own Arabic name below --
  // a category and its context are two different fields on screen and a
  // test that gives them the same text can't tell the two chips apart.
  medicalCategoryName: 'خدمات الولادة المعقدة',
  active: true,
  deleted: false,
  encounterType: 'INPATIENT',
  claimContextCode: 'PREGNANCY_COMPLICATIONS',
  coveragePercent: 100
};

const DIAG_FEES_RULE = {
  id: 3,
  ruleType: 'CATEGORY',
  medicalCategoryId: 30,
  medicalCategoryCode: 'CAT-COV-DIAG-FEES',
  medicalCategoryName: 'أشعة وتحاليل ورسوم أطباء',
  active: true,
  deleted: false,
  encounterType: 'OUTPATIENT',
  claimContextCode: 'OUTPATIENT',
  coveragePercent: 75
};

const CLAIM_CONTEXTS = [
  { code: 'OUTPATIENT', nameAr: 'عيادات خارجية', baseEncounterType: 'OUTPATIENT' },
  { code: 'INPATIENT', nameAr: 'إيواء', baseEncounterType: 'INPATIENT' },
  { code: 'PREGNANCY_COMPLICATIONS', nameAr: 'مضاعفات الحمل', baseEncounterType: 'INPATIENT' }
];

function renderTab({ rules, structure, gapReport }) {
  getPolicyRules.mockResolvedValue(rules);
  getBenefitStructure.mockResolvedValue(structure);
  getActiveClaimContexts.mockResolvedValue(CLAIM_CONTEXTS);
  getBenefitPolicyGapReport.mockResolvedValue(gapReport);

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  const theme = createTheme({ cssVariables: true });

  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <BenefitPolicyRulesTab policyId={1} policyStatus="ACTIVE" policyDefaultCoveragePercent={100} onOpenStructure={vi.fn()} />
        </SnackbarProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

describe('BenefitPolicyRulesTab: real context filter and gap-badge interaction', () => {
  it('shows a valid generic context without falsely labelling it as legacy', async () => {
    renderTab({
      rules: [OUTPATIENT_RULE, PREGNANCY_RULE],
      structure: { groups: [], buckets: [], links: [] },
      gapReport: { rulesWithoutBucket: [], bucketsWithoutRule: [], rulesWithUnknownContext: [] }
    });

    expect(await screen.findByText('علاج طبيعي')).toBeInTheDocument();

    expect(screen.getByText('عيادات خارجية')).toBeInTheDocument();
    expect(screen.queryByText('غير محدد (قديم)')).not.toBeInTheDocument();
    expect(screen.queryByText('⚠ قديمة')).not.toBeInTheDocument();

    // The differentiated rule shows its own category and its real context
    // name as two distinct chips.
    expect(screen.getByText('خدمات الولادة المعقدة')).toBeInTheDocument();
    expect(screen.getByText('مضاعفات الحمل')).toBeInTheDocument();
  });

  it('filtering by a specific context hides rules on every other context', async () => {
    const user = userEvent.setup();
    renderTab({
      rules: [OUTPATIENT_RULE, PREGNANCY_RULE],
      structure: { groups: [], buckets: [], links: [] },
      gapReport: { rulesWithoutBucket: [], bucketsWithoutRule: [], rulesWithUnknownContext: [] }
    });

    await screen.findByText('علاج طبيعي');
    expect(screen.getByText('خدمات الولادة المعقدة')).toBeInTheDocument();

    // MUI's TextField select renders its closed value as plain text, not a
    // native <select> option or displayValue.
    const contextSelect = screen.getByText(/^كل السياقات/);
    await user.click(contextSelect);
    const listbox = await screen.findByRole('listbox');
    await user.click(within(listbox).getByText(/^مضاعفات الحمل/));

    expect(screen.getByText('خدمات الولادة المعقدة')).toBeInTheDocument();
    expect(screen.queryByText('علاج طبيعي')).not.toBeInTheDocument();
  });

  it('normalizes Arabic hamzas and letter variants while searching rules', async () => {
    const user = userEvent.setup();
    renderTab({
      rules: [OUTPATIENT_RULE, DIAG_FEES_RULE],
      structure: { groups: [], buckets: [], links: [] },
      gapReport: { rulesWithoutBucket: [], bucketsWithoutRule: [], rulesWithUnknownContext: [] }
    });

    expect(await screen.findByText('أشعة وتحاليل ورسوم أطباء')).toBeInTheDocument();

    const search = screen.getByPlaceholderText('بحث بالرمز أو الاسم أو النوع...');
    await user.type(search, 'اشعه اطباء');

    expect(screen.getByText('أشعة وتحاليل ورسوم أطباء')).toBeInTheDocument();
    expect(screen.queryByText('علاج طبيعي')).not.toBeInTheDocument();
  });

  it('toggling the gap chip hides a rule group backed by a real shared bucket, and keeps rules that rely on no bucket at all', async () => {
    const user = userEvent.setup();
    renderTab({
      rules: [OUTPATIENT_RULE, PREGNANCY_RULE],
      // Neither standalone rule links its own bucket -- under the current
      // model that is *always* advisory-flagged (it may deliberately rely
      // on an individual cap or the policy's general ceiling instead), so
      // both are expected to stay under "قواعد بها فجوة". The one row that
      // can genuinely read as "no gap" is a real, bucket-backed group.
      structure: {
        groups: [{ id: 501, code: 'GRP-DENTAL', nameAr: 'مجموعة الأسنان الأساسية', active: true, contextType: 'OUTPATIENT' }],
        buckets: [{ id: 900, benefitGroupId: 501, code: 'B-DENTAL', nameAr: 'وعاء الأسنان', amountLimit: 500, periodType: 'ANNUAL' }],
        links: []
      },
      gapReport: { rulesWithoutBucket: [], bucketsWithoutRule: [], rulesWithUnknownContext: [] }
    });

    await screen.findByText('علاج طبيعي');
    expect(screen.getByText('مجموعة الأسنان الأساسية')).toBeInTheDocument();

    const gapChip = screen.getByText(/قواعد بها فجوة/);
    await user.click(gapChip);

    expect(screen.getByText('علاج طبيعي')).toBeInTheDocument();
    expect(screen.getByText('خدمات الولادة المعقدة')).toBeInTheDocument();
    expect(screen.queryByText('مجموعة الأسنان الأساسية')).not.toBeInTheDocument();
  });
});
