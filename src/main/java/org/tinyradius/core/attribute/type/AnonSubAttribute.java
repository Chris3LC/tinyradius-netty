package org.tinyradius.core.attribute.type;

import io.netty.buffer.ByteBuf;
import org.tinyradius.core.dictionary.Dictionary;

import java.util.Optional;

/**
 * Represents an unparsable VSA sub-attribute.
 * <p>
 * As per RFC 2865: The String field is one or more octets. The actual format of the
 * information is site or application specific, and a robust
 * implementation SHOULD support the field as undistinguished octets.
 */
public class AnonSubAttribute implements RadiusAttribute {

    private final Dictionary dictionary;

    private final ByteBuf data;
    private final int vendorId; // for Vendor-Specific sub-attributes, otherwise -1

    public AnonSubAttribute(Dictionary dictionary, int vendorId, ByteBuf data) {
        this.dictionary = dictionary;
        this.vendorId = vendorId;
        this.data = data;
    }

    @Override
    public int getVendorId() {
        return vendorId;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public Optional<Byte> getTag() {
        return Optional.empty();
    }

    @Override
    public byte[] getValue() {
        return data.copy().array();
    }

    @Override
    public String getValueString() {
        return "Unparsable";
    }

    @Override
    public Dictionary getDictionary() {
        return dictionary;
    }

    @Override
    public ByteBuf getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Unparsable";
    }
}
