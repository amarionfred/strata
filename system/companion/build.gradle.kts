plugins { id("com.android.application") version "9.4.1" }

android {
    namespace = "dev.omalauncher.companion"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.omalauncher.systempreview"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-preview"
    }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
    sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("generated/omarchy-assets").get().asFile.path)
    sourceSets.getByName("main").kotlin.directories.add(layout.buildDirectory.dir("generated/omarchy-sources").get().asFile.path)
    sourceSets.getByName("main").java.directories.add("../../app/src/main/java")
    sourceSets.getByName("test").java.directories.add("../../app/src/test/java")
}

// Compile the launcher's actual parser/renderer, without its PRoot-dependent runtime.
val syncOmarchySources = tasks.register<Sync>("syncOmarchySources") {
    from("../../app/src/main/kotlin") {
        include("dev/omalauncher/theme/ThemeColors.kt", "dev/omalauncher/theme/Templates.kt")
    }
    into(layout.buildDirectory.dir("generated/omarchy-sources"))
}
val syncOmarchyThemes = tasks.register<Sync>("syncOmarchyThemes") {
    from("../../app/src/main/assets") { include("themes/*.toml") }
    into(layout.buildDirectory.dir("generated/omarchy-assets"))
}
tasks.named("preBuild") { dependsOn(syncOmarchyThemes, syncOmarchySources) }

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    compileOnly("androidx.annotation:annotation:1.3.0")
    testImplementation("junit:junit:4.13.2")
}
