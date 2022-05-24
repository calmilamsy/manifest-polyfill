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

                version.addProperty("url", "https://babric.github.io/manifest-polyfill/$id.json")
                File(out, "$id.json").writeText(gson.toJson(versionInfo))
            }
        }
    }

    File(out, "version_manifest_v2.json").writeText(gson.toJson(manifest))
}