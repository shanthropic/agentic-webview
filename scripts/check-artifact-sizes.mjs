import { existsSync, readdirSync, statSync } from 'node:fs';
import { basename, join, relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const requireBuilt = process.argv.includes('--require-built');
const failures = [];
const reports = [];

const budgets = [
  { name: 'web runtime', directory: 'web-runtime/dist', match: /^agentic_runtime\.min\.js$/, maximumBytes: 128 * 1024, required: true },
  { name: 'browser-api JAR', directory: 'browser-api/build/libs', match: /^(?!.*(?:sources|javadoc|tests)).*\.jar$/, maximumBytes: 640 * 1024 },
  { name: 'browser-webview AAR', directory: 'browser-webview/build/outputs/aar', match: /release\.aar$/, maximumBytes: 2 * 1024 * 1024 },
  { name: 'browser-compose AAR', directory: 'browser-compose/build/outputs/aar', match: /release\.aar$/, maximumBytes: 1024 * 1024 },
  { name: 'agent-tools JAR', directory: 'agent-tools/build/libs', match: /^(?!.*(?:sources|javadoc|tests)).*\.jar$/, maximumBytes: 512 * 1024 },
  { name: 'Koog adapter JAR', directory: 'integrations/koog/build/libs', match: /^(?!.*(?:sources|javadoc|tests)).*\.jar$/, maximumBytes: 512 * 1024 },
  { name: 'JSON-RPC adapter JAR', directory: 'integrations/jsonrpc/build/libs', match: /^(?!.*(?:sources|javadoc|tests)).*\.jar$/, maximumBytes: 512 * 1024 },
];

for (const budget of budgets) {
  const directory = join(root, budget.directory);
  const artifacts = existsSync(directory)
    ? allFiles(directory).filter(path => budget.match.test(basename(path)))
    : [];
  if (artifacts.length === 0) {
    if (budget.required || requireBuilt) failures.push(`${budget.name} is missing under ${budget.directory}`);
    continue;
  }
  for (const artifact of artifacts) {
    const bytes = statSync(artifact).size;
    reports.push(`${relative(root, artifact)}: ${bytes} bytes (budget ${budget.maximumBytes})`);
    if (bytes > budget.maximumBytes) {
      failures.push(`${relative(root, artifact)} is ${bytes} bytes; budget is ${budget.maximumBytes} bytes`);
    }
  }
}

if (failures.length) {
  process.stderr.write(`Artifact-size validation failed:\n${failures.map(value => `- ${value}`).join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`Artifact-size budgets passed:\n${reports.map(value => `- ${value}`).join('\n')}\n`);
}

function allFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? allFiles(path) : [path];
  });
}
