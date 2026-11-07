rootProject.name = "CloudstreamPlugins"

// Este ciclo publica apenas a nova extensão de teste de filmes.
// Os módulos antigos são mantidos no repositório, mas não entram no build.
val disabled = listOf(
    "Donflix",
    "SuperflixFilmes",
    "SuperflixSeries",
    "SuperflixAnimes",
    "SuperflixDoramas"
)

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
