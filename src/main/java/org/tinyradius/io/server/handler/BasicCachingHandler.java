package org.tinyradius.io.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.io.server.RequestCtx;
import org.tinyradius.io.server.ResponseCtx;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Simple caching handler backed by ConcurrentHashMap, invalidates using {@link Timer}.
 */
public class BasicCachingHandler extends MessageToMessageCodec<RequestCtx, ResponseCtx> {

    private static final Logger logger = LogManager.getLogger();

    private final Timer timer;
    private final int ttlMs;

    private final Map<Packet, ResponseCtx> requests = new ConcurrentHashMap<>();

    /**
     * @param timer for cache eviction
     * @param ttlMs time for items to stay cached after being returned, in milliseconds
     */
    public BasicCachingHandler(Timer timer, int ttlMs) {
        this.timer = timer;
        this.ttlMs = ttlMs;
    }

    /**
     * @param ctx        ChannelHandlerContext
     * @param requestCtx inbound request context
     * @param out        list to which decoded messages should be added
     */
    protected void onMiss(ChannelHandlerContext ctx, RequestCtx requestCtx, List<Object> out) {
        out.add(requestCtx);
    }

    /**
     * @param ctx         ChannelHandlerContext
     * @param requestCtx  inbound request context
     * @param responseCtx outbound response context
     * @param out         list to which decoded messages should be added
     */
    protected void onHit(ChannelHandlerContext ctx, RequestCtx requestCtx, ResponseCtx responseCtx, List<Object> out) {
        ctx.writeAndFlush(responseCtx);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, RequestCtx requestCtx, List<Object> out) {
        final Packet packet = Packet.from(requestCtx);
        final ResponseCtx responseContext = requests.get(packet);

        if (responseContext != null) {
            logger.debug("Cache hit, resending response, id: {}, remote address: {}", packet.id, packet.remoteAddress);
            onHit(ctx, requestCtx, responseContext, out);
        } else {
            logger.debug("Cache miss, proxying request, id: {}, remote address: {}", packet.id, packet.remoteAddress);
            onMiss(ctx, requestCtx, out);
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseCtx msg, List<Object> out) {
        final Packet packet = Packet.from(msg);
        requests.put(packet, msg);
        timer.newTimeout(t -> requests.remove(packet), ttlMs, MILLISECONDS);
        out.add(msg);
    }

    private static class Packet {

        private final int id;
        private final InetSocketAddress remoteAddress;
        private final byte[] authenticator;

        private Packet(int id, InetSocketAddress remoteAddress, byte[] authenticator) {
            this.id = id;
            this.remoteAddress = remoteAddress;
            this.authenticator = authenticator;
        }

        private static Packet from(RequestCtx ctx) {
            return new Packet(ctx.getRequest().getId(), ctx.getEndpoint().getAddress(), ctx.getRequest().getAuthenticator());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Packet packet = (Packet) o;
            return id == packet.id &&
                    Objects.equals(remoteAddress, packet.remoteAddress) &&
                    Arrays.equals(authenticator, packet.authenticator);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id, remoteAddress);
            result = 31 * result + Arrays.hashCode(authenticator);
            return result;
        }
    }
}
