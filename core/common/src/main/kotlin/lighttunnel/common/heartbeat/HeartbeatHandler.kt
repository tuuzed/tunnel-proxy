package lighttunnel.common.heartbeat

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import lighttunnel.common.proto.msg.ProtoMsgPing
import lighttunnel.common.utils.injectLogger
import java.util.concurrent.TimeUnit

/**
 * 心跳处理器
 */
class HeartbeatHandler(
    observeOutput: Boolean = false,
    readerIdleTime: Long = 0L,
    writerIdleTime: Long = 0L,
    allIdleTime: Long = 0L,
    unit: TimeUnit = TimeUnit.SECONDS,
    private val callback: HeartbeatCallback? = null
) : IdleStateHandler(observeOutput, readerIdleTime, writerIdleTime, allIdleTime, unit) {

    private val logger by injectLogger()

    override fun channelIdle(ctx: ChannelHandlerContext, evt: IdleStateEvent) {
        logger.trace("channelIdle: {}, {}", ctx, evt)
        ctx.channel().writeAndFlush(ProtoMsgPing)
        if (callback?.invoke(ctx, evt) != true) {
            super.channelIdle(ctx, evt)
        }
    }
}
