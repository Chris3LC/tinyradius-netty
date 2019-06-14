package com.globalreachtech.tinyradius.grt;

import com.globalreachtech.tinyradius.dictionary.Dictionary;
import com.globalreachtech.tinyradius.netty.RadiusClient;
import com.globalreachtech.tinyradius.packet.RadiusPacket;
import com.globalreachtech.tinyradius.util.RadiusException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Timer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class GrtClientPacketManager implements RadiusClient.PacketManager {

    private static Log logger = LogFactory.getLog(GrtClientPacketManager.class);


    private RadiusQueue<RequestContext> queue = new RadiusQueue<>();

    private Dictionary dictionary;

    public GrtClientPacketManager(Dictionary dictionary, Timer timer) {
        this.dictionary = dictionary;
    }

    @Override
    public void processInbound(DatagramPacket packet) {
        RequestContext context = lookup(packet);
        if (context == null) {
            logger.info("Request context not found for received packet, ignoring...");
        } else {
            context.calculateResponseTime();

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Received clientResponse in %d.%dms: %s\nFor clientRequest: %s",
                        context.responseTime() / 1000000,
                        context.responseTime() % 1000000 / 10000,
                        context.clientResponse().toString(),
                        context.clientRequest().toString()));
            }

            dequeue(context);
        }
    }


    private RequestContext lookup(DatagramPacket response) {
        requireNonNull(response, "clientResponse cannot be null");

        ByteBuf buf = response.content().duplicate().skipBytes(1);

        int identifier = buf.readByte() & 0xff;

        for (RequestContext context : queue.get(identifier)) {
            if (identifier != context.clientRequest().getPacketIdentifier())
                continue;
            if (!(response.sender().equals(
                    context.endpoint().getEndpointAddress())))
                continue;
            try {
                RadiusPacket resp = RadiusPacket.decodeResponsePacket(dictionary,
                        new ByteBufInputStream(response.content().duplicate()),
                        context.endpoint().getSharedSecret(), context.clientRequest());

                if (logger.isInfoEnabled())
                    logger.info(String.format("Found context %d for clientResponse identifier => %d",
                            context.identifier(), resp.getPacketIdentifier()));

                context.setClientResponse(resp);

                return context;

            } catch (IOException | RadiusException e) {
            }
        }

        return null;
    }
}
