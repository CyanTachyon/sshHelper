package moe.tachyon

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Guest(masterId: String, val port: Int)
{
    val socket = Socket()
    val output: OutputStream
    val input: BufferedReader
    val outputMutex = Mutex()
    val coroutineScope = CoroutineScope(
        Dispatchers.IO + CoroutineExceptionHandler()
        { _, throwable ->
            throwable.printStackTrace()
        })

    init
    {
        socket.reuseAddress = true
        socket.connect(InetSocketAddress(serverHost, serverPort))
        input = socket.inputStream.bufferedReader()
        output = socket.outputStream
        output.writePackage(ConnectToHost(masterId))
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun run() = makeClient(input.readPackage() as Client)

    fun makeClient(client: Client)
    {
        if (client.port == 0)
        {
            println("错误：客户端不存在")
            exitProcess(1)
        }
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
        require(success) { "无法连接到客户端 (${client.host}:${client.port})" }
        println("连接到客户端 (${client.host}:${client.port})")
        val serverSocket = ServerSocket(port)
        println("请使用ssh连接 localhost:$port")

        val clientInput = DataInputStream(clientSocket.getInputStream())
        val clientOutput = DataOutputStream(clientSocket.getOutputStream())

        listenClient(clientSocket, clientInput, clientOutput)

        while (true)
        {
            val sshSocket = serverSocket.accept()
            val sshInput = sshSocket.getInputStream()
            val sshOutput = sshSocket.getOutputStream()
            println("收到SSH连接请求，来自 ${sshSocket.inetAddress.hostAddress}:${sshSocket.port}")
            launchForwarder(sshInput, sshOutput, clientOutput)
        }
    }

    val sshMap = mutableMapOf<String, Pair<InputStream, OutputStream>>()

    @OptIn(ExperimentalUuidApi::class)
    fun listenClient(clientSocket: Socket, input: DataInputStream, output: DataOutputStream) = coroutineScope.launch()
    {
        try
        {
            while (true)
            {
                val uuidBytes = ByteArray(16)
                input.readFully(uuidBytes)
                val uuidString = Uuid.fromByteArray(uuidBytes).toHexString()
                val length = input.readInt()
                val (sshInput, sshOutput) = sshMap[uuidString] ?: run()
                {
                    if (length >= 0) outputMutex.withLock()
                    {
                        input.skipBytes(length)
                        output.write(uuidBytes)
                        output.writeInt(-1)
                        output.flush()
                    }
                    continue
                }
                if (length == -1)
                {
                    runCatching { sshInput.close() }
                    runCatching { sshOutput.close() }
                    sshMap.remove(uuidString)
                    continue
                }
                val buffer = ByteArray(length)
                input.readFully(buffer)
                sshOutput.write(buffer)
                sshOutput.flush()
            }
        }
        catch (e: Throwable)
        {
            e.printStackTrace()
        }
        runCatching { clientSocket.close() }
        sshMap.values.forEach()
        {
            val (sshInput, sshOutput) = it
            runCatching { sshInput.close() }
            runCatching { sshOutput.close() }
        }
        println("客户端连接已断开 (${clientSocket.inetAddress.hostAddress}:${clientSocket.port})")
        exitProcess(0)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun launchForwarder(sshInput: InputStream, sshOutput: OutputStream, clientOutput: DataOutputStream)
    {
        val uuid = Uuid.random()
        val uuidBytes = uuid.toByteArray()
        val uuidString = uuid.toHexString()
        sshMap[uuidString] = Pair(sshInput, sshOutput)
        coroutineScope.launch()
        {
            runCatching()
            {
                outputMutex.withLock()
                {
                    clientOutput.write(uuidBytes)
                    clientOutput.writeInt(0)
                }
                val buffer = ByteArray(8192)
                while (true)
                {
                    val read = sshInput.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue
                    outputMutex.withLock()
                    {
                        clientOutput.write(uuidBytes)
                        clientOutput.writeInt(read)
                        clientOutput.write(buffer, 0, read)
                        clientOutput.flush()
                    }
                }
            }
            runCatching { sshInput.close() }
            runCatching { sshOutput.close() }
            runCatching { sshMap.remove(uuidString) }
            runCatching()
            {
                outputMutex.withLock()
                {
                    clientOutput.write(uuidBytes)
                    clientOutput.writeInt(-1)
                    clientOutput.flush()
                }
            }
            println("SSH连接已断开 ($uuidString)")
        }
    }
}