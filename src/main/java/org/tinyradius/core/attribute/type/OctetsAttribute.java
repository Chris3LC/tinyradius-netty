package org.tinyradius.core.attribute.type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.tinyradius.core.RadiusPacketException;
import org.tinyradius.core.attribute.AttributeTemplate;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.dictionary.Vendor;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The basic generic Radius attribute. All type-specific implementations extend this class
 * by adding additional type conversion methods and validations.
 */
public class OctetsAttribute implements RadiusAttribute {

    private final Dictionary dictionary;

    private final ByteBuf data;
    private final int vendorId; // for Vendor-Specific sub-attributes, otherwise -1

    public OctetsAttribute(Dictionary dictionary, int vendorId, ByteBuf data) {
        this.dictionary = requireNonNull(dictionary, "Dictionary not set");

        if (requireNonNull(data, "Attribute data not set").readableBytes() > 253)
            throw new IllegalArgumentException("Attribute data too long, max 253 octets, actual: " + data.readableBytes());

        final Optional<Vendor> vendor = dictionary.getVendor(vendorId);
        final int typeSize = vendor.map(Vendor::getTypeSize).orElse(1);
        final int lengthSize = vendor.map(Vendor::getLengthSize).orElse(1);


        // todo verify length matches length field?
        final byte[] lengthBytes = toLengthBytes(lengthSize, length);

        this.vendorId = vendorId;
        this.data = data;

        // todo check not oversize
    }

    public OctetsAttribute(Dictionary dictionary, int vendorId, byte[] data) {
        this(dictionary, vendorId, Unpooled.wrappedBuffer(data));
    }

    @Override
    public int getVendorId() {
        return vendorId;
    }

    /**
     * @return RFC2868 Tag
     */
    @Override
    public Optional<Byte> getTag() {
        return isTagged() ?
                Optional.of(data.getByte(getHeaderSize())) :
                Optional.empty();
    }

    @Override
    public byte[] getValue() {
        final int offset = getHeaderSize() + getTagSize();
        final int length = data.capacity() - offset;
        final byte[] bytes = new byte[length];
        data.getBytes(offset, bytes);
        return bytes;
    }

    @Override
    public String getValueString() {
        return DatatypeConverter.printHexBinary(getValue());
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
        return isTagged() ?
                "[Tagged: " + getTag() + "] " + getAttributeName() + ": " + getValueString() :
                getAttributeName() + ": " + getValueString();
    }

    @Override
    public RadiusAttribute encode(byte[] requestAuth, String secret) throws RadiusPacketException {
        final Optional<AttributeTemplate> template = getAttributeTemplate();
        return template.isPresent() ?
                template.get().encode(this, requestAuth, secret) :
                this;
    }

    // do not remove - for removing from list of attributes
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OctetsAttribute)) return false;
        OctetsAttribute that = (OctetsAttribute) o;
        return getVendorId() == that.getVendorId() &&
                data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, getVendorId());
    }

    public static byte[] stringHexParser(Dictionary dictionary, int vendorId, int type, byte tag, String value) {
        return DatatypeConverter.parseHexBinary(value);
    }
}
