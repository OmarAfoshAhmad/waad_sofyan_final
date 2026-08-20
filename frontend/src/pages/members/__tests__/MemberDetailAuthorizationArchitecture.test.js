import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const view = readFileSync('src/pages/members/UnifiedMemberView.jsx', 'utf8');
const dependentModal = readFileSync('src/pages/members/DependentModal.jsx', 'utf8');

describe('member detail command boundaries', () => {
  it('uses the same capability matrix as the list', () => {
    expect(view).toContain('getMemberCapabilities(user)');
    expect(view).toContain('capabilities.lifecycle');
    expect(view).toContain('capabilities.hardDelete');
    expect(view).toContain('capabilities.edit');
    expect(view).toContain('capabilities.create');
  });

  it('requires a reason for every manual status transition', () => {
    expect(view).not.toContain('applyStatusChange(targetId, targetStatus, null)');
    expect(view).toContain('!statusChangeDialog.reason.trim()');
    expect(view).toContain('سبب تغيير الحالة');
  });

  it('keeps relationship and status out of the dependent generic edit payload', () => {
    expect(dependentModal).toContain('...(!isEditMode && {');
    expect(dependentModal).toContain('disabled={isEditMode}');
    expect(dependentModal).toContain('تُغيّر من إجراء الحالة المدقّق');
  });
});
