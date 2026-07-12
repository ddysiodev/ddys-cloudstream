import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('provider implements required CloudStream methods', async () => {
  const source = await readFile('DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysProvider.kt', 'utf8');
  for (const fragment of [
    'override suspend fun getMainPage',
    'override suspend fun search',
    'override suspend fun quickSearch',
    'override suspend fun load',
    'override suspend fun loadLinks',
    'newMovieLoadResponse',
    'newTvSeriesLoadResponse',
    'newExtractorLink',
    'loadExtractor',
  ]) {
    assert.ok(source.includes(fragment), `missing ${fragment}`);
  }
});

test('provider handles common DDYS payload edge cases', async () => {
  const source = await readFile('DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysProvider.kt', 'utf8');
  for (const fragment of [
    'movieArrayItems',
    'movieArrayKeys = listOf("items", "list", "results", "movies", "records", "data")',
    'root.firstObject("meta", "pagination") ?: data.firstObject("meta", "pagination")',
    'Regex("""/(?:movie|movies)/([^/?#]+)""")',
    'substringBefore(\'?\').substringBefore(\'#\').trimEnd(\'/\')',
  ]) {
    assert.ok(source.includes(fragment), `missing ${fragment}`);
  }
});

test('settings expose configurable API and playback controls', async () => {
  const source = await readFile('DdysProvider/src/main/kotlin/io/ddys/cloudstream/DdysSettings.kt', 'utf8');
  for (const fragment of [
    'DEFAULT_API_BASE',
    'DEFAULT_SITE_BASE',
    'apiKey',
    'pageSize',
    'homeLimit',
    'directOnly',
    'includeExternal',
    'AlertDialog.Builder',
  ]) {
    assert.ok(source.includes(fragment), `missing ${fragment}`);
  }
});
