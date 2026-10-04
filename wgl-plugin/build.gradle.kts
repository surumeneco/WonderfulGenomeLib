import org.gradle.jvm.tasks.Jar

// Production code dependencies are configured in the root build.gradle.kts.

val verifyStandalonePluginJar by tasks.registering {
    dependsOn(tasks.named("jar"))
    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val contents = zipTree(jarFile)
        check(contents.matching { include("co/surumene/wgl/api/GenomeEngine.class") }.files.isNotEmpty()) {
            "plugin JAR does not contain wgl-api"
        }
        check(contents.matching { include("co/surumene/wgl/core/WonderfulGenomeEngine.class") }.files.isNotEmpty()) {
            "plugin JAR does not contain wgl-core"
        }
        check(contents.matching { include("co/surumene/wgl/plugin/WonderfulGenomeLibPlugin.class") }.files.isNotEmpty()) {
            "plugin JAR does not contain wgl-plugin"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyStandalonePluginJar)
}
