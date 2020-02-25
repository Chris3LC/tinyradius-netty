package org.tinyradius.client.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.client.PendingRequestCtx;
import org.tinyradius.packet.PacketCodec;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusPacketException;

import java.net.InetSocketAddress;
import java.util.List;

@ChannelHandler.Sharable
public class ClientPacketCodec extends MessageToMessageCodec<DatagramPacket, PendingRequestCtx> {

    private static final Logger logger = LogManager.getLogger();

    private final PacketCodec packetCodec;

    public ClientPacketCodec(PacketCodec packetCodec) {
        this.packetCodec = packetCodec;
    }

    protected DatagramPacket encodePacket(InetSocketAddress localAddress, PendingRequestCtx msg) {
        try {
            final RadiusPacket packet = msg.getRequest().encodeRequest(msg.getEndpoint().getSecret());
            final DatagramPacket datagramPacket = packetCodec.toDatagram(
                    packet, msg.getEndpoint().getAddress(), localAddress);
            logger.debug("Sending request to {}", msg.getEndpoint().getAddress());
            return datagramPacket;
        } catch (RadiusPacketException e) {
            logger.warn("Could not encode Radius packet: {}", e.getMessage());
            msg.getResponse().tryFailure(e);
            return null;
        }
    }

    protected RadiusPacket decodePacket(DatagramPacket msg) {
        InetSocketAddress remoteAddress = msg.sender();

        if (remoteAddress == null) {
            logger.warn("Ignoring request, remoteAddress is null");
            return null;
        }

        try {
            // can't verify until we know corresponding request auth
            RadiusPacket packet = packetCodec.fromDatagram(msg);
            logger.debug("Received packet from {} - {}", remoteAddress, packet);
            return packet;
        } catch (RadiusPacketException e) {
            logger.warn("Could not decode Radius packet: {}", e.getMessage());
            return null;
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, PendingRequestCtx msg, List<Object> out) {
        final DatagramPacket datagramPacket = encodePacket((InetSocketAddress) ctx.channel().localAddress(), msg);
        if (datagramPacket != null)
            out.add(datagramPacket);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
        final RadiusPacket radiusPacket = decodePacket(msg);
        if (radiusPacket != null)
            out.add(radiusPacket);
    }
}
