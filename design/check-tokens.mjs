// GOALS.md G2.4: tokens.json 의 색·글자 크기가 tokens.css 와 앱 Theme.kt 에 똑같이 들어가 있는지 확인
// 사용: node design/check-tokens.mjs
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const tokens = JSON.parse(readFileSync(path.join(root, 'design/tokens.json'), 'utf8'));
const css = readFileSync(path.join(root, 'design/prototype/tokens.css'), 'utf8');
const kt = readFileSync(path.join(root, 'app/src/main/java/io/moodlab/moodrive/ui/Theme.kt'), 'utf8');

const kebab = s => s.replace(/[A-Z]/g, m => '-' + m.toLowerCase());
const problems = [];

for (const [name, hex] of Object.entries(tokens.color)) {
  const cssVal = css.match(new RegExp(`--${kebab(name)}:\\s*(#[0-9A-Fa-f]{6})`))?.[1];
  if (cssVal?.toUpperCase() !== hex.toUpperCase()) problems.push(`css --${kebab(name)} = ${cssVal} ≠ ${hex}`);
  const ktVal = kt.match(new RegExp(`val ${name} = Color\\(0xFF([0-9A-Fa-f]{6})\\)`))?.[1];
  if (('#' + ktVal).toUpperCase() !== hex.toUpperCase()) problems.push(`Theme.kt Mc.${name} = ${ktVal} ≠ ${hex}`);
}
for (const [name, t] of Object.entries(tokens.type)) {
  const cssVal = css.match(new RegExp(`--fs-${name}:\\s*(\\d+)px`))?.[1];
  if (Number(cssVal) !== t.size) problems.push(`css --fs-${name} = ${cssVal} ≠ ${t.size}`);
  const ktVal = kt.match(new RegExp(`val ${name} = (\\d+)\\.sp`))?.[1];
  if (Number(ktVal) !== t.size) problems.push(`Theme.kt Ms.${name} = ${ktVal} ≠ ${t.size}`);
}
for (const [name, v] of Object.entries(tokens.touch)) {
  const ktVal = kt.match(new RegExp(`val touch${name[0].toUpperCase() + name.slice(1)} = (\\d+)\\.dp`))?.[1];
  if (Number(ktVal) !== v) problems.push(`Theme.kt Ms.touch${name} = ${ktVal} ≠ ${v}`);
}

console.log(problems.length ? problems.map(p => '✗ ' + p).join('\n') : '✓ tokens.json = tokens.css = Theme.kt');
process.exit(problems.length ? 1 : 0);
