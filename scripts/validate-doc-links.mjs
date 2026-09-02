import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const roots = [join(root, 'README.md'), join(root, 'CONTRIBUTING.md'), ...markdownFiles(join(root, 'docs'))];
const failures = [];

for (const file of roots) {
  const source = readFileSync(file, 'utf8');
  for (const match of source.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].split('#', 1)[0].trim();
    if (!target || /^[a-z]+:/i.test(target) || target.startsWith('#')) continue;
    if (!existsSync(resolve(dirname(file), target))) failures.push(`${file.slice(root.length + 1)} -> ${target}`);
  }
}

if (failures.length) {
  process.stderr.write(`Broken documentation links:\n${failures.join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`Validated links in ${roots.length} Markdown files.\n`);
}

function markdownFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? markdownFiles(path) : extname(path) === '.md' ? [path] : [];
  });
}
