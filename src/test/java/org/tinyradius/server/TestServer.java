package org.tinyradius.server;

import io.netty.channel.Channel;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.PacketType;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.SecretProvider;

import java.net.InetSocketAddress;

/**
 * Test server which terminates after 30 s.
 * Knows only the client "localhost" with secret "testing123" and
 * the user "mw" with the password "test".
 * <p>
 * TestServer can answer both to Access-Request and Access-Response
 * packets with Access-Accept/Reject or Accounting-Response, respectively.
 */
public class TestServer {

    public static void main(String[] args) throws Exception {

        final DefaultDictionary dictionary = DefaultDictionary.INSTANCE;

        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

        final Timer timer = new HashedWheelTimer();

        final SecretProvider secretProvider = remote ->
                remote.getAddress().getHostAddress().equals("127.0.0.1") ? "testing123" : null;

        final RequestHandler<AccessRequest> authHandler = new DeduplicatorHandler<>(new AuthHandler() {
            @Override
            public String getUserPassword(String userName) {
                return userName.equals("test") ? "password" : null;
            }

            // Adds an attribute to the Access-Accept packet
            @Override
            public Promise<RadiusPacket> handlePacket(Dictionary dictionary, Channel channel, AccessRequest accessRequest, InetSocketAddress remoteAddress, String sharedSecret) {
                System.out.println("Received Access-Request:\n" + accessRequest);
                final Promise<RadiusPacket> promise = channel.eventLoop().newPromise();
                super.handlePacket(dictionary, channel, accessRequest, remoteAddress, sharedSecret).addListener((Future<RadiusPacket> f) -> {
                    final RadiusPacket packet = f.getNow();
                    if (packet == null) {
                        System.out.println("Ignore packet.");
                        promise.tryFailure(f.cause());
                    } else {
                        if (packet.getPacketType() == PacketType.ACCESS_ACCEPT)
                            packet.addAttribute("Reply-Message", "Welcome " + accessRequest.getUserName() + "!");
                        System.out.println("Answer:\n" + packet);
                        promise.trySuccess(packet);
                    }
                });

                return promise;
            }
        }, timer, 30000);

        RequestHandler<AccountingRequest> acctHandler = new DeduplicatorHandler<>(new AcctHandler(), timer, 30000);

        final RadiusServer<NioDatagramChannel> server = new RadiusServer<>(
                eventLoopGroup,
                new ReflectiveChannelFactory<>(NioDatagramChannel.class),
                null,
                new ChannelInboundHandler<>(dictionary, authHandler, timer, secretProvider, AccessRequest.class),
                new ChannelInboundHandler<>(dictionary, acctHandler, timer, secretProvider, AccountingRequest.class),
                11812, 11813);

        final Future<Void> future = server.start();
        future.addListener(future1 -> {
            if (future1.isSuccess()) {
                System.out.println("Server started");
            } else {
                System.out.println("Failed to start server: " + future1.cause());
                server.stop();
                eventLoopGroup.shutdownGracefully();
            }
        });

        System.in.read();

        server.stop();

        eventLoopGroup.shutdownGracefully()
                .awaitUninterruptibly();
    }

}
