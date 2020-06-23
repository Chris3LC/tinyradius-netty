package org.tinyradius.packet.request;

import org.tinyradius.attribute.type.RadiusAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.BaseRadiusPacket;

import java.util.List;

public class GenericRadiusRequest extends BaseRadiusPacket<RadiusRequest> implements RadiusRequest {

    /**
     * Builds a Radius packet with the given type, identifier and attributes.
     * <p>
     * Use {@link RadiusRequest#create(Dictionary, byte, byte, byte[], List)}
     * where possible as that automatically creates Access/Accounting
     * variants as required.
     *
     * @param dictionary    custom dictionary to use
     * @param type          packet type
     * @param identifier    packet identifier
     * @param authenticator can be null if creating manually
     * @param attributes    list of RadiusAttribute objects
     */
    public GenericRadiusRequest(Dictionary dictionary, byte type, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        super(dictionary, type, identifier, authenticator, attributes);
    }

    @Override
    public GenericRadiusRequest encodeRequest(String sharedSecret) {
        final byte[] authenticator = createHashedAuthenticator(sharedSecret, new byte[16]);
        return new GenericRadiusRequest(getDictionary(), getType(), getId(), authenticator, getAttributes());
    }

    @Override
    public GenericRadiusRequest withAttributes(List<RadiusAttribute> attributes) {
        return new GenericRadiusRequest(getDictionary(), getType(), getId(), getAuthenticator(), attributes);
    }
}
