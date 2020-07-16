@file:JvmName("-LoggerKt")

package lighttunnel.base.util

@Suppress("unused")
inline fun <reified T> T.loggerDelegate() = lazy { org.slf4j.LoggerFactory.getLogger(T::class.java)!! }
