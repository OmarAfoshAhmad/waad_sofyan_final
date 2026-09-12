import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.resolve(here, '..');

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (/\.(jsx?|mjs)$/.test(entry.name) && !full.includes(`${path.sep}__tests__${path.sep}`)) out.push(full);
  }
  return out;
};

const read = (relativePath) => fs.readFileSync(path.resolve(src, relativePath), 'utf8');

/**
 * The PDF worker used to be fetched from unpkg.com at runtime. That is a
 * third-party script executing inside the app with no integrity hash, and
 * nginx's CSP (worker-src 'self' blob:) blocks it anyway -- so the preview was
 * both a supply-chain exposure and broken. It also sat behind a top-level
 * import in App.jsx, which put all of pdfjs into the login page's bundle.
 */
describe('pdf worker', () => {
  it('is never loaded from another origin', () => {
    const offenders = walk(src).filter((file) => {
      const text = fs.readFileSync(file, 'utf8');
      return /workerSrc\s*=\s*[`'"](\/\/|https?:)/.test(text) || /unpkg\.com/.test(text);
    });
    expect(offenders.map((f) => path.relative(src, f))).toEqual([]);
  });

  it('is configured next to the only component that renders PDFs, not at app boot', () => {
    const app = read('App.jsx');
    expect(app).not.toMatch(/pdfWorker/);
    expect(app).not.toMatch(/react-pdf/);
    expect(fs.existsSync(path.resolve(src, 'utils/pdfWorker.js'))).toBe(false);

    const panel = read('components/documents/DocumentPreviewPanel.jsx');
    expect(panel).toMatch(/GlobalWorkerOptions\.workerSrc\s*=\s*new URL\(/);
    expect(panel).toMatch(/import\.meta\.url/);
  });
});
