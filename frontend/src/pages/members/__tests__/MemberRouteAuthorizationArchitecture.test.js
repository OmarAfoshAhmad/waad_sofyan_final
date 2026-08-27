import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routes = readFileSync('src/routes/MainRoutes.jsx', 'utf8');

describe('member command route authorization', () => {
  it.each([
    ['add', 'MEMBER_CREATE'],
    [':id/edit', 'MEMBER_EDIT_IDENTITY'],
    [':id/add-dependent', 'MEMBER_CREATE']
  ])('guards %s with the backend capability %s', (path, permission) => {
    const marker = `path: '${path}'`;
    const start = routes.indexOf(marker);
    expect(start).toBeGreaterThan(-1);
    const routeBlock = routes.slice(start, start + 260);
    expect(routeBlock).toContain(`requiredPermission=\"${permission}\"`);
    expect(routeBlock).not.toContain('allowedRoles=');
  });
});
