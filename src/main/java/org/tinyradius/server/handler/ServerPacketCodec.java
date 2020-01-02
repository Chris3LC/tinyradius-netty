package org.tinyradius.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.packet.PacketEncoder;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.server.RequestCtx;
import org.tinyradius.server.ResponseCtx;
import org.tinyradius.server.SecretProvider;
import org.tinyradius.util.RadiusEndpoint;
import org.tinyradius.util.RadiusPacketException;

import java.net.InetSocketAddress;
import java.util.List;

@ChannelHandler.Sharable
public class ServerPacketCodec extends MessageToMessageCodec<DatagramPacket, ResponseCtx> {

    private static final Logger logger = LoggerFactory.getLogger(ServerPacketCodec.class);

    private final PacketEncoder packetEncoder;
    private final SecretProvider secretProvider;

    public ServerPacketCodec(PacketEncoder packetEncoder, SecretProvider secretProvider) {
        this.packetEncoder = packetEncoder;
        this.secretProvider = secretProvider;
    }

    protected RequestCtx decodePacket(DatagramPacket msg) {
        final InetSocketAddress remoteAddress = msg.sender();

        String secret = secretProvider.getSharedSecret(remoteAddress);
        if (secret == null) {
            logger.warn("Ignoring request from {}, shared secret lookup failed", remoteAddress);
            return null;
        }

        try {
            RadiusPacket packet = packetEncoder.fromDatagram(msg, secret);
            logger.debug("Received packet from {} - {}", remoteAddress, packet);

            return new RequestCtx(packet, new RadiusEndpoint(remoteAddress, secret));
        } catch (RadiusPacketException e) {
            logger.warn("Could not decode Radius packet: {}", e.getMessage());
            return null;
        }
    }

    protected DatagramPacket encodePacket(InetSocketAddress localAddress, ResponseCtx msg) {
        final RadiusPacket packet = msg.getResponse()
                .encodeResponse(msg.getEndpoint().getSecret(), msg.getRequest().getAuthenticator());
        try {
            final DatagramPacket datagramPacket = packetEncoder.toDatagram(
                    packet, msg.getEndpoint().getAddress(), localAddress);
            logger.debug("Sending response to {}", msg.getEndpoint().getAddress());
            return datagramPacket;
        } catch (RadiusPacketException e) {
            logger.warn("Could not encode Radius packet: {}", e.getMessage());
            return null;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
        final RequestCtx requestCtx = decodePacket(msg);
        if (requestCtx != null)
            out.add(requestCtx);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseCtx msg, List<Object> out) {
        final DatagramPacket datagramPacket = encodePacket((InetSocketAddress) ctx.channel().localAddress(), msg);
        if (datagramPacket != null)
            out.add(datagramPacket);
    }
}