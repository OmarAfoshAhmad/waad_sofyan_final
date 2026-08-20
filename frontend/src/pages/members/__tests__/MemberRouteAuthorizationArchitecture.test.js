import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routes = readFileSync('src/routes/MainRoutes.jsx', 'utf8');

describe('member command route authorization', () => {
  it.each(['add', ':id/edit', ':id/add-dependent'])('guards %s with the backend command roles', (path) => {
    const marker = `path: '${path}'`;
    const start = routes.indexOf(marker);
    expect(start).toBeGreaterThan(-1);
    const routeBlock = routes.slice(start, start + 260);
    expect(routeBlock).toContain("allowedRoles={['EMPLOYER_ADMIN', 'DATA_ENTRY']}");
  });
});
