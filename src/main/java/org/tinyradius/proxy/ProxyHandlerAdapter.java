package org.tinyradius.proxy;

import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.proxy.handler.LifecycleRequestHandler;
import org.tinyradius.server.HandlerAdapter;
import org.tinyradius.util.Lifecycle;
import org.tinyradius.util.SecretProvider;

/**
 * ChannelInboundHandler that allows any RadiusPacket type and implements Lifecycle.
 */
public class ProxyHandlerAdapter extends HandlerAdapter<RadiusPacket> implements Lifecycle {

    private final Lifecycle requestHandler;

    /**
     * @param dictionary     for encoding/decoding RadiusPackets
     * @param requestHandler ProxyRequestHandler to handle requests. Should also implement {@link Lifecycle}
     *                       as the handler is expected to manage the socket for proxying.
     * @param timer          handle timeouts if requests take too long to be processed
     * @param secretProvider lookup sharedSecret given remote address
     */
    public ProxyHandlerAdapter(
            Dictionary dictionary,
            LifecycleRequestHandler<RadiusPacket> requestHandler,
            Timer timer,
            SecretProvider secretProvider) {
        super(dictionary, requestHandler, timer, secretProvider, RadiusPacket.class);
        this.requestHandler = requestHandler;
    }

    @Override
    public Future<Void> start() {
        return requestHandler.start();
    }

    @Override
    public Future<Void> stop() {
        return requestHandler.stop();
    }
}
