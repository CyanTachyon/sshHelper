package moe.tachyon

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import java.net.ServerSocket
import java.net.Socket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Server
{
    val masters = mutableMapOf<String, Socket>()

    val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler()
    { _, throwable ->
        throwable.printStackTrace()
    })

    fun start()
    {
        val serverSocket = ServerSocket(serverPort)
        println("服务器已启动，监听端口 $serverPort")
        while (true)
        {
            val clientSocket = serverSocket.accept()
            println("收到连接请求，来自 ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
            deal(clientSocket)
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
    fun deal(socket: Socket) = coroutineScope.launch()
    {
        var master: String? = null
        runCatching()
        {
            socket.inputStream.bufferedReader().forEachPackage()
            { p ->
                when (p)
                {
                    is RegisterHost   ->
                    {
                        println("got handshake, from ${socket.inetAddress.hostAddress}:${socket.port}")
                        val id = p.id ?: Uuid.random().toHexString()
                        master = id
                        val output = socket.outputStream
                        output.write(json.encodeToString(RegisterHost(id) as Package).toByteArray())
                        masters[id] = socket
                        output.write("\n".toByteArray())
                        output.flush()
                    }

                    is ConnectToHost ->
                    {
                        val master = masters[p.id]
                        val output = socket.outputStream
                        if (master == null)
                        {
                            println("错误：未找到对应的主机，来自 ${socket.inetAddress.hostAddress}:${socket.port}")
                            output.write(json.encodeToString(Client(host = "", port = 0) as Package).toByteArray())
                            output.write("\n".toByteArray())
                            output.flush()
                            delay(1000)
                            socket.close()
                            return@launch
                        }
                        output.write(json.encodeToString(Client(host = master.inetAddress.hostAddress, port = master.port) as Package).toByteArray())
                        output.write("\n".toByteArray())
                        output.flush()
                        val masterOutput = master.outputStream
                        masterOutput.write(json.encodeToString(Client(host = socket.inetAddress.hostAddress, port = socket.port) as Package).toByteArray())
                        masterOutput.write("\n".toByteArray())
                        masterOutput.flush()
                        println("客户端连接到主机 ${master.inetAddress.hostAddress}:${master.port}")
                    }
                    is Client   -> println("错误：客户端不应发送 Client 包，来自 ${socket.inetAddress.hostAddress}:${socket.port}")
                    Ping -> Unit
                }
            }
        }
        master?.let { masters.remove(it) }
    }
}