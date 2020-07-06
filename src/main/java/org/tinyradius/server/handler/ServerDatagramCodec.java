package org.tinyradius.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.request.RadiusRequest;
import org.tinyradius.server.RequestCtx;
import org.tinyradius.server.ResponseCtx;
import org.tinyradius.server.SecretProvider;
import org.tinyradius.util.RadiusEndpoint;
import org.tinyradius.util.RadiusPacketException;

import java.net.InetSocketAddress;
import java.util.List;

import static org.tinyradius.packet.request.RadiusRequest.fromDatagram;

/**
 * Datagram codec for receiving requests and sending responses
 */
@ChannelHandler.Sharable
public class ServerDatagramCodec extends MessageToMessageCodec<DatagramPacket, ResponseCtx> {

    private static final Logger logger = LogManager.getLogger();

    private final Dictionary dictionary;
    private final SecretProvider secretProvider;

    public ServerDatagramCodec(Dictionary dictionary, SecretProvider secretProvider) {
        this.dictionary = dictionary;
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
            final RadiusRequest request = fromDatagram(dictionary, msg);
            logger.debug("Received request from {} - {}", remoteAddress, request);
            // log first before errors may be thrown

            return new RequestCtx(request.decodeRequest(secret), new RadiusEndpoint(remoteAddress, secret));
        } catch (RadiusPacketException e) {
            logger.warn("Could not decode Radius packet: {}", e.getMessage());
            return null;
        }
    }

    protected DatagramPacket encodePacket(InetSocketAddress localAddress, ResponseCtx msg) {
        try {
            final DatagramPacket datagramPacket = msg
                    .getResponse()
                    .encodeResponse(msg.getEndpoint().getSecret(), msg.getRequest().getAuthenticator())
                    .toDatagram(msg.getEndpoint().getAddress(), localAddress);
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