dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 3

cloudstream {
    description = "Exemplo educacional com catálogo, busca, detalhes e reprodução de Big Buck Bunny, obra CC BY 3.0."
    authors = listOf("Cloudburst", "Luna712")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1

    tvTypes = listOf("Movie")

    requiresResources = true
    language = "pt-BR"

    // Original example icon retained from the template.
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
