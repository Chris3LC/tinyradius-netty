package org.tinyradius.attribute.type;

import io.netty.buffer.Unpooled;
import org.tinyradius.attribute.AttributeHolder;
import org.tinyradius.dictionary.Dictionary;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * This class represents a "Vendor-Specific" attribute. Both an attribute itself and an attribute container.
 */
public class VendorSpecificAttribute extends RadiusAttribute implements AttributeHolder<VendorSpecificAttribute> {

    public static final byte VENDOR_SPECIFIC = 26;

    private final int childVendorId;
    private final List<RadiusAttribute> attributes;

    /**
     * @param dictionary  dictionary to use for (sub)attributes
     * @param vendorId    ignored, VSAs should always be -1 (top level attribute)
     * @param attributeId ignored, should always be Vendor-Specific (26)
     * @param data        data as hex to parse for childVendorId and sub-attributes
     */
    public VendorSpecificAttribute(Dictionary dictionary, int vendorId, int attributeId, String data) {
        this(dictionary, vendorId, attributeId, DatatypeConverter.parseHexBinary(data));
    }

    /**
     * @param dictionary  dictionary to use for (sub)attributes
     * @param vendorId    ignored, VSAs should always be -1 (top level attribute)
     * @param attributeId ignored, should always be Vendor-Specific (26)
     * @param data        data to parse for childVendorId and sub-attributes
     */
    public VendorSpecificAttribute(Dictionary dictionary, int vendorId, int attributeId, byte[] data) {
        this(dictionary, vendorId(data), AttributeHolder.extractAttributes(dictionary, vendorId(data), data, 4), data);
        if (vendorId != -1)
            throw new IllegalArgumentException("Vendor-Specific attribute should be top level attribute, vendorId should be -1, actual: " + vendorId);
        if (attributeId != 26)
            throw new IllegalArgumentException("Vendor-Specific attribute attributeId should always be 26, actual: " + attributeId);
    }

    /**
     * Constructs a new Vendor-Specific attribute
     *
     * @param dictionary    dictionary to use for (sub)attributes
     * @param childVendorId vendor ID of the sub-attributes
     * @param attributes    sub-attributes held
     */
    public VendorSpecificAttribute(Dictionary dictionary, int childVendorId, List<RadiusAttribute> attributes) {
        this(dictionary, childVendorId, attributes, Unpooled.buffer()
                .writeInt(childVendorId)
                .writeBytes(AttributeHolder.attributesToBytes(attributes))
                .copy().array());
    }

    /**
     * @param dictionary    dictionary to use for (sub)attributes
     * @param childVendorId vendor ID of the sub-attributes
     * @param attributes    sub-attributes held
     * @param data          equivalent of childVendorId + subattribute data in byte array form
     */
    private VendorSpecificAttribute(Dictionary dictionary, int childVendorId, List<RadiusAttribute> attributes, byte[] data) {
        super(dictionary, -1, VENDOR_SPECIFIC, data);
        this.childVendorId = childVendorId;
        this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));

        final int len = data.length + 2;
        if (len < 7) // VSA headers are 6 bytes
            throw new IllegalArgumentException("Vendor-Specific attribute should be greater than 6 octets, actual: " + len);
    }

    /**
     * @param data byte array, length minimum 4
     * @return vendorId
     */
    private static int vendorId(byte[] data) {
        return ByteBuffer.wrap(data).getInt();
    }

    @Override
    public int getChildVendorId() {
        return childVendorId;
    }

    @Override
    public List<RadiusAttribute> getAttributes() {
        return attributes;
    }

    @Override
    public VendorSpecificAttribute withAttributes(List<RadiusAttribute> attributes) {
        return new VendorSpecificAttribute(getDictionary(), getChildVendorId(), attributes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vendor-Specific: Vendor ID ").append(getChildVendorId());
        getDictionary().getVendorName(getChildVendorId())
                .ifPresent(s -> sb.append(" (").append(s).append(")"));
        for (RadiusAttribute sa : getAttributes()) {
            sb.append("\n  ").append(sa.toString());
        }
        return sb.toString();
    }

    @Override
    public List<RadiusAttribute> flatten() {
        return new ArrayList<>(getAttributes());
    }
}
