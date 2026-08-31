import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const source = fs.readFileSync(path.resolve(__dirname, '../UserDetails.jsx'), 'utf8');

describe('UserDetails authentication wiring', () => {
  it('imports and invokes the authentication hook used by the page', () => {
    expect(source).toMatch(/import\s+useAuth\s+from\s+['"]hooks\/useAuth['"]/);
    expect(source).toMatch(/const\s+\{\s*user:\s*currentUser\s*\}\s*=\s*useAuth\(\)/);
  });
});
