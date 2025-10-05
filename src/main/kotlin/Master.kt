package moe.tachyon
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Master(
    val sshHost: String,
    val sshPort: Int,
)
{
    lateinit var socket: Socket
    lateinit var output: OutputStream
    lateinit var input: InputStream
    private val outputMutex = Mutex()
    var id: String? = null
    val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler()
    { _, throwable ->
        throwable.printStackTrace()
    })

    fun init()
    {
        socket = Socket()
        socket.reuseAddress = true
        socket.connect(InetSocketAddress(serverHost, serverPort))
        input = socket.inputStream
        output = socket.outputStream
        output.writePackage(RegisterHost(id))
        println("已连接到服务器 $serverHost:$serverPort，等待主机连接...")
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun run()
    {
        while (true)
        {
            init()
            runCatching()
            {
                input.bufferedReader().forEachPackage()
                {
                    when (it)
                    {
                        is Client        -> makeClient(it)
                        is ConnectToHost -> Unit
                        Ping             -> Unit
                        is RegisterHost  ->
                        {
                            id = it.id
                            println("注册主机成功，主机ID：${it.id}，请在客户端使用该ID连接")
                        }
                    }
                }
            }.onFailure(Throwable::printStackTrace)
        }
    }

    fun makeClient(client: Client) = coroutineScope.launch()
    {
        println("收到主机连接请求，正在连接到客户端 (${client.host}:${client.port})...")
        val clientSocket = Socket()

        clientSocket.reuseAddress = true
        clientSocket.bind(InetSocketAddress(socket.localAddress, socket.localPort))
        var success = false
        repeat(5)
        {
            runCatching()
            {
                clientSocket.connect(InetSocketAddress(client.host, client.port), 5000)
                success = true
            }
            if (success) return@repeat
        }
        require(success) { "无法连接到主机 (${client.host}:${client.port})" }
        ClientManager(clientSocket).start()
    }

    private inner class ClientManager(val socket: Socket)
    {
        val sshMap = mutableMapOf<String, Triple<Socket, InputStream, OutputStream>>()
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        @OptIn(ExperimentalUuidApi::class)
        fun start() = coroutineScope.launch()
        {
            runCatching()
            {
                while (true)
                {
                    val uuidBytes = ByteArray(16)
                    input.readFully(uuidBytes)
                    val uuidString = Uuid.fromByteArray(uuidBytes).toHexString()
                    val length = input.readInt()
                    if (length == -1)
                    {
                        val (ssh, sshInput, sshOutput) = sshMap.remove(uuidString) ?: continue
                        runCatching { sshInput.close() }
                        runCatching { sshOutput.close() }
                        runCatching { ssh.close() }
                        continue
                    }
                    if (length == 0)
                    {
                        runCatching { createSSH(uuidString) }.onFailure(Throwable::printStackTrace)
                        continue
                    }
                    val data = ByteArray(length)
                    input.readFully(data)
                    val (_, _, sshOutput) = sshMap[uuidString] ?: outputMutex.withLock()
                    {
                        output.write(uuidBytes)
                        output.writeInt(-1)
                        output.flush()
                        continue
                    }
                    sshOutput.write(data)
                }
            }.onFailure(Throwable::printStackTrace)
            sshMap.values.forEach()
            {
                val (ssh, sshInput, sshOutput) = it
                runCatching { sshInput.close() }
                runCatching { sshOutput.close() }
                runCatching { ssh.close() }
            }
            socket.close()
            println("客户端连接已断开(${socket.inetAddress.hostAddress}:${socket.port})")
        }

        @OptIn(ExperimentalUuidApi::class)
        fun createSSH(uuidString: String)
        {
            println("创建SSH连接，UUID：$uuidString")
            val uuid = Uuid.parseHex(uuidString)
            val sshSocket = Socket()
            sshSocket.connect(InetSocketAddress(sshHost, sshPort))
            val sshInput = sshSocket.getInputStream()
            val sshOutput = sshSocket.getOutputStream()
            sshMap[uuidString] = Triple(sshSocket, sshInput, sshOutput)
            coroutineScope.launch()
            {
                runCatching()
                {
                    val buffer = ByteArray(8192)
                    while (true)
                    {
                        val read = sshInput.read(buffer)
                        if (read == -1) break
                        outputMutex.withLock()
                        {
                            output.write(uuid.toByteArray())
                            output.writeInt(read)
                            output.write(buffer, 0, read)
                            output.flush()
                        }
                    }
                }
                runCatching { sshInput.close() }
                runCatching { sshOutput.close() }
                runCatching { sshSocket.close() }
                runCatching { sshMap.remove(uuidString) }
                runCatching()
                {
                    outputMutex.withLock()
                    {
                        output.write(uuid.toByteArray())
                        output.writeInt(-1)
                        output.flush()
                    }
                }
                println("SSH连接已断开 ($uuidString)")
            }
        }
    }
}