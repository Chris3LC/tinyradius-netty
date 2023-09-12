package org.tinyradius.core.attribute;

import io.netty.buffer.ByteBuf;
import org.tinyradius.core.RadiusPacketException;
import org.tinyradius.core.attribute.codec.AttributeCodecType;
import org.tinyradius.core.attribute.type.AttributeType;
import org.tinyradius.core.attribute.type.EncodedAttribute;
import org.tinyradius.core.attribute.type.OctetsAttribute;
import org.tinyradius.core.attribute.type.RadiusAttribute;
import org.tinyradius.core.dictionary.Dictionary;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.tinyradius.core.attribute.codec.AttributeCodecType.*;
import static org.tinyradius.core.attribute.rfc.Rfc2865.USER_PASSWORD;
import static org.tinyradius.core.attribute.rfc.Rfc2868.TUNNEL_PASSWORD;
import static org.tinyradius.core.attribute.type.VendorSpecificAttribute.VENDOR_SPECIFIC;

/**
 * Represents a Radius attribute type.
 */
public class AttributeTemplate {

    private final int vendorId;
    private final int type;
    private final String name;
    private final String dataType;

    private final boolean tagged;
    private final AttributeCodecType codecType;

    private final AttributeType decodedType;
    private final AttributeType encodedType;

    private final Map<Integer, String> int2str = new HashMap<>();
    private final Map<String, Integer> str2int = new HashMap<>();

    /**
     * Create a new attribute type. Convenience method that assumes no encrypt and no Tag support.
     *
     * @param vendorId    vendor ID or -1 if N/A
     * @param type        sub-attribute type code, as unsigned byte
     * @param name        sub-attribute name
     * @param rawDataType string | octets | integer | date | ipaddr | ipv6addr | ipv6prefix
     * @see AttributeTemplate#AttributeTemplate(int, int, String, String, byte, boolean)
     */
    public AttributeTemplate(int vendorId, int type, String name, String rawDataType) {
        this(vendorId, type, name, rawDataType, (byte) 0, false);
    }

    /**
     * Create a new attribute type.
     *
     * @param vendorId    vendor ID or -1 if N/A
     * @param type        sub-attribute type code, as unsigned byte
     * @param name        sub-attribute name
     * @param rawDataType string | octets | integer | date | ipaddr | ipv6addr | ipv6prefix
     * @param encryptFlag encrypt flag as per FreeRadius dictionary format, can be 1/2/3, or default 0 for none
     * @param hasTag      whether attribute supports tags, as defined in RFC2868, default false
     */
    public AttributeTemplate(int vendorId, int type, String name, String rawDataType, byte encryptFlag, boolean hasTag) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("Name is empty");
        requireNonNull(rawDataType, "Data type is null");
        this.vendorId = vendorId;
        this.type = type;
        this.name = name;
        this.dataType = rawDataType.toLowerCase();

        decodedType = vendorId == -1 && type == VENDOR_SPECIFIC ?
                AttributeType.VSA :
                AttributeType.fromDataType(this.dataType);

        this.tagged = detectHasTag(vendorId, type, hasTag);
        this.codecType = detectAttributeCodec(vendorId, type, encryptFlag);
        this.encodedType = this.codecType == NO_ENCRYPT ?
                decodedType : AttributeType.OCTETS;
    }

    /**
     * Create RadiusAttribute.
     *
     * @param dictionary dictionary to use
     * @param tag        tag as per RFC2868
     * @param value      value to set attribute
     * @return new RadiusAttribute
     */
    public RadiusAttribute create(Dictionary dictionary, byte tag, byte[] value) {
        return decodedType.create(dictionary, vendorId, type, tag, value);
    }

    /**
     * Create RadiusAttribute.
     *
     * @param dictionary dictionary to use
     * @param tag        tag as per RFC2868
     * @param value      value to set attribute
     * @return new RadiusAttribute
     */
    public RadiusAttribute create(Dictionary dictionary, byte tag, String value) {
        return decodedType.create(dictionary, vendorId, type, tag, value);
    }

    /**
     * Parse RadiusAttribute from raw byte data.
     * <p>
     * If attribute type has encryption, this will create an OctetsAttribute wrapped in EncodedDecorator,
     * otherwise uses the type specified in dictionary.
     *
     * @param dictionary dictionary to use
     * @param data       attribute data to parse incl. type/length
     * @return new RadiusAttribute
     */
    public RadiusAttribute parse(Dictionary dictionary, ByteBuf data) {
        return autoWrapEncode(encodedType.create(dictionary, vendorId, data));
    }

    /**
     * Create RadiusAttribute with encoded data.
     * <p>
     * If attribute type supports encryption, this will
     * return an OctetsAttribute as underlying implementation
     * so contents aren't validated at construction.
     *
     * @param dictionary   dictionary to use
     * @param tag          tag as per RFC2868
     * @param encodedValue encoded data, attribute data excl. type/length/tag
     * @return new RadiusAttribute
     */
    public RadiusAttribute createEncoded(Dictionary dictionary, byte tag, byte[] encodedValue) {
        return autoWrapEncode(encodedType.create(dictionary, vendorId, type, tag, encodedValue));
    }

    private RadiusAttribute autoWrapEncode(OctetsAttribute attribute) {
        return encryptEnabled() ? new EncodedAttribute(attribute) : attribute;
    }

    /**
     * @return Radius type code for this attribute e.g. '1' (for User-Name)
     */
    public int getType() {
        return type;
    }

    /**
     * @return name of type e.g. 'User-Name'
     */
    public String getName() {
        return name;
    }

    /**
     * @return vendor ID or -1 if not applicable
     */
    public int getVendorId() {
        return vendorId;
    }

    /**
     * @return string | octets | integer | date | ipaddr | ipv6addr | ipv6prefix
     */
    public String getDataType() {
        return dataType;
    }

    /**
     * @return whether attribute supports Tag field as per RFC2868
     */
    public boolean isTagged() {
        return tagged;
    }

    public boolean encryptEnabled() {
        return codecType != NO_ENCRYPT;
    }

    /**
     * @return one of AttributeCodecType enum, defaults to NO_ENCRYPT for none
     */
    public AttributeCodecType getCodecType() {
        return codecType;
    }

    /**
     * @param value int value
     * @return the name of the given integer value if this attribute
     * is an enumeration, or null if it is not or if the integer value
     * is unknown.
     */
    public String getEnumeration(int value) {
        return int2str.get(value);
    }

    /**
     * @param value string value
     * @return the number of the given string value if this attribute is
     * an enumeration, or null if it is not or if the string value is unknown.
     */
    public Integer getEnumeration(String value) {
        return str2int.get(value);
    }

    /**
     * Adds a name for an integer value of this attribute.
     *
     * @param num  number that shall get a name
     * @param name the name for this number
     */
    public void addEnumerationValue(int num, String name) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("Name is empty");
        int2str.put(num, name);
        str2int.put(name, num);
    }

    /**
     * @param attribute   attribute to encode
     * @param requestAuth (corresponding) request packet authenticator
     * @param secret      shared secret to encode with
     * @return attribute with encoded data
     * @throws RadiusPacketException errors encoding attribute
     */
    public RadiusAttribute encode(RadiusAttribute attribute, byte[] requestAuth, String secret) throws RadiusPacketException {
        // don't wrap in EncodedDecorator if not supported
        if (!encryptEnabled() || attribute.isEncoded())
            return attribute;

        try {
            return createEncoded(attribute.getDictionary(), attribute.getTag().orElse((byte) 0),
                    codecType.getCodec().encode(attribute.getValue(), requestAuth, secret));
        } catch (Exception e) {
            throw new RadiusPacketException("Error encoding attribute " + attribute, e);
        }
    }

    /**
     * @param attribute   attribute to decode
     * @param requestAuth (corresponding) request packet authenticator
     * @param secret      shared secret to decode with
     * @return attribute with decoded data
     * @throws RadiusPacketException errors decoding attribute
     */
    public RadiusAttribute decode(RadiusAttribute attribute, byte[] requestAuth, String secret) throws RadiusPacketException {
        if (!attribute.isEncoded())
            return attribute;

        try {
            return create(attribute.getDictionary(), attribute.getTag().orElse((byte) 0),
                    codecType.getCodec().decode(attribute.getValue(), requestAuth, secret));
        } catch (Exception e) {
            throw new RadiusPacketException("Error decoding attribute " + attribute, e);
        }
    }

    public String toString() {
        String s = Integer.toUnsignedString(getType()) + "/" + getName() + ": " + getDataType();
        if (getVendorId() != -1)
            s += " (Vendor " + getVendorId() + ")";
        return s;
    }

    private static boolean detectHasTag(int vendorId, int type, boolean hasTag) {
        return (vendorId == -1 && type == TUNNEL_PASSWORD) || hasTag;
    }

    private static AttributeCodecType detectAttributeCodec(int vendorId, int type, byte encryptFlag) {
        if (vendorId == -1 && type == USER_PASSWORD)
            return RFC2865_USER_PASSWORD;

        if (vendorId == -1 && type == TUNNEL_PASSWORD)
            return RFC2868_TUNNEL_PASSWORD;

        if (vendorId == 529 && type == 214) // Ascend-Send-Secret
            return ASCEND_SEND_SECRET;

        return fromEncryptFlagId(encryptFlag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeTemplate)) return false;
        final AttributeTemplate that = (AttributeTemplate) o;
        return vendorId == that.vendorId &&
                type == that.type &&
                tagged == that.tagged &&
                name.equals(that.name) &&
                dataType.equals(that.dataType) &&
                codecType == that.codecType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendorId, type, name, dataType, tagged, codecType);
    }
}
