package org.tinyradius.server;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.util.Lifecycle;

import java.net.InetSocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * Implements a simple Radius server.
 */
public class RadiusServer implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(RadiusServer.class);

    private final ChannelFactory<? extends DatagramChannel> factory;
    protected final EventLoopGroup eventLoopGroup;
    private final ChannelHandler authHandler;
    private final ChannelHandler acctHandler;
    private final InetSocketAddress authSocket;
    private final InetSocketAddress acctSocket;

    private DatagramChannel authChannel = null;
    private DatagramChannel acctChannel = null;

    private Future<Void> serverStatus = null;

    /**
     * @param eventLoopGroup for both channel IO and processing
     * @param factory        to create new Channel
     * @param authHandler    ChannelHandler to handle requests received on authSocket
     * @param acctHandler    ChannelHandler to handle requests received on acctSocket
     * @param authSocket     socket to listen on for auth requests
     * @param acctSocket     socket to listen on for accounting requests
     */
    public RadiusServer(EventLoopGroup eventLoopGroup,
                        ChannelFactory<? extends DatagramChannel> factory,
                        ChannelHandler authHandler,
                        ChannelHandler acctHandler,
                        InetSocketAddress authSocket,
                        InetSocketAddress acctSocket) {
        this.eventLoopGroup = requireNonNull(eventLoopGroup, "eventLoopGroup cannot be null");
        this.factory = requireNonNull(factory, "factory cannot be null");
        this.authHandler = authHandler;
        this.acctHandler = acctHandler;
        this.authSocket = authSocket;
        this.acctSocket = acctSocket;
    }

    @Override
    public Future<Void> start() {
        if (this.serverStatus != null)
            return this.serverStatus;

        final Promise<Void> status = eventLoopGroup.next().newPromise();

        final PromiseCombiner promiseCombiner = new PromiseCombiner(ImmediateEventExecutor.INSTANCE);

        // todo error handling/timeout?
        promiseCombiner.addAll(listenAuth(), listenAcct());
        promiseCombiner.finish(status);

        this.serverStatus = status;
        return status;
    }

    @Override
    public Future<Void> stop() {
        logger.info("stopping Radius server");

        final Promise<Void> promise = eventLoopGroup.next().newPromise();
        final PromiseCombiner promiseCombiner = new PromiseCombiner(ImmediateEventExecutor.INSTANCE);

        if (authChannel != null)
            promiseCombiner.add(authChannel.close());

        if (acctChannel != null)
            promiseCombiner.add(acctChannel.close());

        promiseCombiner.finish(promise);
        return promise;
    }

    private ChannelFuture listenAuth() {
        logger.info("starting RadiusAuthListener on port " + authSocket.getPort());
        getAuthChannel().pipeline().addLast(authHandler);
        return listen(getAuthChannel(), authSocket);
    }

    private ChannelFuture listenAcct() {
        logger.info("starting RadiusAcctListener on port " + acctSocket.getPort());
        getAcctChannel().pipeline().addLast(acctHandler);
        return listen(getAcctChannel(), acctSocket);
    }

    /**
     * @param channel       to listen on
     * @param listenAddress the address to bind to
     * @return channelFuture of started channel socket
     */
    protected ChannelFuture listen(final DatagramChannel channel, final InetSocketAddress listenAddress) {
        requireNonNull(channel, "channel cannot be null");
        requireNonNull(listenAddress, "listenAddress cannot be null");

        final ChannelPromise promise = channel.newPromise();

        eventLoopGroup.register(channel)
                .addListener(f -> channel.bind(listenAddress)
                        .addListener(g -> promise.trySuccess()));

        return promise;
    }

    public DatagramChannel getAuthChannel() {
        if (authChannel == null)
            authChannel = factory.newChannel();
        return authChannel;
    }

    public DatagramChannel getAcctChannel() {
        if (acctChannel == null)
            acctChannel = factory.newChannel();
        return acctChannel;
    }

}