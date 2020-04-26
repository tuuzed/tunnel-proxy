package lighttunnel.server

import io.netty.handler.ssl.util.SelfSignedCertificate
import lighttunnel.logger.LoggerFactory
import lighttunnel.server.http.HttpPluginImplStaticFile
import lighttunnel.util.SslContextUtil
import org.apache.log4j.Level
import org.junit.Before
import org.junit.Test

class TunnelServerTest {

    private lateinit var tunnelServer: TunnelServer

    @Before
    fun setUp() {
        LoggerFactory.configConsole(Level.OFF, names = *arrayOf(
            "io.netty",
            "org.ini4j",
            "org.slf4j",
            "org.json",
            "org.apache.commons.cli"
        ))
        LoggerFactory.configConsole(level = Level.ALL)
        tunnelServer = TunnelServer(
            bindPort = 5080,
            sslBindPort = 5443,
            sslContext = SslContextUtil.forBuiltinServer(),
            httpBindPort = 8080,
            httpsBindPort = 8443,
            httpsContext = SslContextUtil.forBuiltinServer(),
            dashboardBindPort = 5081,
            httpPlugin = HttpPluginImplStaticFile(
                rootPathList = listOf("C:\\", "D:\\"),
                domainPrefixList = listOf("127.0.0.1")
            )
        )
    }

    @Test
    fun start() {
        tunnelServer.start()
        Thread.currentThread().join()
    }

}