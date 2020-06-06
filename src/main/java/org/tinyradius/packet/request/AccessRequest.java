package org.tinyradius.packet.request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.util.MessageAuthSupport;
import org.tinyradius.util.RadiusPacketException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * This class represents an Access-Request Radius packet.
 */
public interface AccessRequest<T extends AccessRequest<T>> extends RadiusRequest<T>, MessageAuthSupport<T> {

    Logger logger = LogManager.getLogger();

    SecureRandom RANDOM = new SecureRandom();

    byte USER_PASSWORD = 2;
    byte CHAP_PASSWORD = 3;
    byte EAP_MESSAGE = 79;
    Set<Byte> AUTH_ATTRS = new HashSet<>(Arrays.asList(USER_PASSWORD, CHAP_PASSWORD, EAP_MESSAGE));

    byte USER_NAME = 1;


    /**
     * Create copy of AccessRequest with new authenticator and encoded attributes
     *
     * @param sharedSecret shared secret that secures the communication
     *                     with the other Radius server/client
     * @param newAuth      authenticator to use to encode PAP password,
     *                     nullable if using different auth protocol
     * @return RadiusPacket with new authenticator and encoded attributes
     * @throws RadiusPacketException if invalid or missing attributes
     */
    T encodeAuthMechanism(String sharedSecret, byte[] newAuth) throws RadiusPacketException;

    /**
     * AccessRequest overrides this method to generate a randomized authenticator (RFC 2865)
     * and encode required attributes (e.g. User-Password).
     *
     * @param sharedSecret shared secret that secures the communication
     *                     with the other Radius server/client
     * @return RadiusPacket with new authenticator and encoded attributes
     */
    @Override
    default T encodeRequest(String sharedSecret) throws RadiusPacketException {
        if (sharedSecret == null || sharedSecret.isEmpty())
            throw new IllegalArgumentException("Shared secret cannot be null/empty");

        // create authenticator only if needed - maintain idempotence
        byte[] newAuth = getAuthenticator() == null ? random16bytes() : getAuthenticator();

        return encodeAuthMechanism(sharedSecret, newAuth)
                .encodeMessageAuth(sharedSecret, newAuth);
    }

    /**
     * Verify packet for specific auth protocols
     *
     * @param sharedSecret shared secret
     * @throws RadiusPacketException if invalid or missing attributes
     */
    T verifyAuthMechanism(String sharedSecret) throws RadiusPacketException;

    /**
     * AccessRequest cannot verify authenticator as they
     * contain random bytes.
     * <p>
     * It can, however, check the User-Password/Challenge attributes
     * are present and attempt decryption, depending on auth protocol.
     *
     * @param sharedSecret shared secret, only applicable for PAP
     * @return
     */
    @Override
    default AccessRequest<T> verifyRequest(String sharedSecret) throws RadiusPacketException {
        verifyMessageAuth(sharedSecret, getAuthenticator());
        verifyAuthMechanism(sharedSecret);
        return this;
    }

    /**
     * Create new AccessRequest, tries to identify auth protocol from attributes.
     *
     * @param dictionary    custom dictionary to use
     * @param identifier    packet identifier
     * @param authenticator authenticator for packet, nullable
     * @param attributes    list of attributes for packet, should not be empty
     *                      or a stub AccessRequest will be returned
     * @return AccessRequest auth mechanism-specific implementation
     */
    static AccessRequest<?> create(Dictionary dictionary, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        return lookupAuthType(attributes).newInstance(dictionary, identifier, authenticator, attributes);
    }

    static AccessRequestConstructor lookupAuthType(List<RadiusAttribute> attributes) {
        /*
         * An Access-Request that contains either a User-Password or
         * CHAP-Password or ARAP-Password or one or more EAP-Message attributes
         * MUST NOT contain more than one type of those four attributes.
         */
        final Set<Byte> detectedAuth = attributes.stream()
                .map(RadiusAttribute::getType)
                .filter(AUTH_ATTRS::contains)
                .collect(toSet());

        if (detectedAuth.isEmpty()) {
            logger.debug("AccessRequest no auth mechanism found, passing through");
            return AccessRequestNoAuth::new;
        }

        if (detectedAuth.size() > 1) {
            logger.warn("AccessRequest identified multiple auth mechanisms");
            return AccessRequestNoAuth::new;
        }

        switch (detectedAuth.iterator().next()) {
            case EAP_MESSAGE:
                return AccessRequestEap::new;
            case CHAP_PASSWORD:
                return AccessRequestChap::new;
            case USER_PASSWORD:
                return AccessRequestPap::new;
            default:
                return AccessRequestNoAuth::new;
        }
    }

    static byte[] random16bytes() {
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        return randomBytes;
    }

    interface AccessRequestConstructor {
        AccessRequest newInstance(Dictionary dictionary, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes);
    }

}
