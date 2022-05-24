import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun HttpClient.get(uri: String): String {
    val request = HttpRequest.newBuilder(URI(uri)).GET().build();
    val response = this.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

inline fun <reified T> Gson.fromJson(json: String): T {
    val t = object : TypeToken<T>() {}.type;
    return this.fromJson(json, t) as T
}

fun main() {
    val gson = Gson();
    val httpClient = HttpClient.newHttpClient()
    val out = File("out").apply { mkdir() }

    val index = gson.fromJson<JsonArray>(httpClient.get("https://betacraft.uk/server-archive/server_index.json"))
    val manifest = gson.fromJson<JsonObject>(httpClient.get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"))

    manifest["versions"].asJsonArray.map { it.asJsonObject }.parallelStream().forEach { version ->
        val id = version["id"].asString
        val url = version["url"].asString

        val versionInfo = gson.fromJson<JsonObject>(httpClient.get(url))
        val downloads = versionInfo["downloads"].asJsonObject

        var changed = false

        if (!downloads.has("server")) {
            val serverInfo = index.singleOrNull { it.asJsonObject["names"].asJsonArray.map { it.asString }.contains(id) }?.asJsonObject

            if (serverInfo != null) {
                val formats = serverInfo["available_formats"].asJsonArray
                val jarFormat = formats.map { it as JsonObject }.single { it["format"].asString == "jar" }

                downloads.add("server", JsonObject().apply {
                    addProperty("sha1", jarFormat["sha1"].asString.lowercase())
                    addProperty("size", jarFormat["size"].asInt)
                    addProperty("url", jarFormat["url"].asString)
                })

                changed = true
            }
        }

        // Replace lwjgl2 with babric fork
        versionInfo["libraries"]?.asJsonArray?.let { libraries ->
            libraries.map { it.asJsonObject }.forEach libraries@{ library ->
                val (groupId, name, version) = library["name"].asString.split(":")

                if (groupId == "org.lwjgl.lwjgl" && version.startsWith("2.")) {
                    library["rules"]?.asJsonArray?.let { rules ->
                        rules.map { it.asJsonObject }.forEach { rule ->
                            val action = rule["action"].asString
                            val os = rule["os"]?.asJsonObject

                            // Remove macos specific workarounds
                            if (action == "allow" && os != null && os["name"].asString == "osx") {
                                libraries.remove(library)
                                return@libraries
                            }
                        }
                    }

                    library.addProperty("name", "${groupId}:${name}:2.9.4-babric.1")
                    library.addProperty("url", "https://maven.glass-launcher.net/releases/")
                    library.remove("downloads")
                    library.remove("rules")
                    changed = true
                }
            }
        }

        if (changed) {
            version.addProperty("url", "https://babric.github.io/manifest-polyfill/$id.json")
            File(out, "$id.json").writeText(gson.toJson(versionInfo))
        }
    }

    File(out, "version_manifest_v2.json").writeText(gson.toJson(manifest))
}