pluginManagement {
  repositories {
    mavenLocal()
    maven("http://depot.sankuai.com/nexus/content/groups/public/") {
      isAllowInsecureProtocol = true
    }
    maven("https://pixel.sankuai.com/repository/mtdp")
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    maven("http://depot.sankuai.com/nexus/content/groups/public/") {
      isAllowInsecureProtocol = true
    }
    maven("https://pixel.sankuai.com/repository/mtdp")
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")
