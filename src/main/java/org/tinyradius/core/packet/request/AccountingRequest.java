package org.tinyradius.core.packet.request;

import org.tinyradius.core.attribute.type.RadiusAttribute;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.packet.PacketType;

import java.util.List;

/**
 * This class represents a Radius packet of the type Accounting-Request.
 */
public class AccountingRequest extends GenericRequest {

    /**
     * Constructs an Accounting-Request packet to be sent to a Radius server.
     *
     * @param dictionary    custom dictionary to use
     * @param identifier    packet identifier
     * @param authenticator authenticator for packet, nullable
     * @param attributes    list of attributes
     */
    public AccountingRequest(Dictionary dictionary, byte identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        super(dictionary, PacketType.ACCOUNTING_REQUEST, identifier, authenticator, attributes);
    }

    @Override
    public AccountingRequest encodeRequest(String sharedSecret) {
        return new AccountingRequest(getDictionary(), getId(), genAuth(sharedSecret), getAttributes());
    }

    @Override
    public AccountingRequest withAttributes(List<RadiusAttribute> attributes) {
        return new AccountingRequest(getDictionary(), getId(), getAuthenticator(), attributes);
    }
}
