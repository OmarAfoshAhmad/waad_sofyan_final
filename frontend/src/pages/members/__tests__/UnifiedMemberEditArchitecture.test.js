import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const source = readFileSync('src/pages/members/UnifiedMemberEdit.jsx', 'utf8');

describe('generic member edit boundary', () => {
  it('does not invoke the lifecycle transition endpoint', () => {
    expect(source).not.toContain('changeMemberStatus');
  });

  it.each(['employerId', 'relationship', 'status'])('does not expose %s through the generic change handler', (field) => {
    expect(source).not.toContain(`handleChange('${field}')`);
  });

  it('explains the dedicated audited operations to the user', () => {
    expect(source).toContain('نقل جهة العمل عملية مستقلة ومؤرخة');
    expect(source).toContain('تغيير القرابة عملية أسرية مستقلة ومدققة');
    expect(source).toContain('غيّر الحالة من الإجراء المستقل');
  });
});
