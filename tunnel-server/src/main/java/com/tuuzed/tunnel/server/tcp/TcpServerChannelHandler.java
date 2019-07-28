package com.tuuzed.tunnel.server.tcp;

import com.tuuzed.tunnel.common.logging.Logger;
import com.tuuzed.tunnel.common.logging.LoggerFactory;
import com.tuuzed.tunnel.common.proto.ProtoMessage;
import com.tuuzed.tunnel.server.internal.AttributeKeys;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

@SuppressWarnings("Duplicates")
public class TcpServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(TcpServerChannelHandler.class);

    @NotNull
    private final TcpServer tcpServer;

    public TcpServerChannelHandler(@NotNull TcpServer tcpServer) {
        this.tcpServer = tcpServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        int port = sa.getPort();
        TcpServer.Descriptor descriptor = tcpServer.getDescriptorByPort(port);
        if (descriptor != null) {
            final Channel tunnelChannel = descriptor.tunnelSessions().tunnelChannel();
            final long tunnelToken = descriptor.tunnelSessions().tunnelToken();
            Long sessionToken = ctx.channel().attr(AttributeKeys.SESSION_TOKEN).get();
            if (sessionToken == null) {
                sessionToken = descriptor.tunnelSessions().putSessionChannel(ctx.channel());
                ctx.channel().attr(AttributeKeys.SESSION_TOKEN).set(sessionToken);
            }
            tunnelChannel.writeAndFlush(
                new ProtoMessage(
                    ProtoMessage.Type.REMOTE_CONNECTED,
                    Unpooled.copyLong(tunnelToken, sessionToken).array(),
                    null
                )
            );
        } else {
            ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        int port = sa.getPort();
        TcpServer.Descriptor descriptor = tcpServer.getDescriptorByPort(port);
        if (descriptor != null) {
            final Channel tunnelChannel = descriptor.tunnelSessions().tunnelChannel();
            final long tunnelToken = descriptor.tunnelSessions().tunnelToken();
            final Long sessionToken = ctx.channel().attr(AttributeKeys.SESSION_TOKEN).get();
            if (sessionToken != null) {
                Channel channel = descriptor.tunnelSessions().removeSessionChannel(sessionToken);
                if (channel != null) {
                    channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
            // 解决 HTTP/1.x 数据传输问题
            ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    tunnelChannel.writeAndFlush(
                        new ProtoMessage(
                            ProtoMessage.Type.REMOTE_DISCONNECT,
                            Unpooled.copyLong(tunnelToken, sessionToken).array(),
                            null
                        )
                    );
                }
            });
        }
        super.channelInactive(ctx);
        ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.trace("exceptionCaught: ", cause);
        ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        logger.trace("channelRead0: {}", ctx);
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        int port = sa.getPort();
        TcpServer.Descriptor descriptor = tcpServer.getDescriptorByPort(port);
        if (descriptor != null) {
            final byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);

            final Channel tunnelChannel = descriptor.tunnelSessions().tunnelChannel();
            final long tunnelToken = descriptor.tunnelSessions().tunnelToken();
            final Long sessionToken = ctx.channel().attr(AttributeKeys.SESSION_TOKEN).get();
            if (sessionToken != null) {
                tunnelChannel.writeAndFlush(
                    new ProtoMessage(
                        ProtoMessage.Type.TRANSFER,
                        Unpooled.copyLong(tunnelToken, sessionToken).array(),
                        data
                    )
                );
            }
        }
    }

}
