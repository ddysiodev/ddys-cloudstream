version = 2

cloudstream {
    description = "DDYS API CloudStream provider with search, categories, details, episodes, and configurable playback sources."
    authors = listOf("DDYS")
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Anime", "Documentary", "Others")
    language = "zh"
    iconUrl = "https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/main/assets/icon.png"
    requiresResources = false
}

android {
    buildFeatures {
        buildConfig = true
    }
}
