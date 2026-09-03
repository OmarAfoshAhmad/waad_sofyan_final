import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const viewSource = readFileSync('src/pages/provider-contracts/ProviderContractView.jsx', 'utf8');

/**
 * The contract view's back buttons used navigate(-1) -- relative to
 * whatever the browser history happened to contain -- while the provider
 * page it is reached from always uses a fixed navigate('/providers'). That
 * mismatch (relative "go back one" vs. absolute "jump here") is what made
 * Providers -> Contract -> back -> Providers -> Contract feel like it never
 * left, since each push added a new history entry -1 never actually
 * unwound. A fixed destination, like every other back button in this
 * module already uses, does not depend on how the page was reached.
 */
describe('provider contract view back navigation', () => {
  it('never navigates by relative history offset', () => {
    expect(viewSource).not.toContain('navigate(-1)');
  });

  it('returns to the contracts list via a fixed path', () => {
    const backButtonCount = (viewSource.match(/navigate\('\/provider-contracts'\)/g) || []).length;
    expect(backButtonCount).toBeGreaterThanOrEqual(2);
  });
});
