import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java

    `maven-publish`
}

group = "me.docdrewskii.profitmultiplier"
version = "1.5.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    withSourcesJar()
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://libraries.minecraft.net/")

    maven("https://repo.helpch.at/releases/")

    maven("https://jitpack.io")
}

configurations.compileClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {

    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.7.3")

    compileOnly("me.clip:placeholderapi:2.11.7")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"

        options.compilerArgs.add("-Xlint:-options")
    }

    jar {
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

val apiInclude = "me/docdrewskii/profitmultiplier/api/**"
val apiExclude = "me/docdrewskii/profitmultiplier/api/impl/**"

val apiJar by tasks.registering(Jar::class) {
    archiveBaseName.set("ProfitMultiplier-API")
    archiveClassifier.set("")
    from(sourceSets.main.get().output) {
        include(apiInclude)
        exclude(apiExclude)
    }
}

val apiSourcesJar by tasks.registering(Jar::class) {
    archiveBaseName.set("ProfitMultiplier-API")
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allJava) {
        include(apiInclude)
        exclude(apiExclude)
    }
}

tasks.named("build") {
    dependsOn(apiJar, apiSourcesJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {

            from(components["java"])
        }
    }
}
