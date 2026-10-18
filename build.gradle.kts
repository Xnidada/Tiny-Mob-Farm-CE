plugins {
    id("java")
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.3"
}

group = "cn.davidma.tinymobfarm"
version = "1.0.91-beta"

base {
    archivesName.set("TinyMobFarm-CE")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

minecraft {
    mcVersion.set("1.12.2")
    username.set("Developer")
}

repositories {
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "BlameJared Maven"
        url = uri("https://maven.blamejared.com")
    }
    mavenCentral()
}

dependencies {
    patchedMinecraft("me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0")
    compileOnly("CraftTweaker2:CraftTweaker2-API:4.1.20.715")
    compileOnly("CraftTweaker2:CraftTweaker2-MC1120-Main:1.12-4.1.20.715")
}

tasks.processResources.configure {
    inputs.property("version", project.version)
    inputs.property("mcversion", minecraft.mcVersion.get())

    filesMatching("mcmod.info") {
        expand(
            mapOf(
                "version" to project.version,
                "mcversion" to minecraft.mcVersion.get()
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}
