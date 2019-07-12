package org.tinyradius.attribute;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Byte.toUnsignedInt;
import static org.tinyradius.attribute.Attributes.createAttribute;
import static org.tinyradius.attribute.Attributes.parseAttribute;

/**
 * This class represents a "Vendor-Specific" attribute.
 */
public class VendorSpecificAttribute extends RadiusAttribute {

    private static final Logger logger = LoggerFactory.getLogger(VendorSpecificAttribute.class);

    public static final int VENDOR_SPECIFIC = 26;

    private final List<RadiusAttribute> subAttributes;

    public static VendorSpecificAttribute parse(Dictionary dictionary, int ignored, byte[] data, int offset) throws RadiusException {
        int vsaCode = data[offset];
        if (vsaCode != VENDOR_SPECIFIC)
            throw new RadiusException("not a Vendor-Specific attribute");

        // todo check bounds for VSA header lengths
        int vsaLen = toUnsignedInt(data[offset + 1]) - 6;
        if (vsaLen < 6)
            throw new RadiusException("Vendor-Specific attribute too short: " + vsaLen);

        // read vendor ID and vendor data
        int vendorId = (toUnsignedInt(data[offset + 2]) << 24
                | toUnsignedInt(data[offset + 3]) << 16
                | toUnsignedInt(data[offset + 4]) << 8
                | toUnsignedInt(data[offset + 5]));

        final List<RadiusAttribute> subAttributes = extractAttributes(dictionary, data, offset, vsaLen, vendorId);

        return new VendorSpecificAttribute(dictionary, vendorId, subAttributes);
    }

    public VendorSpecificAttribute(Dictionary dictionary, int vendorId, int ignored, byte[] ignored2)  {
        this(dictionary, vendorId, new ArrayList<>());
    }

    public VendorSpecificAttribute(Dictionary dictionary, int vendorId, int ignored, String ignored2)  {
        this(dictionary, vendorId, new ArrayList<>());
    }

    public VendorSpecificAttribute(Dictionary dictionary, int vendorId, List<RadiusAttribute> subAttributes) {
        super(dictionary, vendorId, VENDOR_SPECIFIC, new byte[0]);
        this.subAttributes = subAttributes;
    }

    /**
     * Constructs a new Vendor-Specific attribute to be sent.
     *
     * @param vendorId vendor ID of the sub-attributes
     */
    public VendorSpecificAttribute(Dictionary dictionary, int vendorId) {
        this(dictionary, vendorId, new ArrayList<>());
    }

    /**
     * Adds a sub-attribute to this attribute.
     *
     * @param attribute sub-attribute to add
     */
    public void addSubAttribute(RadiusAttribute attribute) {
        if (attribute.getVendorId() != getVendorId())
            throw new IllegalArgumentException("sub attribute has incorrect vendor ID");

        subAttributes.add(attribute);
    }

    /**
     * Adds a sub-attribute with the specified name to this attribute.
     *
     * @param name  name of the sub-attribute
     * @param value value of the sub-attribute
     * @throws IllegalArgumentException invalid sub-attribute name or value
     */
    public void addSubAttribute(String name, String value) throws RadiusException {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("type name is empty");
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("value is empty");

        AttributeType type = getDictionary().getAttributeTypeByName(name);
        if (type == null)
            throw new IllegalArgumentException("unknown attribute type '" + name + "'");
        if (type.getVendorId() == -1)
            throw new IllegalArgumentException("attribute type '" + name + "' is not a Vendor-Specific sub-attribute");
        if (type.getVendorId() != getVendorId())
            throw new IllegalArgumentException("attribute type '" + name + "' does not belong to vendor ID " + getVendorId());

        RadiusAttribute attribute = createAttribute(getDictionary(), getVendorId(), type.getTypeCode(), value);
        addSubAttribute(attribute);
    }

    /**
     * Removes the specified sub-attribute from this attribute.
     *
     * @param attribute RadiusAttribute to remove
     */
    public void removeSubAttribute(RadiusAttribute attribute) {
        subAttributes.remove(attribute);
    }

    /**
     * Returns the list of sub-attributes.
     *
     * @return List of RadiusAttribute objects
     */
    public List<RadiusAttribute> getSubAttributes() {
        return subAttributes;
    }

    /**
     * Returns all sub-attributes of this attribut which have the given type.
     *
     * @param attributeType type of sub-attributes to get
     * @return list of RadiusAttribute objects, does not return null
     */
    public List<RadiusAttribute> getSubAttributes(int attributeType) {
        if (attributeType < 1 || attributeType > 255)
            throw new IllegalArgumentException("sub-attribute type out of bounds");

        return subAttributes.stream()
                .filter(sa -> sa.getType() == attributeType)
                .collect(Collectors.toList());
    }

    /**
     * Returns a sub-attribute of the given type which may only occur once in
     * this attribute.
     *
     * @param type sub-attribute type
     * @return RadiusAttribute object or null if there is no such sub-attribute
     * @throws RuntimeException if there are multiple occurrences of the
     *                          requested sub-attribute type
     */
    public RadiusAttribute getSubAttribute(int type) {
        List<RadiusAttribute> attrs = getSubAttributes(type);
        if (attrs.size() > 1)
            throw new RuntimeException("multiple sub-attributes of requested type " + type);

        return attrs.isEmpty() ? null : attrs.get(0);
    }

    /**
     * Returns a single sub-attribute of the given type name.
     *
     * @param type attribute type name
     * @return RadiusAttribute object or null if there is no such attribute
     * @throws RuntimeException if the attribute occurs multiple times
     */
    public RadiusAttribute getSubAttribute(String type) {
        if (type == null || type.isEmpty())
            throw new IllegalArgumentException("type name is empty");

        AttributeType t = getDictionary().getAttributeTypeByName(type);
        if (t == null)
            throw new IllegalArgumentException("unknown attribute type name '" + type + "'");
        if (t.getVendorId() != getVendorId())
            throw new IllegalArgumentException("vendor ID mismatch");

        return getSubAttribute(t.getTypeCode());
    }

    /**
     * Returns the value of the Radius attribute of the given type or null if
     * there is no such attribute.
     *
     * @param type attribute type name
     * @return value of the attribute as a string or null if there is no such
     * attribute
     * @throws IllegalArgumentException if the type name is unknown
     * @throws RuntimeException         attribute occurs multiple times
     */
    public String getSubAttributeValue(String type) {
        RadiusAttribute attr = getSubAttribute(type);
        return attr == null ?
                null : attr.getDataString();
    }

    /**
     * Renders this attribute as a byte array.
     */
    @Override
    public byte[] toByteArray()  {
        final ByteBuf buffer = Unpooled.buffer();

        buffer.writeByte(VENDOR_SPECIFIC);
        buffer.writeByte(0); // length placeholder
        buffer.writeInt(getVendorId());
//        buffer.writeByte(getVendorId() >> 24 & 0x0ff); todo check large numbers
//        bos.write(getVendorId() >> 16 & 0x0ff);
//        bos.write(getVendorId() >> 8 & 0x0ff);
//        bos.write(getVendorId() & 0x0ff);

        for (RadiusAttribute attribute : subAttributes) {
            buffer.writeBytes(attribute.toByteArray());
        }

        byte[] attrData = buffer.copy().array();

        int len = attrData.length;
        if (len > 255)
            throw new RuntimeException("Vendor-Specific attribute too long: " + len);

        attrData[1] = (byte) len;
        return attrData;
    }

    private static List<RadiusAttribute> extractAttributes(Dictionary dictionary, byte[] data, int offset, int vsaLen, int vendorId) throws RadiusException {
        final ArrayList<RadiusAttribute> attributes = new ArrayList<>();
        int pos = 0;
        while (pos < vsaLen) {
            if (pos + 1 >= vsaLen)
                throw new RadiusException("Vendor-Specific attribute malformed");
            int subtype = toUnsignedInt(data[(offset + 6) + pos]);
            RadiusAttribute a = parseAttribute(dictionary, vendorId, subtype, data, (offset + 6) + pos);
            attributes.add(a);
            int sublength = toUnsignedInt(data[(offset + 6) + pos + 1]);
            pos += sublength;
        }

        if (pos != vsaLen)
            throw new RadiusException("Vendor-Specific attribute malformed");
        return attributes;
    }

    /**
     * Returns a string representation for debugging.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vendor-Specific: ");
        String vendorName = getDictionary().getVendorName(getVendorId());
        if (vendorName != null) {
            sb.append(vendorName);
            sb.append(" (");
            sb.append(getVendorId());
            sb.append(")");
        } else {
            sb.append("vendor ID ");
            sb.append(getVendorId());
        }
        for (RadiusAttribute sa : getSubAttributes()) {
            sb.append("\n");
            sb.append(sa.toString());
        }
        return sb.toString();
    }
}
