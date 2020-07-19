package org.tinyradius.core.dictionary;

import io.netty.buffer.ByteBuf;
import org.tinyradius.core.attribute.type.RadiusAttribute;

import static org.tinyradius.core.attribute.type.AttributeType.OCTETS;

/**
 * A dictionary retrieves AttributeTemplate objects by name or attribute ID.
 */
public interface Dictionary extends CoreDictionary {

    /**
     * Creates a RadiusAttribute object of the appropriate type by looking up type and vendorId.
     *
     * @param vendorId vendor ID or -1
     * @param type     attribute type
     * @param value    attribute data as byte array
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(int vendorId, int type, byte[] value) {
        return createAttribute(vendorId, type, (byte) 0, value);
    }

    /**
     * Creates a RadiusAttribute object of the appropriate type by looking up type and vendorId.
     *
     * @param vendorId vendor ID or -1
     * @param type     attribute type
     * @param tag      tag as per RFC2868, nullable, ignored if not supported by attribute
     * @param value    attribute data as byte array
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(int vendorId, int type, byte tag, byte[] value) {
        return getAttributeTemplate(vendorId, type)
                .map(at -> at.create(this, tag, value))
                .orElseGet(() -> OCTETS.create(this, vendorId, type, tag, value));
    }

    /**
     * Creates a RadiusAttribute object of the appropriate type by looking up type and vendorId.
     *
     * @param vendorId vendorId or -1
     * @param type     attribute type
     * @param data     attribute data to parse incl. type/length
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(int vendorId, int type, ByteBuf data) {
        return getAttributeTemplate(vendorId, type)
                .map(at -> at.parse(this, data))
                .orElseGet(() -> OCTETS.create(this, vendorId, data));
    }

    /**
     * Creates a RadiusAttribute object of the appropriate type by looking up type and vendorId.
     *
     * @param vendorId vendor ID or -1
     * @param type     attribute type
     * @param value    attribute data as String
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(int vendorId, int type, String value) {
        return createAttribute(vendorId, type, (byte) 0, value);
    }

    /**
     * Creates a RadiusAttribute object of the appropriate type by looking up type and vendorId.
     *
     * @param vendorId vendor ID or -1
     * @param type     attribute type
     * @param tag      tag as per RFC2868, nullable, ignored if not supported by attribute
     * @param value    attribute data as String
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(int vendorId, int type, byte tag, String value) {
        return getAttributeTemplate(vendorId, type)
                .map(at -> at.create(this, tag, value))
                .orElseGet(() -> OCTETS.create(this, vendorId, type, tag, value));
    }

    /**
     * Convenience method to create a Radius attribute.
     * <p>
     * Uses AttributeTemplate to lookup the type code and parses String value
     * depending on the type.
     * <p>
     * If attribute has tag field, will be set to 0x00.
     *
     * @param name  name of the attribute, for example "NAS-IP-Address", should NOT be 'Vendor-Specific'
     * @param value value of the attribute, for example "127.0.0.1"
     * @return RadiusAttribute object
     */
    default RadiusAttribute createAttribute(String name, String value) {
        return getAttributeTemplate(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown attribute type name: '" + name + "'"))
                .create(this, (byte) 0, value);
    }
}
