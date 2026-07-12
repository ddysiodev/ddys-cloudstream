# API Mapping

| CloudStream capability | DDYS API endpoint |
| --- | --- |
| Latest home row | `GET /latest?limit=<homeLimit>` |
| Hot home row | `GET /hot?limit=<homeLimit>` |
| Category pages | `GET /movies?type=<type>&page=<page>&per_page=<pageSize>` |
| Search | `GET /search?q=<keyword>&page=<page>&per_page=<pageSize>` |
| Detail | `GET /movies/{slug}` |
| Sources | `GET /movies/{slug}/sources` |
| Recommendations | `GET /movies/{slug}/related` |

The provider accepts common DDYS movie fields such as `slug`, `title`, `poster`, `fanart`, `overview`, `year`, `type`, `type_name`, `vod_*`, and `tags`.

Source groups are normalized from `items`, `resources`, `episodes`, `playlist`, `play`, `urls`, or `list`. Each resource can use `url`, `link`, `href`, `play_url`, `download_url`, `magnet`, or `ed2k`.

Playback behavior:

- Direct media links are emitted as `ExtractorLink`.
- `.m3u8` links use `ExtractorLinkType.M3U8`.
- `.mpd` links use `ExtractorLinkType.DASH`.
- Magnet links use `ExtractorLinkType.MAGNET`.
- External links are passed through CloudStream `loadExtractor` when external resources are enabled.
- When direct-only mode is enabled, non-direct links are skipped.
