# DDYS CloudStream

DDYS CloudStream is the official CloudStream extension repository for the DDYS API. It targets Android and Android TV users with home rows, categories, search, detail pages, source lists, playback link handling, and configurable API settings.

## Features

- CloudStream repository entry: `repo.json`
- DDYS Kotlin provider: `DdysProvider`
- Home rows: latest, hot, movies, series, anime, variety, documentaries
- Paginated search
- Category browsing through DDYS API `type`
- Detail pages with poster, backdrop, year, plot, tags, and recommendations
- Playback links for direct media, M3U8, MPD, magnets, and extractor-compatible external links
- Series/anime/variety resource lists as selectable episodes
- Movie pages aggregate all available playback sources
- Plugin settings for API Base, Site Base, API Key, page size, home limit, direct-only mode, and external resources
- GitHub Actions build for `.cs3` and `plugins.json`
- Static checks, Node tests, and Release ZIP packaging

## Install

Add this repository URL in CloudStream:

```text
https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/main/repo.json
```

Generated plugin metadata is published at:

```text
https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/builds/plugins.json
```

## Settings

After installing the extension, open plugin settings to configure:

- `API Base`: default `https://ddys.io/api/v1`
- `Site Base`: default `https://ddys.io`
- `API Key`: optional for public read-only endpoints
- Page size: default 24, range 1-80
- Home limit: default 24, range 1-80
- Direct-only resources
- External/cloud/magnet resources

Use your DDYS Worker Proxy URL as `API Base` if you run one.

## Local Checks

```bash
node tools/check.mjs
node --test tests/*.test.mjs
```

Android compilation runs in GitHub Actions. Static checks still work on machines without a local JDK or Android SDK.

## License

MIT
