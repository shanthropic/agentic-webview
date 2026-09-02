import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const failures = [];

const modules = new Map([
  ['browser-api', []],
  ['browser-webview', [':browser-api']],
  ['browser-compose', [':browser-webview']],
  ['agent-tools', [':browser-api']],
  ['integrations/koog', [':agent-tools']],
  ['integrations/jsonrpc', [':agent-tools']],
  ['samples/android', [':browser-compose', ':agent-tools']],
]);

for (const [module, expectedDependencies] of modules) {
  const buildFile = join(root, module, 'build.gradle.kts');
  requirePath(buildFile);
  if (!existsSync(buildFile)) continue;

  const source = readFileSync(buildFile, 'utf8');
  const actualDependencies = [...source.matchAll(/project\("(:[^"\n]+)"\)/g)]
    .map(match => match[1])
    .sort();
  const expected = [...expectedDependencies].sort();
  if (JSON.stringify(actualDependencies) !== JSON.stringify(expected)) {
    fail(`${module} project dependencies are ${actualDependencies.join(', ') || '(none)'}; expected ${expected.join(', ') || '(none)'}`);
  }
}

const expectedIncludes = [...modules.keys()].map(module => `:${module.replace('/', ':')}`).sort();
const settings = readRequired(join(root, 'settings.gradle.kts'));
const actualIncludes = [...settings.matchAll(/include\("(:[^"\n]+)"\)/g)].map(match => match[1]).sort();
if (JSON.stringify(actualIncludes) !== JSON.stringify(expectedIncludes)) {
  fail(`settings.gradle.kts modules are ${actualIncludes.join(', ')}; expected ${expectedIncludes.join(', ')}`);
}

for (const legacyPath of ['agentic-webview', 'app', 'web-injector']) {
  if (existsSync(join(root, legacyPath))) fail(`Legacy path still exists: ${legacyPath}`);
}

checkSources('browser-api', [
  [/^import android(?:\.|$)/m, 'Android platform import'],
  [/^import androidx(?:\.|$)/m, 'AndroidX import'],
  [/^import ai\.koog(?:\.|$)/m, 'Koog import'],
  [/agenticwebview\.webview/, 'WebView implementation reference'],
]);
checkSources('agent-tools', [
  [/^import android(?:\.|$)/m, 'Android platform import'],
  [/^import androidx(?:\.|$)/m, 'AndroidX import'],
  [/^import ai\.koog(?:\.|$)/m, 'Koog import'],
  [/agenticwebview\.webview/, 'WebView implementation reference'],
]);
checkSources('browser-webview', [
  [/^import androidx\.compose(?:\.|$)/m, 'Compose import'],
  [/^import ai\.koog(?:\.|$)/m, 'Koog import'],
  [/agenticwebview\.tools/, 'agent-tools reference'],
]);
checkSources('browser-compose', [
  [/^import ai\.koog(?:\.|$)/m, 'Koog import'],
  [/agenticwebview\.tools/, 'agent-tools reference'],
]);

for (const sourceRoot of ['browser-api', 'browser-webview', 'browser-compose', 'agent-tools', 'integrations', 'samples']) {
  checkSources(sourceRoot, [
    [/\bAgenticWebController\b/, 'deleted controller API'],
    [/\bAgenticWebViewConfig\b/, 'deleted configuration API'],
  ]);
}

const gradleProperties = readRequired(join(root, 'gradle.properties'));
const versionMatch = gradleProperties.match(/^VERSION_NAME=(.+)$/m);
const packageJsonPath = join(root, 'web-runtime', 'package.json');
const runtimePackage = JSON.parse(readRequired(packageJsonPath));
if (!versionMatch) {
  fail('VERSION_NAME is missing from gradle.properties');
} else if (runtimePackage.version !== versionMatch[1].split('-', 1)[0]) {
  fail(`web-runtime version ${runtimePackage.version} does not match Gradle base version ${versionMatch[1]}`);
}

const runtimeBundle = join(root, 'web-runtime', 'dist', 'agentic_runtime.min.js');
requirePath(runtimeBundle);
if (existsSync(runtimeBundle)) {
  const bundleBytes = statSync(runtimeBundle).size;
  const maximumRuntimeBytes = 128 * 1024;
  if (bundleBytes > maximumRuntimeBytes) {
    fail(`Runtime bundle is ${bundleBytes} bytes; budget is ${maximumRuntimeBytes} bytes`);
  }
}

if (failures.length) {
  process.stderr.write(`Architecture validation failed:\n${failures.map(value => `- ${value}`).join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`Validated ${modules.size} module boundaries, legacy removal, version alignment, and runtime budget.\n`);
}

function checkSources(directory, rules) {
  const base = join(root, directory);
  if (!existsSync(base)) return;
  for (const file of files(base).filter(path => ['.kt', '.kts'].includes(extname(path)))) {
    const source = readFileSync(file, 'utf8');
    for (const [pattern, description] of rules) {
      if (pattern.test(source)) fail(`${relative(root, file)} contains forbidden ${description}`);
    }
  }
}

function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? files(path) : [path];
  });
}

function readRequired(path) {
  requirePath(path);
  return existsSync(path) ? readFileSync(path, 'utf8') : '';
}

function requirePath(path) {
  if (!existsSync(path)) fail(`Required path is missing: ${relative(root, path)}`);
}

function fail(message) {
  failures.push(message);
}
