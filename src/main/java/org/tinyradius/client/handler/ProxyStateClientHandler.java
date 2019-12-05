package org.tinyradius.client.handler;

import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.packet.PacketEncoder;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.packet.RadiusPackets;
import org.tinyradius.util.RadiusEndpoint;
import org.tinyradius.util.RadiusException;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.tinyradius.attribute.Attributes.createAttribute;

/**
 * ClientHandler that matches requests/response by appending Proxy-State attribute to
 * outbound packets. This avoids problem with mismatched requests/responses when using
 * packetIdentifier, which is limited to 256 unique IDs.
 */
public class ProxyStateClientHandler extends ClientHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProxyStateClientHandler.class);

    private static final int PROXY_STATE = 33;

    private final AtomicInteger proxyIndex = new AtomicInteger(1);
    private final PacketEncoder packetEncoder;

    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    /**
     * @param packetEncoder to decode packet incoming DatagramPackets to RadiusPackets
     */
    public ProxyStateClientHandler(PacketEncoder packetEncoder) {
        this.packetEncoder = packetEncoder;
    }

    private String nextProxyStateId() {
        return Integer.toString(proxyIndex.getAndIncrement());
    }

    @Override
    public DatagramPacket prepareDatagram(RadiusPacket original, RadiusEndpoint endpoint, InetSocketAddress sender, Promise<RadiusPacket> promise) throws RadiusException {
        final RadiusPacket radiusPacket = RadiusPackets.create(
                original.getDictionary(), original.getType(), original.getIdentifier(), original.getAttributes());

        final String requestId = nextProxyStateId();
        radiusPacket.addAttribute(createAttribute(original.getDictionary(), -1, PROXY_STATE, requestId.getBytes(UTF_8)));
        final RadiusPacket encodedRequest = radiusPacket.encodeRequest(endpoint.getSharedSecret());

        requests.put(requestId, new Request(endpoint, encodedRequest.getAuthenticator(), encodedRequest.getIdentifier(), promise));

        promise.addListener(f -> requests.remove(requestId));

        return packetEncoder.toDatagram(encodedRequest, endpoint.getAddress(), sender);
    }

    @Override
    protected void handleResponse(DatagramPacket datagramPacket) throws RadiusException {
        final InetSocketAddress sender = datagramPacket.sender();

        if (sender == null)
            throw new RadiusException("Ignoring response - sender is null (local address " + datagramPacket.recipient() + ")");

        RadiusPacket response = packetEncoder.fromDatagram(datagramPacket);

        // retrieve my Proxy-State attribute (the last)
        List<RadiusAttribute> proxyStates = response.getAttributes(PROXY_STATE);
        if (proxyStates.isEmpty())
            throw new RadiusException("Ignoring response - no Proxy-State attribute");

        RadiusAttribute proxyState = proxyStates.get(proxyStates.size() - 1);
        String proxyStateId = new String(proxyState.getValue(), UTF_8);

        final Request request = requests.get(proxyStateId);

        if (request == null)
            throw new RadiusException("Ignoring response - request context not found");

        if (response.getIdentifier() != request.identifier)
            throw new RadiusException("Ignoring response - identifier mismatch, request ID " + request.identifier +
                    ", response ID " + response.getIdentifier());

        if (!sender.equals(request.endpoint.getAddress()))
            throw new RadiusException("Ignoring response - sender address " + sender +
                    " does not match recipient address for corresponding request.");

        response.verify(request.endpoint.getSharedSecret(), request.authenticator);

        response.removeLastAttribute(PROXY_STATE);

        logger.info("Found request for response identifier => {}", response.getIdentifier());
        request.promise.trySuccess(response);
    }

    private static class Request {

        private final RadiusEndpoint endpoint;
        private final byte[] authenticator;
        private final int identifier;
        private final Promise<RadiusPacket> promise;

        Request(RadiusEndpoint endpoint, byte[] authenticator, int identifier, Promise<RadiusPacket> promise) {
            this.endpoint = endpoint;
            this.authenticator = authenticator;
            this.identifier = identifier;
            this.promise = promise;
        }
    }
}
