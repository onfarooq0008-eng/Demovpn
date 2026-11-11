pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "FastVPN"
include(":app")

// ics-openvpn engine: picked up automatically when cloned to ./ics-openvpn (scripts/fetch_engine.sh --ics).
if (file("ics-openvpn/main").exists()) {
    include(":openvpn")
    project(":openvpn").projectDir = file("ics-openvpn/main")
}
