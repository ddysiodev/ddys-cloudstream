# Compatibility

This repository follows the current CloudStream extension repository pattern:

- `repo.json` uses `manifestVersion: 1`.
- `repo.json` points to a generated `plugins.json` on the `builds` branch.
- The provider is a Kotlin Android library subproject.
- The plugin is registered with `@CloudstreamPlugin`.
- `registerMainAPI(DdysProvider(...))` registers the provider.
- GitHub Actions builds `.cs3`, `.jar`, and `plugins.json`.

The provider uses these CloudStream APIs:

- `mainPageOf`
- `newHomePageResponse`
- `newSearchResponseList`
- `newMovieSearchResponse`
- `newMovieLoadResponse`
- `newTvSeriesLoadResponse`
- `newEpisode`
- `newExtractorLink`
- `loadExtractor`

Android TV considerations:

- Home rows use compact labels.
- Settings avoid custom resources and use native Android controls.
- Movies keep one detail page and expose all available links at playback time.
- Series-style content exposes resources as selectable episodes.

Known runtime dependencies:

- CloudStream pre-release stubs from `com.lagradost:cloudstream3:pre-release`
- NiceHttp from `com.github.Blatzar:NiceHttp:0.4.11`
- Jackson Kotlin module `2.13.1`
