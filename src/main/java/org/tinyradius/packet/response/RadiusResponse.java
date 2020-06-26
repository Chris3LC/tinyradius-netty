package org.tinyradius.packet.response;

import org.tinyradius.attribute.type.RadiusAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusPacketException;

import java.util.List;

import static org.tinyradius.packet.util.PacketType.*;

public interface RadiusResponse extends RadiusPacket<RadiusResponse> {

    /**
     * Creates a RadiusPacket object. Depending on the passed type, an
     * appropriate packet is created. Also sets the type, and the
     * the packet identifier.
     *
     * @param dictionary    custom dictionary to use
     * @param type          packet type
     * @param identifier    packet identifier
     * @param authenticator authenticator for packet, nullable
     * @param attributes    list of attributes for packet
     * @return RadiusPacket object
     */
    static RadiusResponse create(Dictionary dictionary, byte type, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        switch (type) {
            case ACCESS_ACCEPT:
            case ACCESS_REJECT:
            case ACCESS_CHALLENGE:
                return new AccessResponse(dictionary, type, identifier, authenticator, attributes);
            default:
                return new GenericResponse(dictionary, type, identifier, authenticator, attributes);
        }
    }

    /**
     * Encode and generate authenticator.
     * <p>
     * Requires request authenticator to generate response authenticator.
     * <p>
     * Must be idempotent.
     *
     * @param sharedSecret shared secret to be used to encode this packet
     * @param requestAuth  request packet authenticator
     * @return new RadiusPacket instance with same properties and valid authenticator
     */
    RadiusResponse encodeResponse(String sharedSecret, byte[] requestAuth);

    /**
     * Decodes the response against the supplied shared secret and request authenticator.
     * <p>
     * Must be idempotent.
     *
     * @param sharedSecret shared secret
     * @param requestAuth  authenticator for corresponding request
     * @return verified RadiusResponse with decoded attributes if appropriate
     * @throws RadiusPacketException if authenticator check fails
     */
    RadiusResponse decodeResponse(String sharedSecret, byte[] requestAuth) throws RadiusPacketException;
}
