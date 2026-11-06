pluginManagement {
    repositories {
        // Tencent + Huawei public mirrors FIRST. They proxy Maven Central + Google and — critically
        // — return HTTP 404 for missing artifacts (e.g. pom-only plugin markers have no .jar), which
        // Gradle handles correctly. The Aliyun mirrors return 502 for missing artifacts, which Gradle
        // treats as a fatal server error and aborts plugin resolution on. Maven Central itself is
        // 429-rate-limiting the build sandbox IP on cold cache re-download. Canonical repos remain
        // as final fallbacks.
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven-public") }
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven-public") }
        google(); mavenCentral()
    }
}
rootProject.name = "LeafGuard"
include(":app")
