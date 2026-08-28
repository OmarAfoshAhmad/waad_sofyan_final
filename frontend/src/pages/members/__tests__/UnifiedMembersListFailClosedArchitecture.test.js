import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const source = readFileSync('src/pages/members/UnifiedMembersList.jsx', 'utf8');

describe('member list fail-closed loading', () => {
  it('clears stale rows and renders the server refusal with a retry action', () => {
    expect(source).toContain("setMembers([])");
    expect(source).toContain("setTotalCount(0)");
    expect(source).toContain("setLoadError(message)");
    expect(source).toContain("{loadError}");
    expect(source).toContain('onClick={fetchMembers}>إعادة المحاولة');
  });
});
