package org.tinyradius.io;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.core.dictionary.DefaultDictionary;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.packet.request.AccountingRequest;
import org.tinyradius.core.packet.request.RadiusRequest;
import org.tinyradius.io.client.RadiusClient;
import org.tinyradius.io.client.handler.ClientDatagramCodec;
import org.tinyradius.io.client.handler.PromiseAdapter;
import org.tinyradius.io.client.timeout.FixedTimeoutHandler;
import org.tinyradius.io.server.RadiusServer;
import org.tinyradius.io.server.SecretProvider;
import org.tinyradius.io.server.handler.ProxyHandler;
import org.tinyradius.io.server.handler.ServerPacketCodec;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * TestProxy shows how to implement a proxy radius server. You can use
 * this class together with TestClient and TestServer.
 * <p>
 * Listens on localhost:1812 and localhost:1813. Proxies every access request
 * to localhost:10000 and every accounting request to localhost:10001.
 * You can use TestClient to ask this TestProxy and TestServer
 * with the parameters 10000 and 10001 as the target server.
 * Uses "testing123" as the shared secret for the communication with the
 * target server (localhost:10000/localhost:10001) and "proxytest" as the
 * shared secret for the communication with connecting clients.
 */
public class TestProxy {

    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws Exception {

        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
        final Dictionary dictionary = DefaultDictionary.INSTANCE;

        final Timer timer = new HashedWheelTimer();
        final Bootstrap bootstrap = new Bootstrap().channel(NioDatagramChannel.class).group(eventLoopGroup);

        final SecretProvider secretProvider = remote -> {
            if (remote.getPort() == 11812 || remote.getPort() == 11813)
                return "testing123";

            return remote.getAddress().getHostAddress().equals("127.0.0.1") ?
                    "proxytest" : null;
        };

        final FixedTimeoutHandler retryStrategy = new FixedTimeoutHandler(timer, 3, 1000);

        try (RadiusClient radiusClient = new RadiusClient(
                bootstrap, new InetSocketAddress(0), retryStrategy, new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) {
                ch.pipeline().addLast(new ClientDatagramCodec(dictionary), new PromiseAdapter());
            }
        })) {
            final ChannelInitializer<DatagramChannel> channelInitializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(DatagramChannel ch) {
                    ch.pipeline().addLast(new ServerPacketCodec(dictionary, secretProvider), new ProxyHandler(radiusClient) {
                        @Override
                        public Optional<RadiusEndpoint> getProxyServer(RadiusRequest request, RadiusEndpoint client) {
                            try {
                                InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
                                int port = request instanceof AccountingRequest ? 11813 : 11812;
                                return Optional.of(new RadiusEndpoint(new InetSocketAddress(address, port), "testing123"));
                            } catch (UnknownHostException e) {
                                return Optional.empty();
                            }
                        }
                    });
                }
            };

            try (RadiusServer proxy = new RadiusServer(bootstrap,
                    channelInitializer, channelInitializer,
                    new InetSocketAddress(1812), new InetSocketAddress(1813))) {

                proxy.isReady().addListener(future1 -> {
                    if (future1.isSuccess()) {
                        logger.info("Server started");
                    } else {
                        logger.info("Failed to start server", future1.cause());
                        proxy.close();
                        eventLoopGroup.shutdownGracefully();
                    }
                });

                System.in.read();
            }
        }

        eventLoopGroup.shutdownGracefully();
    }
}
