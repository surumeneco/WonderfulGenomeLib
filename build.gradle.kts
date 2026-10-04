plugins {
    base
}

group = "co.surumene"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.0.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

project(":wgl-core") {
    dependencies {
        "api"(project(":wgl-api"))
    }
}

project(":wgl-plugin") {
    dependencies {
        "api"(project(":wgl-api"))
        "implementation"(project(":wgl-core"))
        "compileOnly"("io.papermc.paper:paper-api:26.2.build.129-stable")
    }
}
