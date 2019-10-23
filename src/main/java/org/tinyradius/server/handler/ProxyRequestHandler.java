package org.tinyradius.server.handler;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.client.RadiusClient;
import org.tinyradius.client.handler.ProxyStateClientHandler;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusEndpoint;
import org.tinyradius.util.RadiusException;
import org.tinyradius.server.SecretProvider;

import java.net.InetSocketAddress;

/**
 * RadiusServer handler that proxies packets to destination.
 * <p>
 * Depends on RadiusClient to proxy packets.
 * <p>
 * RadiusClient port should be set to proxy port, which will be used to communicate
 * with upstream servers. RadiusClient should also use a variant of {@link ProxyStateClientHandler}
 * which matches requests/responses by adding a custom Proxy-State attribute.
 * <p>
 * This implementation expects {@link #getProxyServer(RadiusPacket, RadiusEndpoint)} to lookup
 * endpoint to forward requests to.
 */
public abstract class ProxyRequestHandler implements RequestHandler<RadiusPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ProxyRequestHandler.class);

    private final RadiusClient radiusClient;

    protected ProxyRequestHandler(RadiusClient radiusClient) {
        this.radiusClient = radiusClient;
    }

    /**
     * This method must be implemented to return a RadiusEndpoint
     * if the given packet is to be proxied. The endpoint represents the
     * Radius server the packet should be proxied to.
     *
     * @param packet the packet in question
     * @param client the client endpoint the packet originated from
     *               (containing the address, port number and shared secret)
     * @return a RadiusEndpoint or null if the packet should not be
     * proxied
     */
    public abstract RadiusEndpoint getProxyServer(RadiusPacket packet, RadiusEndpoint client);

    /**
     * Proxies the given packet to the server given in the proxy connection.
     * Stores the proxy connection object in the cache with a key that
     * is added to the packet in the "Proxy-State" attribute.
     */
    @Override
    public Promise<RadiusPacket> handlePacket(Channel channel, RadiusPacket request, InetSocketAddress remoteAddress, SecretProvider secretProvider) {
        Promise<RadiusPacket> promise = channel.eventLoop().newPromise();

        RadiusEndpoint clientEndpoint = new RadiusEndpoint(remoteAddress, secretProvider.getSharedSecret(remoteAddress));
        RadiusEndpoint serverEndpoint = getProxyServer(request, clientEndpoint);

        if (serverEndpoint == null) {
            logger.info("Server not found for client proxy request, ignoring");
            promise.tryFailure(new RadiusException("Server not found for client proxy request"));
            return promise;
        }

        logger.info("Proxy packet to " + serverEndpoint.getAddress());

        radiusClient.communicate(request, serverEndpoint)
                .addListener((Future<RadiusPacket> f) -> {
                    if (f.isSuccess())
                        promise.trySuccess(handleServerResponse(request.getDictionary(), f.getNow()));
                    else
                        promise.tryFailure(f.cause());
                });

        return promise;
    }

    /**
     * Sends an answer to a proxied packet back to the original host.
     * Retrieves the RadiusProxyConnection object from the cache employing
     * the Proxy-State attribute.
     *
     * @param dictionary dictionary for new packet to use
     * @param packet     response received from server
     * @return packet to send back to client
     */
    protected RadiusPacket handleServerResponse(Dictionary dictionary, RadiusPacket packet) {
        return new RadiusPacket(dictionary, packet.getType(), packet.getIdentifier(), packet.getAttributes());
    }
}
