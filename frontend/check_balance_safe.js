const fs = require('fs');
const path = 'd:/tba_waad_system-main/tba_waad_system-main/frontend/src/pages/settings/SystemSettingsPage.jsx';
const content = fs.readFileSync(path, 'utf8');

let braces = 0;
let parens = 0;
let brackets = 0;

for (let char of content) {
    if (char === '{') braces++;
    if (char === '}') braces--;
    if (char === '(') parens++;
    if (char === ')') parens--;
    if (char === '[') brackets++;
    if (char === ']') brackets--;
}

console.log(`Braces: ${braces}, Parens: ${parens}, Brackets: ${brackets}`);
if (braces !== 0 || parens !== 0 || brackets !== 0) {
    console.log('UNBALANCED!');
} else {
    console.log('Balanced.');
}
