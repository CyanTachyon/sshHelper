package moe.tachyon
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.OutputStream

val serverHost = "server.tachyon.moe"
val serverPort = 7788

@Serializable
sealed interface Package

@Serializable
@SerialName("ping")
data object Ping: Package

@Serializable
@SerialName("register_host")
data class RegisterHost(val id: String? = null): Package

@Serializable
@SerialName("connect_to_host")
data class ConnectToHost(val id: String): Package

@Serializable
@SerialName("client")
data class Client(val host: String, val port: Int): Package

@OptIn(ExperimentalSerializationApi::class)
val json = Json()
{
    ignoreUnknownKeys = true
    prettyPrint = false
    encodeDefaults = true
    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
    explicitNulls = false
}

fun BufferedReader.readPackage(): Package?
{
    var line = this.readLine() ?: return null
    while (line.isBlank()) line = this.readLine() ?: return null
    return json.decodeFromString<Package>(line)
}

inline fun BufferedReader.forEachPackage(action: (Package) -> Unit)
{
    while (true)
    {
        val p = this.readPackage() ?: break
        action(p)
    }
}

fun OutputStream.writePackage(p: Package)
{
    this.write(json.encodeToString(p).toByteArray())
    this.write("\n".toByteArray())
    this.flush()
}