import fs from 'fs';
import path from 'path';

const root = process.cwd();
const srcDir = path.join(root, 'src');

const checks = [
  {
    name: 'Legacy JWT API login endpoint usage',
    pattern:
      /(post|get|put|delete|request)\s*\([^\n]*['"`]\/?auth\/login['"`]|url\s*:\s*['"`]\/?auth\/login['"`]|fetch\(\s*['"`][^'"`]*\/?auth\/login['"`]/g,
    allow: (file, line) => file.endsWith('utils/axios.js') && line.includes("url?.includes('/auth/login')")
  },
  {
    name: 'Manual Bearer header injection',
    pattern: /Authorization\s*=\s*`Bearer/g,
    allow: () => false
  },
  {
    name: 'Manual LYD currency string formatting',
    pattern: /toLocaleString\([^\n]*\)\s*\+\s*['\"]\s*د\.ل|`\$\{[^}]*toLocaleString\([^)]*\)\}\s*د\.ل`/g,
    allow: () => false
  }
];

const unsafeHtmlPattern = /dangerouslySetInnerHTML\s*=\s*\{\{\s*__html\s*:\s*([^}]+)\}\}/g;

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (/\.(js|jsx)$/.test(entry.name)) {
      files.push(fullPath);
    }
  }
  return files;
}

function findLine(content, index) {
  return content.slice(0, index).split('\n').length;
}

const jsFiles = walk(srcDir);
const violations = [];

for (const file of jsFiles) {
  const content = fs.readFileSync(file, 'utf8');
  const rel = path.relative(root, file).replace(/\\/g, '/');

  for (const check of checks) {
    let match;
    while ((match = check.pattern.exec(content)) !== null) {
      const lineNo = findLine(content, match.index);
      const line = content.split('\n')[lineNo - 1] || '';
      if (!check.allow(rel, line)) {
        violations.push(`${rel}:${lineNo} -> ${check.name}`);
      }
    }
    check.pattern.lastIndex = 0;
  }

  let htmlMatch;
  while ((htmlMatch = unsafeHtmlPattern.exec(content)) !== null) {
    const lineNo = findLine(content, htmlMatch.index);
    const expr = htmlMatch[1] || '';
    if (!expr.includes('sanitizeHtml(') && !/sanitized/i.test(expr)) {
      violations.push(`${rel}:${lineNo} -> dangerouslySetInnerHTML without sanitizeHtml(...)`);
    }
  }
  unsafeHtmlPattern.lastIndex = 0;
}

if (violations.length > 0) {
  console.error('Production sanity checks FAILED:\n');
  for (const v of violations) console.error(`- ${v}`);
  process.exit(1);
}

console.log('Production sanity checks PASSED');
