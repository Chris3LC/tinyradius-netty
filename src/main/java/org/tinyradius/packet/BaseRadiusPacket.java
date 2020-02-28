package org.tinyradius.packet;

import org.tinyradius.attribute.NestedAttributeHolder;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.attribute.VendorSpecificAttribute;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusPacketException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A generic Radius packet. Subclasses provide convenience methods for special packet types.
 */
public class BaseRadiusPacket implements NestedAttributeHolder, PacketAuthSupport, RadiusPacket {

    private static final int VENDOR_SPECIFIC_TYPE = 26;

    private final int type;
    private final int identifier;
    private final List<RadiusAttribute> attributes;
    private final byte[] authenticator;

    private final Dictionary dictionary;

    /**
     * Builds a Radius packet with the given type and identifier,
     * without attributes, and with null authenticator.
     * <p>
     * Use {@link RadiusPackets#create(Dictionary, int, int)}
     * where possible as that automatically creates Access/Accounting
     * variants as required.
     *
     * @param dictionary custom dictionary to use
     * @param type       packet type
     * @param identifier packet identifier
     */
    public BaseRadiusPacket(Dictionary dictionary, int type, int identifier) {
        this(dictionary, type, identifier, null, new ArrayList<>());
    }

    /**
     * Builds a Radius packet with the given type and identifier
     * and without attributes.
     * <p>
     * Use {@link RadiusPackets#create(Dictionary, int, int, byte[])}
     * where possible as that automatically creates Access/Accounting
     * variants as required.
     *
     * @param dictionary    custom dictionary to use
     * @param type          packet type
     * @param identifier    packet identifier
     * @param authenticator authenticator for packet, nullable
     */
    public BaseRadiusPacket(Dictionary dictionary, int type, int identifier, byte[] authenticator) {
        this(dictionary, type, identifier, authenticator, new ArrayList<>());
    }

    /**
     * Builds a Radius packet with the given type and identifier
     * and without attributes.
     * <p>
     * Use {@link RadiusPackets#create(Dictionary, int, int, List)}
     * where possible as that automatically creates Access/Accounting
     * variants as required.
     *
     * @param dictionary custom dictionary to use
     * @param type       packet type
     * @param identifier packet identifier
     * @param attributes list of attributes for packet
     */
    public BaseRadiusPacket(Dictionary dictionary, int type, int identifier, List<RadiusAttribute> attributes) {
        this(dictionary, type, identifier, null, attributes);
    }

    /**
     * Builds a Radius packet with the given type, identifier and attributes.
     * <p>
     * Use {@link RadiusPackets#create(Dictionary, int, int, byte[], List)}
     * where possible as that automatically creates Access/Accounting
     * variants as required.
     *
     * @param dictionary    custom dictionary to use
     * @param type          packet type
     * @param identifier    packet identifier
     * @param authenticator can be null if creating manually
     * @param attributes    list of RadiusAttribute objects
     */
    public BaseRadiusPacket(Dictionary dictionary, int type, int identifier, byte[] authenticator, List<RadiusAttribute> attributes) {
        if (type < 1 || type > 255)
            throw new IllegalArgumentException("Packet type out of bounds: " + type);
        if (identifier < 0 || identifier > 255)
            throw new IllegalArgumentException("Packet identifier out of bounds: " + identifier);
        if (authenticator != null && authenticator.length != 16)
            throw new IllegalArgumentException("Authenticator must be 16 octets, actual: " + authenticator.length);

        this.type = type;
        this.identifier = identifier;
        this.authenticator = authenticator;
        this.attributes = new ArrayList<>(attributes); // catch nulls, avoid mutating original list
        this.dictionary = requireNonNull(dictionary, "dictionary is null");
    }

    @Override
    public int getIdentifier() {
        return identifier;
    }

    @Override
    public int getType() {
        return type;
    }

    /**
     * Adds a Radius attribute to this packet. Can also be used
     * to add Vendor-Specific sub-attributes. If a attribute with
     * a vendor code != -1 is passed in, a VendorSpecificAttribute
     * is created for the sub-attribute.
     *
     * @param attribute RadiusAttribute object
     */
    @Override
    public void addAttribute(RadiusAttribute attribute) {
        if (attribute.getVendorId() == getVendorId() || attribute.getType() == VENDOR_SPECIFIC_TYPE) {
            attributes.add(attribute);
        } else {
            VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, attribute.getVendorId());
            vsa.addAttribute(attribute);
            attributes.add(vsa);
        }
    }

    /**
     * Removes the specified attribute from this packet.
     *
     * @param attribute RadiusAttribute to remove
     */
    @Override
    public void removeAttribute(RadiusAttribute attribute) {
        if (attribute.getVendorId() == getVendorId() || attribute.getType() == VENDOR_SPECIFIC_TYPE) {
            attributes.remove(attribute);
        } else {
            removeSubAttribute(attribute);
        }
    }

    private void removeSubAttribute(RadiusAttribute attribute) {
        for (VendorSpecificAttribute vsa : getVendorSpecificAttributes(attribute.getVendorId())) {
            vsa.removeAttribute(attribute);
            if (vsa.getAttributes().isEmpty())
                // removed the last sub-attribute --> remove the whole Vendor-Specific attribute
                attributes.remove(vsa);
        }
    }

    @Override
    public List<RadiusAttribute> getAttributes() {
        return attributes;
    }

    public BaseRadiusPacket encodeRequest(String sharedSecret) throws RadiusPacketException {
        return encodeResponse(sharedSecret, new byte[16]);
    }

    public BaseRadiusPacket encodeResponse(String sharedSecret, byte[] requestAuth) {
        // todo add Message-Authenticator
        final byte[] authenticator = createHashedAuthenticator(sharedSecret, requestAuth);
        return RadiusPackets.create(dictionary, type, identifier, authenticator, attributes);
    }

    @Override
    public byte[] getAuthenticator() {
        return authenticator == null ? null : authenticator.clone();
    }

    @Override
    public Dictionary getDictionary() {
        return dictionary;
    }

    public void verifyResponse(String sharedSecret, byte[] requestAuth) throws RadiusPacketException {
        verifyPacketAuth(sharedSecret, requestAuth);
    }

    public void verifyRequest(String sharedSecret) throws RadiusPacketException {
        verifyPacketAuth(sharedSecret, new byte[16]);
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(PacketType.getPacketTypeName(getType()));
        s.append(", ID ");
        s.append(identifier);
        for (RadiusAttribute attr : attributes) {
            s.append("\n");
            s.append(attr.toString());
        }
        return s.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseRadiusPacket)) return false;
        BaseRadiusPacket that = (BaseRadiusPacket) o;
        return type == that.type &&
                identifier == that.identifier &&
                Objects.equals(attributes, that.attributes) &&
                Arrays.equals(authenticator, that.authenticator) &&
                Objects.equals(dictionary, that.dictionary);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, identifier, attributes, dictionary);
        result = 31 * result + Arrays.hashCode(authenticator);
        return result;
    }

    public BaseRadiusPacket copy() {
        return RadiusPackets.create(getDictionary(), getType(), getIdentifier(), getAuthenticator(), new ArrayList<>(getAttributes()));
    }
}
