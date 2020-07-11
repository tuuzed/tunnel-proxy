@file:JvmName("-ConstsKt")

package lighttunnel.client.util

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import lighttunnel.client.TunnelClientChannelHandler
import lighttunnel.client.conn.TunnelConnection
import lighttunnel.proto.TunnelRequest

internal val AK_TUNNEL_ID: AttributeKey<Long?> = AttributeKey.newInstance("\$lighttunnel.client.TunnelId")

internal val AK_SESSION_ID: AttributeKey<Long?> = AttributeKey.newInstance("\$lighttunnel.client.SessionId")

internal val AK_NEXT_CHANNEL: AttributeKey<Channel?> = AttributeKey.newInstance("\$lighttunnel.client.NextChannel")

internal val AK_TUNNEL_REQUEST: AttributeKey<TunnelRequest?> = AttributeKey.newInstance("\$lighttunnel.client.TunnelRequest")

internal val AK_TUNNEL_CONNECTION: AttributeKey<TunnelConnection?> = AttributeKey.newInstance("\$lighttunnel.client.TunnelConnection")

internal val AK_CHANNEL_INACTIVE_EXTRA: AttributeKey<TunnelClientChannelHandler.ChannelInactiveExtra?> = AttributeKey.newInstance("\$lighttunnel.client.ChannelInactiveExtra")
