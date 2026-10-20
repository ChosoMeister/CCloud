// Optional local-environment workaround for networks where dl.google.com (Google Maven)
// returns 404/403 (observed during the cinematic baseline, 2026-10-20).
// It does NOT change the project's dependencies; it only adds mirror repositories
// in front of the declared ones. Use it explicitly:
//
//   ./gradlew --init-script scripts/dev/mirror-init.gradle.kts assembleDebug
//
// CI must not use this file.
val mirrors = listOf(
    "https://maven.myket.ir",
    "https://maven.aliyun.com/repository/google",
)

fun RepositoryHandler.addMirrors() {
    mirrors.forEachIndexed { i, url ->
        maven { name = "LocalMirror$i"; setUrl(url) }
    }
}

settingsEvaluated {
    pluginManagement.repositories.addMirrors()
    dependencyResolutionManagement.repositories.addMirrors()
}
