package lighttunnel.lts.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.netty.handler.ssl.SslContext
import lighttunnel.common.extensions.asInt
import lighttunnel.common.utils.*
import lighttunnel.logger.LoggerConfigure
import lighttunnel.lts.internal.*
import lighttunnel.server.TunnelRequestInterceptor
import lighttunnel.server.args.HttpTunnelArgs
import lighttunnel.server.args.HttpsTunnelArgs
import lighttunnel.server.args.TunnelDaemonArgs
import lighttunnel.server.args.TunnelSslDaemonArgs
import lighttunnel.server.http.HttpPlugin
import lighttunnel.server.http.HttpTunnelRequestInterceptor
import org.apache.log4j.Level
import org.ini4j.Ini
import java.io.File

internal class RootCommand : CliktCommand(name = ManifestUtils.appName) {

    private val cmdShowVersion: Boolean by option("-v", "--version", help = "Show version").flag()

    private val cmdBossThreads: Int by option(
        "--boss-threads",
        help = "Number of boss threads, there is no upper limit by default"
    )
        .int()
        .default(-1)

    private val cmdWorkerThreads: Int by option(
        "--worker-threads",
        help = "Number of worker threads, there is no upper limit by default"
    )
        .int()
        .default(-1)

    private val cmdConfig: String? by option("-c", "--config", help = "config file")
        .default("")

    private val cmdBindAddr: Pair<String, Int> by option("--bind-addr", help = "Bind address, default: 0.0.0.0:7080")
        .convert { IpUtils.parseAddr(it) }
        .default("0.0.0.0" to 7080)

    private val cmdBindSslAddr: Pair<String, Int>? by option(
        "--bind-ssl-addr",
        help = "Bind address with ssl, e.g. 0.0.0.0:7443"
    ).convert { IpUtils.parseAddr(it) }

    private val cmdBindHttpAddr: Pair<String, Int>? by option(
        "--bind-http-addr",
        help = "Bind http address, e.g. 0.0.0.0:8080"
    ).convert { IpUtils.parseAddr(it) }

    private val cmdHttpsBindAddr: Pair<String, Int>? by option(
        "--bind-https-addr",
        help = "Bind https address, e.g. 0.0.0.0:8443"
    ).convert { IpUtils.parseAddr(it) }

    private val cmdHttpApiBindAddr: Pair<String, Int>? by option(
        "--bind-http-api-addr",
        help = "Bind http api address, e.g. 0.0.0.0:7081"
    ).convert { IpUtils.parseAddr(it) }

    private val cmdHttpApiUsername: String? by option("--http-api-username", help = "Http api username")

    private val cmdHttpApiPassword: String? by option("--http-api-password", help = "Http api password")

    private val cmdAuthToken: String? by option("--auth-token", help = "Auth token")

    private val cmdAllowPorts: String? by option(
        "--allow-ports",
        help = "TCP tunnel allow ports, e.g. 1024,1025,1026-65535, default: 1024-65535"
    )
        .default("1024-65535")


    private val cmdLogLevel: Level by option(
        "--log-level",
        help = "Log level, e.g. [OFF|FATAL|ERROR|WARN|INFO|DEBUG|TRACE|ALL], default: INFO"
    )
        .convert { Level.toLevel(it, Level.INFO) }
        .default(Level.INFO)

    private val cmdLogFile: String? by option("--log-file", help = "Save log to file, e.g. lts.log")

    private val cmdLogFileSize: String by option("--log-file-size", help = "Save log file size, e.g. 1MB")
        .default("1MB")

    private val cmdLogFileCount: Int by option("--log-file-count", help = "Log file count, default: 10")
        .int()
        .default(10)


    override fun run() {
        // 调试内存泄漏
        // ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
        if (cmdShowVersion) {
            echo("   App-Name: ${ManifestUtils.appName}")
            echo("App-Version: ${ManifestUtils.version}")
            echo(" Build-Date: ${ManifestUtils.buildDate}")
            echo("Commit-Date: ${ManifestUtils.commitDate}")
            echo("Commit-Hash: ${ManifestUtils.commitHash}")
            return
        }
        configureLogger()
        startupServer()
        Thread.currentThread().join()
    }


    /** 是否从命令参数启动 */
    private val isStartFromCommand: Boolean get() = cmdConfig.isNullOrEmpty()

    private val ini: Ini by lazy { Ini().also { it.load(File(cmdConfig ?: "lts.ini")) } }

    private fun configureLogger() {
        val level: Level
        val file: String?
        val maxBackupIndex: Int
        val maxFileSize: String
        if (isStartFromCommand) {
            level = cmdLogLevel
            file = cmdLogFile
            maxBackupIndex = cmdLogFileCount
            maxFileSize = cmdLogFileSize
        } else {
            val basic = ini["basic"] ?: return
            level = Level.toLevel(basic["log_level"], Level.INFO)
            file = basic["log_file"]
            maxBackupIndex = basic["log_count"].asInt() ?: 3
            maxFileSize = basic["log_size"] ?: "1MB"
        }
        LoggerConfigure.configConsole(
            Level.WARN, names = arrayOf(
                "io.netty",
                "org.ini4j",
                "org.slf4j",
                "org.json",
            )
        )
        LoggerConfigure.configConsole(cmdLogLevel, conversionPattern = "%-d{yyyy-MM-dd HH:mm:ss} - [%5p] %m%n")
        if (file != null) {
            LoggerConfigure.configFile(
                file = file,
                maxBackupIndex = maxBackupIndex,
                level = level,
                maxFileSize = FileUtils.toFileSize(maxFileSize, 1024 * 1024)
            )
        }
    }

    private fun startupServer() {
        val bossThreads: Int
        val workerThreads: Int

        val bindAddr: Pair<String, Int>
        val bindSslAddr: Pair<String, Int>?
        val tunnelSslContext: SslContext

        val bindHttpAddr: Pair<String, Int>?
        val bindHttpsAddr: Pair<String, Int>?
        val httpsSslContext: SslContext

        val bindHttpApiAddr: Pair<String, Int>?
        val httpApiUsername: String?
        val httpApiPassword: String?

        val tunnelRequestInterceptor: TunnelRequestInterceptor
        val httpTunnelRequestInterceptor: HttpTunnelRequestInterceptor
        val httpPlugin: HttpPlugin?

        if (isStartFromCommand) {
            bossThreads = cmdBossThreads
            workerThreads = cmdWorkerThreads

            bindAddr = cmdBindAddr
            bindSslAddr = cmdBindSslAddr
            tunnelSslContext = SslContextUtils.forBuiltinServer()

            bindHttpAddr = cmdBindHttpAddr
            bindHttpsAddr = cmdHttpsBindAddr
            httpsSslContext = SslContextUtils.forBuiltinServer()

            bindHttpApiAddr = cmdHttpApiBindAddr
            httpApiUsername = cmdHttpApiUsername
            httpApiPassword = cmdHttpApiPassword

            tunnelRequestInterceptor = DefaultTunnelRequestInterceptor(
                authToken = cmdAuthToken,
                allowPorts = cmdAllowPorts,
            )
            httpTunnelRequestInterceptor = DefaultHttpTunnelRequestInterceptor()
            httpPlugin = null
        } else {
            val basic = ini["basic"] ?: return
            bossThreads = basic["boss_threads"].asInt() ?: -1
            workerThreads = basic["worker_threads"].asInt() ?: -1

            bindAddr = basic["bind_addr"]?.let { IpUtils.parseAddr(it) } ?: ("0.0.0.0" to 7080)
            bindSslAddr = basic["bind_ssl_addr"]?.let { IpUtils.parseAddr(it) }

            tunnelSslContext = runCatching {
                SslContextUtils.forServer(
                    jks = basic["https_jks"] ?: "lts.jks",
                    storePassword = basic["https_store_password"] ?: "ltspass",
                    keyPassword = basic["https_key_password"] ?: "ltspass",
                )
            }.getOrNull() ?: SslContextUtils.forBuiltinServer()

            bindHttpAddr = basic["bind_http_addr"]?.let { IpUtils.parseAddr(it) }
            bindHttpsAddr = basic["bind_https_addr"]?.let { IpUtils.parseAddr(it) }

            httpsSslContext = runCatching {
                SslContextUtils.forServer(
                    jks = basic["https_jks"] ?: "lts.jks",
                    storePassword = basic["https_store_password"] ?: "ltspass",
                    keyPassword = basic["https_key_password"] ?: "ltspass",
                )
            }.getOrNull() ?: SslContextUtils.forBuiltinServer()

            bindHttpApiAddr = basic["bind_http_api_addr"]?.let { IpUtils.parseAddr(it) }
            httpApiUsername = basic["http_api_username"]
            httpApiPassword = basic["http_api_password"]

            tunnelRequestInterceptor = DefaultTunnelRequestInterceptor(
                authToken = cmdAuthToken,
                allowPorts = cmdAllowPorts,
            )
            httpTunnelRequestInterceptor = DefaultHttpTunnelRequestInterceptor()
            httpPlugin = StaticFileHttpPlugin(
                paths = basic["plugin_sf_paths"]?.split(",")?.toList() ?: emptyList(),
                hosts = basic["plugin_sf_hosts"]?.split(",")?.toList() ?: emptyList()
            )
        }
        lighttunnel.server.Server(
            bossThreads = bossThreads,
            workerThreads = workerThreads,
            tunnelDaemonArgs = TunnelDaemonArgs(
                bindIp = bindAddr.first,
                bindPort = bindAddr.second,
                tunnelRequestInterceptor = tunnelRequestInterceptor,
            ),
            tunnelSslDaemonArgs = bindSslAddr?.let {
                TunnelSslDaemonArgs(
                    bindIp = bindSslAddr.first,
                    bindPort = bindSslAddr.second,
                    tunnelRequestInterceptor = tunnelRequestInterceptor,
                    sslContext = tunnelSslContext
                )
            },
            httpTunnelArgs = bindHttpAddr?.let {
                HttpTunnelArgs(
                    bindIp = bindHttpAddr.first,
                    bindPort = bindHttpAddr.second,
                    httpPlugin = httpPlugin,
                    httpTunnelRequestInterceptor = httpTunnelRequestInterceptor,
                )
            },
            httpsTunnelArgs = bindHttpsAddr?.let {
                HttpsTunnelArgs(
                    bindIp = bindHttpsAddr.first,
                    bindPort = bindHttpsAddr.second,
                    httpPlugin = httpPlugin,
                    httpTunnelRequestInterceptor = httpTunnelRequestInterceptor,
                    sslContext = httpsSslContext
                )
            },
            serverListener = DefaultServerListener(),
        ).start()
        if (bindHttpApiAddr != null) {
            LtsOpenApi().start(
                bindIp = bindHttpApiAddr.first,
                bindPort = bindHttpApiAddr.second,
                authProvider = if (httpApiUsername.isNullOrEmpty() || httpApiPassword.isNullOrEmpty()) {
                    null
                } else {
                    { username, password -> httpApiUsername == username && httpApiPassword == password }
                },
            )
        }
    }
}
