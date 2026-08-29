import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

/**
 * The client displays balances. It does not clamp them, and it does not
 * recompute them.
 *
 * BalanceDisplayIsNotClampedArchitectureTest enforces this on the server: a
 * remaining balance is signed, because zero is the one answer a reader cannot
 * tell apart from "exactly spent" when the truth is "overspent". The client
 * was doing exactly what the server is forbidden to do -- Math.max(0, ...)
 * around a figure the server had already computed and sent -- so a bucket in
 * overspend displayed 0 to the person deciding whether to add another claim
 * line.
 *
 * One of the three sites also recomputed the figure from its parts when the
 * server's was absent, which is a second source of truth for a number the
 * server owns.
 *
 * Scanned as text on purpose. It costs nothing, needs no build step, and the
 * mistake it catches looks entirely reasonable at the point someone writes it.
 */

const SRC = resolve(__dirname, '..');

/** Files whose "remaining" has nothing to do with a coverage balance. */
const UNRELATED = [
  'node_modules',
  '__tests__',
  '__mocks__'
];

function sourceFiles(dir) {
  const found = [];
  for (const entry of readdirSync(dir)) {
    if (UNRELATED.includes(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      found.push(...sourceFiles(full));
    } else if (/\.(js|jsx)$/.test(entry)) {
      found.push(full);
    }
  }
  return found;
}

function codeOnly(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
}

describe('balances shown to a user', () => {
  const files = sourceFiles(SRC);

  it('finds source files to check, so this cannot pass on nothing', () => {
    expect(files.length).toBeGreaterThan(100);
  });

  it('never clamps a remaining balance at zero', () => {
    const offenders = [];

    for (const file of files) {
      const code = codeOnly(readFileSync(file, 'utf8'));
      for (const line of code.split('\n')) {
        if (!line.includes('Math.max(')) continue;
        // Math.max(0, <something about a balance>)
        if (!/Math\.max\(\s*0\s*,/.test(line) && !/Math\.max\(\s*$/.test(line)) continue;
        // Days until a policy expires are a countdown, not a balance, and
        // clamping those at zero is right: an expired policy reads "expired",
        // not "-5 days". This rule is about money and coverage.
        if (/day|Day/.test(line)) continue;
        if (/remaining|available|balance|Remaining|Available|Balance/.test(line)) {
          offenders.push(`${file.replace(SRC, 'src')}: ${line.trim()}`);
        }
      }
    }

    expect(
      offenders,
      'a clamped balance shows an overspent member the same 0 an exactly-spent one shows, ' +
        'to the person deciding whether to commit more money'
    ).toEqual([]);
  });

  /**
   * The narrower, provable rule: never recompute a figure the SERVER ALSO
   * SENDS. ClaimLineRow used to fall back to `amountLimit - usedAmount`
   * whenever remainingAmount was absent, which is a second source of truth for
   * a number the server owns -- and the one that would not know about an
   * exceptional ceiling uplift.
   *
   * Plain arithmetic on two server figures for something the server does NOT
   * send -- times remaining, from timesLimit and usedCount -- is display, not
   * a second rule. It is recorded as an open item rather than banned here: the
   * server should send remainingCount the way it sends remainingAmount, and
   * until it does, a ban would only push the subtraction somewhere else.
   */
  it('never falls back to recomputing a balance the server already sent', () => {
    const offenders = [];

    for (const file of files) {
      const code = codeOnly(readFileSync(file, 'utf8'));
      for (const line of code.split('\n')) {
        const recomputes = /\b\w*[lL]imit\s*-\s*\(?\s*\w*[uU]sed/.test(line);
        const alsoNamesTheServerField =
          /remainingAmount|remainingCoverage|actualRemaining|reservableAvailable/.test(line);
        if (recomputes && alsoNamesTheServerField) {
          offenders.push(`${file.replace(SRC, 'src')}: ${line.trim()}`);
        }
      }
    }

    expect(
      offenders,
      'a fallback that recomputes what the server sent is a second source of truth, and it is ' +
        'the one that will not know about an exceptional uplift'
    ).toEqual([]);
  });
});
