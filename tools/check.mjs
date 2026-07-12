import { spawnSync } from 'node:child_process';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const root = process.cwd();
const failures = [];

const requiredFiles = [
  'repo.json',
  'package.json',
  'README.md',
  'README.en.md',
  'LICENSE',
  '.gitignore',
  'build.gradle.kts',
  'settings.gradle.kts',
  'gradle.properties',
  'gradlew',
  'gradlew.bat',
  'gradle/wrapper/gradle-wrapper.jar',
  'gradle/wrapper/gradle-wrapper.properties',
  '.github/workflows/build.yml',
  'assets/icon.png',
  'DdysProvider/build.gradle.kts',
  'DdysProvider/src/main/AndroidManifest.xml',
  'DdysProvider/src/main/res/values/strings.xml',
  'DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysPlugin.kt',
  'DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysProvider.kt',
  'DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysSettings.kt',
  'docs/api-mapping.md',
  'docs/compatibility.md',
  'examples/custom-api-base.json',
  'tests/repository.test.mjs',
  'tests/static.test.mjs',
  'tools/check.mjs',
  'tools/build-package.ps1',
];

for (const file of requiredFiles) await mustExist(file);
await checkJson();
await checkPackage();
await checkRepository();
await checkGradle();
await checkKotlin();
await checkDocs();
await checkSyntax();
await checkForbiddenFiles();
await checkForbiddenText();

if (failures.length) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exit(1);
}

console.log(JSON.stringify({ ok: true, package: 'ddys-cloudstream', version: '0.1.0', files: (await listFiles(root)).length }, null, 2));

async function checkJson() {
  for (const full of await listFiles(root)) {
    const rel = slash(path.relative(root, full));
    if (!/\.json$/i.test(rel)) continue;
    try {
      JSON.parse(await fs.readFile(full, 'utf8'));
    } catch (error) {
      assert(false, `${rel} is not valid JSON: ${error.message}`);
    }
  }
}

async function checkPackage() {
  const pkg = JSON.parse(await read('package.json'));
  assert(pkg.name === 'ddys-cloudstream', 'package name mismatch.');
  assert(pkg.version === '0.1.0', 'package version mismatch.');
  assert(pkg.private === true, 'package must be private.');
  assert(pkg.type === 'module', 'package must use ESM for checks.');
}

async function checkRepository() {
  const repo = JSON.parse(await read('repo.json'));
  assert(repo.manifestVersion === 1, 'repo manifestVersion must be 1.');
  assert(Array.isArray(repo.pluginLists) && repo.pluginLists.length === 1, 'repo pluginLists must contain one manifest.');
  assert(repo.pluginLists[0] === 'https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/builds/plugins.json', 'repo pluginLists URL mismatch.');
}

async function checkGradle() {
  const rootGradle = await read('build.gradle.kts');
  for (const fragment of [
    'com.android.tools.build:gradle:8.7.3',
    'com.github.recloudstream:gradle:-SNAPSHOT',
    'org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20',
    'cloudstream("com.lagradost:cloudstream3:pre-release")',
    'namespace = "io.ddys.cloudstream"',
  ]) {
    assert(rootGradle.includes(fragment), `build.gradle.kts missing ${fragment}`);
  }

  const pluginGradle = await read('DdysProvider/build.gradle.kts');
  for (const fragment of ['version = 1', 'status = 1', 'tvTypes', 'iconUrl', 'language = "zh"']) {
    assert(pluginGradle.includes(fragment), `DdysProvider/build.gradle.kts missing ${fragment}`);
  }

  const workflow = await read('.github/workflows/build.yml');
  for (const fragment of ['./gradlew make makePluginsJson', './gradlew ensureJarCompatibility', 'plugins.json', 'builds']) {
    assert(workflow.includes(fragment), `build workflow missing ${fragment}`);
  }
}

async function checkKotlin() {
  const provider = await read('DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysProvider.kt');
  for (const fragment of [
    'override val mainPage',
    'override suspend fun getMainPage',
    'override suspend fun search',
    'override suspend fun quickSearch',
    'override suspend fun load',
    'override suspend fun loadLinks',
    'requestJson',
    'safeSources',
    'newMovieLoadResponse',
    'newTvSeriesLoadResponse',
    'newExtractorLink',
    'ExtractorLinkType.M3U8',
    'ExtractorLinkType.DASH',
    'ExtractorLinkType.MAGNET',
    'Authorization',
  ]) {
    assert(provider.includes(fragment), `DdysProvider.kt missing ${fragment}`);
  }

  const plugin = await read('DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysPlugin.kt');
  assert(plugin.includes('@CloudstreamPlugin'), 'plugin missing @CloudstreamPlugin.');
  assert(plugin.includes('registerMainAPI(DdysProvider(settings))'), 'plugin does not register provider.');
  assert(plugin.includes('openSettings'), 'plugin missing settings entry.');
}

async function checkDocs() {
  const readme = await read('README.md');
  for (const fragment of ['CloudStream', 'repo.json', 'API Base', 'Android TV', '直链', 'GitHub Actions']) {
    assert(readme.includes(fragment), `README.md missing ${fragment}`);
  }
  const compatibility = await read('docs/compatibility.md');
  for (const fragment of ['manifestVersion: 1', 'plugins.json', 'registerMainAPI', 'newExtractorLink']) {
    assert(compatibility.includes(fragment), `compatibility.md missing ${fragment}`);
  }
}

async function checkSyntax() {
  for (const full of await listFiles(root)) {
    const rel = slash(path.relative(root, full));
    if (!/\.(js|mjs)$/i.test(rel)) continue;
    const result = spawnSync(process.execPath, ['--check', full], { stdio: 'inherit' });
    assert(result.status === 0, `${rel} failed node --check.`);
  }
}

async function checkForbiddenFiles() {
  for (const full of await listFiles(root)) {
    const rel = slash(path.relative(root, full));
    assert(!/(^|\/)(node_modules|coverage|package|\.git|\.gradle|build)(\/|$)/.test(rel), `forbidden path: ${rel}`);
    assert(!/\.(log|tmp|cache|tgz|zip|cs3|jar)$/i.test(rel) || rel === 'gradle/wrapper/gradle-wrapper.jar', `forbidden file: ${rel}`);
    assert(!/(^|\/)\.env(\.|$)/.test(rel), `forbidden env file: ${rel}`);
    assert(!['package-lock.json', 'pnpm-lock.yaml', 'yarn.lock'].includes(path.basename(rel)), `forbidden lockfile: ${rel}`);
  }
}

async function checkForbiddenText() {
  const patterns = ['ghp_', 'github_pat_', 'npm_', '\uFFFD'];
  for (const full of await listFiles(root)) {
    const rel = slash(path.relative(root, full));
    if (!isTextFile(rel) || rel === 'tools/check.mjs') continue;
    const text = await fs.readFile(full, 'utf8');
    for (const pattern of patterns) assert(!text.includes(pattern), `${rel} contains forbidden text pattern ${pattern}.`);
  }
}

async function mustExist(rel) {
  try {
    await fs.stat(path.join(root, rel));
  } catch {
    failures.push(`Missing required file: ${rel}`);
  }
}

async function read(rel) {
  return fs.readFile(path.join(root, rel), 'utf8');
}

async function listFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const out = [];
  for (const entry of entries) {
    if (['.git', 'node_modules', 'coverage', 'package', '.gradle', 'build'].includes(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...await listFiles(full));
    else out.push(full);
  }
  return out;
}

function isTextFile(rel) {
  return /\.(kt|kts|xml|js|mjs|json|md|txt|ps1|yml|yaml|properties|bat)$/i.test(rel) ||
    rel === '.gitignore' ||
    rel === 'LICENSE' ||
    rel === 'gradlew';
}

function slash(value) {
  return value.replace(/\\/g, '/');
}

function assert(condition, message) {
  if (!condition) failures.push(message);
}
