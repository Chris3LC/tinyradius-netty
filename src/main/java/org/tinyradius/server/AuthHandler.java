package org.tinyradius.server;

import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.SecretProvider;

import java.net.InetSocketAddress;

import static org.tinyradius.packet.RadiusPacket.ACCESS_ACCEPT;
import static org.tinyradius.packet.RadiusPacket.ACCESS_REJECT;

/**
 * Reference implementation of AccessRequest handler that returns Access-Accept/Reject
 * depending on whether {@link #getUserPassword(String)} matches password in Access-Request.
 */
public abstract class AuthHandler extends ServerHandler<AccessRequest> {

    public AuthHandler(Dictionary dictionary, Deduplicator deduplicator, Timer timer, SecretProvider secretProvider) {
        super(dictionary, deduplicator, timer, secretProvider, AccessRequest.class);
    }

    /**
     * Returns the password of the passed user. Either this
     * method or {@link #handlePacket(Channel, AccessRequest, InetSocketAddress, String)} (EventExecutor, AccessRequest)} should be overridden.
     *
     * @param userName user name
     * @return plain-text password or null if user unknown
     */
    public abstract String getUserPassword(String userName);

    /**
     * Constructs an answer for an Access-Request packet. This
     * method should be overridden.
     *
     * @param accessRequest Radius clientRequest packet
     * @return clientResponse packet or null if no packet shall be sent
     */
    @Override
    protected Promise<RadiusPacket> handlePacket(Channel channel, AccessRequest accessRequest, InetSocketAddress remoteAddress, String sharedSecret) {
        Promise<RadiusPacket> promise = channel.eventLoop().newPromise();
        try {
            String password = getUserPassword(accessRequest.getUserName());
            int type = password != null && accessRequest.verifyPassword(password) ?
                    ACCESS_ACCEPT : ACCESS_REJECT;

            RadiusPacket answer = new RadiusPacket(type, accessRequest.getPacketIdentifier());
            answer.setDictionary(dictionary);
            accessRequest.getAttributes(33)
                    .forEach(answer::addAttribute);

            promise.trySuccess(answer);
        } catch (Exception e) {
            promise.tryFailure(e);
        }
        return promise;
    }
}
