package org.tinyradius.proxy.handler;

import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.HashedWheelTimer;
import org.junit.jupiter.api.Test;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.client.RadiusClient;
import org.tinyradius.client.handler.SimpleClientHandler;
import org.tinyradius.client.retry.SimpleRetryStrategy;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.server.handler.AcctHandler;
import org.tinyradius.util.RadiusEndpoint;
import org.tinyradius.util.RadiusException;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.tinyradius.attribute.Attributes.createAttribute;
import static org.tinyradius.packet.PacketType.*;

public class ProxyRequestHandlerTest {
    private final Dictionary dictionary = DefaultDictionary.INSTANCE;
    private final SecureRandom random = new SecureRandom();

    private final NioEventLoopGroup eventExecutors = new NioEventLoopGroup(4);
    private final HashedWheelTimer timer = new HashedWheelTimer();
    private final NioDatagramChannel datagramChannel = new NioDatagramChannel();

    private final RadiusClient<NioDatagramChannel> client = new RadiusClient<>(eventExecutors,
            new ReflectiveChannelFactory<>(NioDatagramChannel.class),
            new SimpleClientHandler(dictionary),
            new SimpleRetryStrategy(timer, 3, 1000),
            null, 0);

    @Test
    void handleServerResponse() {
        final int id = random.nextInt(256);
        eventExecutors.register(datagramChannel).syncUninterruptibly();

        ProxyRequestHandler proxyRequestHandler = new ProxyRequestHandler(client) {
            @Override
            public RadiusEndpoint getProxyServer(RadiusPacket packet, RadiusEndpoint client) {
                return new RadiusEndpoint(new InetSocketAddress(0), "shared");
            }
        };

        final AccountingRequest packet = new AccountingRequest(dictionary, id, null, Arrays.asList(
                createAttribute(dictionary, -1, 33, "state1".getBytes(UTF_8)),
                createAttribute(dictionary, -1, 33, "state2".getBytes(UTF_8))));

        assertEquals(ACCOUNTING_REQUEST, packet.getType());
        assertEquals(Arrays.asList("state1", "state2"), packet.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

        RadiusPacket response = new AcctHandler().handlePacket(datagramChannel, packet, null, "")
                .syncUninterruptibly().getNow();

        proxyRequestHandler.handleServerResponse(dictionary, response);

        assertEquals(id, response.getIdentifier());
        assertEquals(ACCOUNTING_RESPONSE, response.getType());
        assertEquals(Arrays.asList("state1", "state2"), response.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

        eventExecutors.shutdownGracefully();
    }

    @Test
    void handlePacketWithTimeout() {
        final int id = random.nextInt(256);
        eventExecutors.register(datagramChannel).syncUninterruptibly();

        ProxyRequestHandler proxyRequestHandler = new ProxyRequestHandler(client) {
            @Override
            public RadiusEndpoint getProxyServer(RadiusPacket packet, RadiusEndpoint client) {
                return new RadiusEndpoint(new InetSocketAddress(0), "shared");
            }
        };

        final AccessRequest packet = new AccessRequest(dictionary, id, null, "user", "user-pw");
        assertEquals(ACCESS_REQUEST, packet.getType());
        assertEquals("user", packet.getUserName());

        final RadiusException radiusException = assertThrows(RadiusException.class,
                () -> proxyRequestHandler.handlePacket(datagramChannel, packet, new InetSocketAddress(0), "shared").syncUninterruptibly().getNow());

        assertTrue(radiusException.getMessage().toLowerCase().contains("max retries"));
    }

    @Test
    void handlePacketNullServerEndPoint() {
        final int id = random.nextInt(256);
        eventExecutors.register(datagramChannel).syncUninterruptibly();

        ProxyRequestHandler proxyRequestHandler = new ProxyRequestHandler(client) {
            @Override
            public RadiusEndpoint getProxyServer(RadiusPacket packet, RadiusEndpoint client) {
                return null;
            }
        };

        final AccountingRequest packet = new AccountingRequest(dictionary, id, null, Arrays.asList(
                createAttribute(dictionary, -1, 33, "state1".getBytes(UTF_8)),
                createAttribute(dictionary, -1, 33, "state2".getBytes(UTF_8))));

        assertEquals(ACCOUNTING_REQUEST, packet.getType());
        assertEquals(Arrays.asList("state1", "state2"), packet.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));


        final RadiusException radiusException = assertThrows(RadiusException.class,
                () -> proxyRequestHandler.handlePacket(datagramChannel, packet, new InetSocketAddress(0), "shared").syncUninterruptibly());

        assertTrue(radiusException.getMessage().toLowerCase().contains("server not found"));
        eventExecutors.shutdownGracefully();
    }
}
