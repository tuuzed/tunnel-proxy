package com.tuuzed.tunnel.server;

import com.tuuzed.tunnel.common.logging.Logger;
import com.tuuzed.tunnel.common.logging.LoggerFactory;
import com.tuuzed.tunnel.common.protocol.TunnelMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static com.tuuzed.tunnel.common.protocol.TunnelConstants.*;

/**
 * 隧道数据通道处理器
 */
@SuppressWarnings("Duplicates")
public class UserTunnelChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(UserTunnelChannelHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("channelActive: {}", ctx);
        super.channelActive(ctx);
        UserTunnel tunnel = getUserTunnel(ctx);
        if (tunnel != null) {
            final Channel serverChannel = tunnel.serverChannel();
            final Long tunnelToken = serverChannel.attr(ATTR_TUNNEL_TOKEN).get();
            if (tunnelToken != null) {
                Long sessionToken = ctx.channel().attr(ATTR_SESSION_TOKEN).get();
                if (sessionToken == null) {
                    sessionToken = tunnel.generateSessionToken();
                    ctx.channel().attr(ATTR_SESSION_TOKEN).set(sessionToken);
                }
                serverChannel.writeAndFlush(
                        TunnelMessage.newInstance(MESSAGE_TYPE_CONNECT_LOCAL_TUNNEL)
                                .setHead(Unpooled.copyLong(tunnelToken, sessionToken).array())
                );
                tunnel.putUserTunnelChannel(tunnelToken, sessionToken, ctx.channel());
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("channelInactive: {}", ctx);
        UserTunnel tunnel = getUserTunnel(ctx);
        if (tunnel != null) {
            final Channel serverChannel = tunnel.serverChannel();
            final Long tunnelToken = serverChannel.attr(ATTR_TUNNEL_TOKEN).get();
            final Long sessionToken = ctx.channel().attr(ATTR_SESSION_TOKEN).get();
            if (tunnelToken != null && sessionToken != null) {
                tunnel.removeUserTunnelChannel(tunnelToken, sessionToken);
            }
            ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    serverChannel.writeAndFlush(
                            TunnelMessage.newInstance(MESSAGE_TYPE_USER_TUNNEL_DISCONNECT)
                                    .setHead(Unpooled.copyLong(tunnelToken, sessionToken).array())
                    );
                }
            });
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("exceptionCaught: ", cause);
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        logger.debug("channelRead0: {}", ctx);
        int length = msg.readableBytes();
        byte[] data = new byte[length];
        msg.readBytes(data);
        // 根据入站端口获取用户隧道,如果用户隧道不存在则直接关闭连接
        UserTunnel tunnel = getUserTunnel(ctx);
        if (tunnel != null) {
            final Channel serverChannel = tunnel.serverChannel();
            final Long tunnelToken = serverChannel.attr(ATTR_TUNNEL_TOKEN).get();
            final Long sessionToken = ctx.channel().attr(ATTR_SESSION_TOKEN).get();
            if (tunnelToken != null && sessionToken != null) {
                serverChannel.writeAndFlush(
                        TunnelMessage.newInstance(MESSAGE_TYPE_TRANSFER)
                                .setHead(Unpooled.copyLong(tunnelToken, sessionToken).array())
                                .setData(data)
                );
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        UserTunnel tunnel = getUserTunnel(ctx);
        if (tunnel != null) {
            Channel serverChannel = tunnel.serverChannel();
            serverChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }


    // ====================== 工具方法 =========================== //

    /**
     * 获取隧道对象
     */
    @Nullable
    private static UserTunnel getUserTunnel(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }
        SocketAddress socketAddress = ctx.channel().localAddress();
        if (socketAddress instanceof InetSocketAddress) {
            int inboundPort = ((InetSocketAddress) socketAddress).getPort();
            UserTunnel tunnel = UserTunnelManager.getInstance().getUserTunnelByBindPort(inboundPort);
            if (tunnel == null) {
                ctx.close();
                return null;
            }
            return tunnel;
        }
        return null;
    }
}
