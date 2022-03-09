import groovy.json.JsonSlurper
import java.net.URL

@Suppress("UNCHECKED_CAST")
task("build") {
    buildDir.mkdir()

    val index = JsonSlurper().parse(URL("https://betacraft.uk/server-archive/server_index.json")) as List<Map<String, *>>

    val manifest = JsonSlurper().parse(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")) as Map<String, *>

    (manifest["versions"] as List<MutableMap<String, Any>>).parallelStream().forEach { version ->
        val id = version["id"] as String
        val url = version["url"] as String

        val versionInfo = JsonSlurper().parse(URL(url)) as Map<String, *>
        val downloads = versionInfo["downloads"] as MutableMap<String, Map<String, *>>

        if (!downloads.containsKey("server")) {
            val serverInfo = index.singleOrNull { (it["names"] as List<String>).contains(id) }

            if (serverInfo != null) {
                val formats = serverInfo["available_formats"] as List<Map<String, *>>
                val jarFormat = formats.single { it["format"] as String == "jar" }

                downloads["server"] = mapOf(
                    "sha1" to (jarFormat["sha1"] as String).toLowerCase(),
                    "size" to jarFormat["size"] as Int,
                    "url" to jarFormat["url"] as String,
                )

                version["url"] = "https://babric.github.io/manifest-polyfill/$id.json"
                File(buildDir, "$id.json").writeText(groovy.json.JsonOutput.toJson(versionInfo))
            }
        }
    }

    File(buildDir, "version_manifest.json").writeText(groovy.json.JsonOutput.toJson(manifest))
}