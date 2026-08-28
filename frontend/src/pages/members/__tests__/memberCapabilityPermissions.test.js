import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { getMemberCapabilities, MEMBER_CAPABILITY_PERMISSIONS } from '../memberCapabilities';

/**
 * The buttons this file draws and the grants the server checks must be the
 * same list.
 *
 * They drifted once. The server decided member writes by role name while this
 * file read the permission catalogue, so granting MEMBER_CHANGE_STATUS to a
 * data-entry user produced a visible button and a 403 -- a screen telling
 * someone they may do something the server had already decided they may not.
 *
 * Reading the Java map as text is crude, and that is the trade: it costs
 * nothing, needs no build step, and fails on the day someone adds a capability
 * here without a matching grant there, which is the day the drift starts.
 */

const OPERATION_PERMISSIONS = resolve(
  __dirname,
  '../../../../../backend/src/main/java/com/waad/tba/modules/member/security/MemberOperationPermissions.java'
);

function serverPermissions() {
  const source = readFileSync(OPERATION_PERMISSIONS, 'utf8');
  const withoutComments = source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, '');
  return new Set([...withoutComments.matchAll(/SystemPermission\.([A-Z_]+)/g)].map((m) => m[1]));
}

describe('member capabilities and the server permission map', () => {
  it('names only permissions the server actually requires', () => {
    const server = serverPermissions();
    const unknown = Object.entries(MEMBER_CAPABILITY_PERMISSIONS)
      .filter(([, permission]) => !server.has(permission))
      .map(([capability, permission]) => `${capability} -> ${permission}`);

    expect(
      unknown,
      'a capability gated on a permission no member operation requires draws a ' +
        'button whose grant means nothing to the server'
    ).toEqual([]);
  });

  it('gates the ceiling column on the list permission, not the single-member one', () => {
    // The two are different acts: checking the patient in front of you, and
    // pulling a page of the insurer's book.
    expect(MEMBER_CAPABILITY_PERMISSIONS.viewLimits).toBe('MEMBER_LIMIT_VIEW');
    expect(MEMBER_CAPABILITY_PERMISSIONS.viewLimitsList).toBe('MEMBER_LIMIT_LIST_VIEW');

    const provider = getMemberCapabilities({ permissions: ['MEMBER_VIEW', 'MEMBER_LIMIT_VIEW'] });
    expect(provider.viewLimits).toBe(true);
    expect(provider.viewLimitsList).toBe(false);
  });

  it('derives every capability from its own permission and nothing else', () => {
    for (const [capability, permission] of Object.entries(MEMBER_CAPABILITY_PERMISSIONS)) {
      const granted = getMemberCapabilities({ permissions: [permission] });
      expect(granted[capability], `${capability} should follow ${permission}`).toBe(true);

      const withoutIt = getMemberCapabilities({ permissions: [] });
      expect(withoutIt[capability], `${capability} should not appear without ${permission}`).toBe(false);
    }
  });
});
