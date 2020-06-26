package org.tinyradius.packet.request;

import org.tinyradius.attribute.type.RadiusAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.util.MessageAuthSupport;
import org.tinyradius.util.RadiusPacketException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.tinyradius.packet.util.PacketType.ACCESS_REQUEST;

/**
 * This class represents an Access-Request Radius packet.
 */
public abstract class AccessRequest extends GenericRequest implements MessageAuthSupport<RadiusRequest> {

    protected static final SecureRandom RANDOM = new SecureRandom();

    protected static final byte USER_PASSWORD = 2;
    protected static final byte CHAP_PASSWORD = 3;
    protected static final byte EAP_MESSAGE = 79;
    protected static final byte ARAP_PASSWORD = 70;
    private static final Set<Byte> AUTH_ATTRS = new HashSet<>(Arrays.asList(USER_PASSWORD, CHAP_PASSWORD, ARAP_PASSWORD, EAP_MESSAGE));

    protected AccessRequest(Dictionary dictionary, byte id, byte[] authenticator, List<RadiusAttribute> attributes) {
        super(dictionary, ACCESS_REQUEST, id, authenticator, attributes);
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
    static AccessRequest create(Dictionary dictionary, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
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
            logger.warn("AccessRequest no auth mechanism found, parsing as AccessRequestNoAuth");
            return AccessRequestNoAuth::new;
        }

        if (detectedAuth.size() > 1) {
            logger.warn("AccessRequest identified multiple auth mechanisms, parsing as AccessRequestNoAuth");
            return AccessRequestNoAuth::new;
        }

        switch (detectedAuth.iterator().next()) {
            case EAP_MESSAGE:
                logger.debug("Parsing AccessRequest as AccessRequestEap");
                return AccessRequestEap::new;
            case CHAP_PASSWORD:
                logger.debug("Parsing AccessRequest as AccessRequestChap");
                return AccessRequestChap::new;
            case USER_PASSWORD:
                logger.debug("Parsing AccessRequest as AccessRequestPap");
                return AccessRequestPap::new;
            case ARAP_PASSWORD:
                logger.debug("Parsing AccessRequest as AccessRequestArap");
                return AccessRequestArap::new;
            default:
                logger.debug("Parsing AccessRequest as AccessRequestNoAuth");
                return AccessRequestNoAuth::new;
        }
    }

    protected static byte[] random16bytes() {
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        return randomBytes;
    }

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
    abstract AccessRequest encodeAuthMechanism(String sharedSecret, byte[] newAuth) throws RadiusPacketException;

    @Override
    protected byte[] genAuth(String sharedSecret) {
        return getAuthenticator() == null ? random16bytes() : getAuthenticator();
    }

    /**
     * AccessRequest overrides this method to generate a randomized authenticator (RFC 2865)
     * and encode required attributes (e.g. User-Password).
     *
     * @param sharedSecret shared secret that secures the communication
     *                     with the other Radius server/client
     * @return RadiusPacket with new authenticator and encoded attributes
     */
    @Override
    public RadiusRequest encodeRequest(String sharedSecret) throws RadiusPacketException {
        if (sharedSecret == null || sharedSecret.isEmpty())
            throw new IllegalArgumentException("Shared secret cannot be null/empty");

        // create authenticator only if needed - maintain idempotence

        final byte[] newAuth = genAuth(sharedSecret);

        return encodeAuthMechanism(sharedSecret, newAuth)
                .encodeMessageAuth(sharedSecret, newAuth);
    }

    /**
     * Verify packet for specific auth protocols
     *
     * @param sharedSecret shared secret
     * @return verified AccessRequest with decoded attributes if appropriate
     * @throws RadiusPacketException if invalid or missing attributes
     */
    protected abstract AccessRequest decodeAuthMechanism(String sharedSecret) throws RadiusPacketException;

    /**
     * AccessRequest cannot verify authenticator as they
     * contain random bytes.
     * <p>
     * It can, however, check the User-Password/Challenge attributes
     * are present and attempt decryption, depending on auth protocol.
     *
     * @param sharedSecret shared secret, only applicable for PAP
     * @return decoded request
     */
    @Override
    public RadiusRequest decodeRequest(String sharedSecret) throws RadiusPacketException {
        verifyMessageAuth(sharedSecret, getAuthenticator());
        return decodeAuthMechanism(sharedSecret);
    }

    interface AccessRequestConstructor {
        AccessRequest newInstance(Dictionary dictionary, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes);
    }

}
