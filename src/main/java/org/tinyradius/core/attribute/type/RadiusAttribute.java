package org.tinyradius.core.attribute.type;

import io.netty.buffer.ByteBuf;
import org.tinyradius.core.RadiusPacketException;
import org.tinyradius.core.attribute.AttributeTemplate;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.dictionary.Vendor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface RadiusAttribute {

    /**
     * @return vendor Id if Vendor-Specific attribute or sub-attribute, otherwise -1
     */
    int getVendorId();

    /**
     * @return attribute type code, typically 0-255
     */
    default int getType() {
        switch (getTypeSize()) {
            case 2:
                return getData().getShort(0);
            case 4:
                return getData().getInt(0);
            case 1:
            default:
                return Byte.toUnsignedInt(getData().getByte(0));
        }
    }

    default int getLength() {
        switch (getLengthSize()) {
            case 0:
                return getData().readableBytes();
            case 2:
                return getData().getShort(getTypeSize());
            case 1:
            default:
                return Byte.toUnsignedInt(getData().getByte(getTypeSize())); // max 255
        }
    }

    default int getHeaderSize() {
        return getTypeSize() + getLengthSize();
    }

    /**
     * @return Tag if available and specified for attribute type (RFC2868)
     */
    Optional<Byte> getTag();

    /**
     * @return attribute data as raw bytes
     */
    byte[] getValue();

    /**
     * @return value of this attribute as a hex string.
     */
    String getValueString();

    /**
     * @return dictionary that attribute uses
     */
    Dictionary getDictionary();

    ByteBuf getData();

    default ByteBuf toByteBuf() {
        return getData();
    }

    /**
     * @return entire attribute (including headers) as byte array
     */
    default byte[] toByteArray() {
        return getData().copy().array();
    }

    default int getTypeSize() {
        return getVendor()
                .map(Vendor::getTypeSize)
                .orElse(1);
    }

    default int getLengthSize() {
        return getVendor()
                .map(Vendor::getLengthSize)
                .orElse(1);
    }

    default int getTagSize() {
        return getDictionary().getAttributeTemplate(getVendorId(), getType())
                .map(AttributeTemplate::isTagged)
                .orElse(false) ? 1 : 0;
    }

    default Optional<Vendor> getVendor() {
        return getDictionary().getVendor(getVendorId());
    }

    default boolean isTagged() {
        return getAttributeTemplate()
                .map(AttributeTemplate::isTagged)
                .orElse(false);
    }

    default String getAttributeName() {
        return getAttributeTemplate()
                .map(AttributeTemplate::getName)
                .orElse(getVendorId() != -1 ?
                        "Unknown-Sub-Attribute-" + getType() :
                        "Unknown-Attribute-" + getType());
    }

    /**
     * Returns set of all nested attributes if contains sub-attributes,
     * otherwise singleton set of current attribute.
     *
     * @return List of RadiusAttributes
     */
    default List<RadiusAttribute> flatten() {
        return Collections.singletonList(this);
    }

    /**
     * @return AttributeTemplate used to define this attribute
     */
    default Optional<AttributeTemplate> getAttributeTemplate() {
        return getDictionary().getAttributeTemplate(getVendorId(), getType());
    }

    /**
     * Encodes attribute. Must be idempotent.
     *
     * @param requestAuth (corresponding) request packet authenticator
     * @param secret      shared secret to encode with
     * @return attribute with encoded data
     * @throws RadiusPacketException errors encoding attribute
     */
    default RadiusAttribute encode(byte[] requestAuth, String secret) throws RadiusPacketException {
        return this;
    }

    /**
     * Decodes attribute. Must be idempotent.
     *
     * @param requestAuth (corresponding) request packet authenticator
     * @param secret      shared secret to encode with
     * @return attribute with encoded data
     * @throws RadiusPacketException errors decoding attribute
     */
    default RadiusAttribute decode(byte[] requestAuth, String secret) throws RadiusPacketException {
        return this;
    }

    default boolean isEncoded() {
        return false;
    }
}
