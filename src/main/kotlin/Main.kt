package moe.tachyon
fun main()
{
    println("欢迎使用SSH工具")
    println("- 作为服务器启动请输出S")
    println("- 作为主机启动请输出H")
    println("- 作为客户端启动请输出G")
    when (readLine()?.uppercase())
    {
        "S" -> Server().start()
        "H" ->
        {
            println("请输入服务器地址（默认 localhost）")
            val serverHost = readLine().let { if (it.isNullOrBlank()) "localhost" else it }
            println("请输入服务器端口（默认 22）")
            val serverPort = readLine().let { if (it.isNullOrBlank()) 22 else it.toIntOrNull() ?: 22 }
            Master(serverHost, serverPort).run()
        }
        "G" ->
        {
            println("请输入要连接的主机ID")
            val masterId = readLine().let { if (it.isNullOrBlank()) "" else it }
            if (masterId.isBlank())
            {
                println("错误：主机ID不能为空，程序退出")
                return
            }
            println("请输入本地监听端口（默认 2222）")
            val port = readLine().let { if (it.isNullOrBlank()) 2222 else it.toIntOrNull() ?: 2222 }
            Guest(masterId, port).run()
        }
        else -> println("输入错误，程序退出")
    }
    while (true) Thread.sleep(99999)
}