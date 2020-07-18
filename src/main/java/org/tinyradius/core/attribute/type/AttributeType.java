package org.tinyradius.core.attribute.type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.tinyradius.core.attribute.AttributeTemplate;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.dictionary.Vendor;

import java.util.Optional;

public enum AttributeType {
    VSA(VendorSpecificAttribute::new, (dictionary, i, i1, s) -> OctetsAttribute.stringHexParser(s)),
    OCTETS(OctetsAttribute::new, (dictionary, i, i1, s) -> OctetsAttribute.stringHexParser(s)),
    STRING(StringAttribute::new, (dictionary, i, i1, s) -> StringAttribute.stringParser(s)),
    INTEGER(IntegerAttribute::new, IntegerAttribute::stringParser),
    IPV4(IpAttribute.V4::new, (dictionary, i, i1, s) -> IpAttribute.stringParser(s)),
    IPV6(IpAttribute.V6::new, (dictionary, i, i1, s) -> IpAttribute.stringParser(s)),
    IPV6_PREFIX(Ipv6PrefixAttribute::new, (dictionary, i, i1, s) -> Ipv6PrefixAttribute.stringParser(s));

    private final ByteBufConstructor byteBufConstructor;
    private final StringParser stringParser;

    AttributeType(ByteBufConstructor byteBufConstructor, StringParser stringParser) {
        this.byteBufConstructor = byteBufConstructor;
        this.stringParser = stringParser;
    }

    public OctetsAttribute create(Dictionary dictionary, int vendorId, ByteBuf data) {
        return byteBufConstructor.newInstance(dictionary, vendorId, data);
    }

    public OctetsAttribute create(Dictionary dictionary, int vendorId, int type, byte tag, byte[] value) {
        final Optional<Vendor> vendor = dictionary.getVendor(vendorId);
        final int headerSize = vendor.map(Vendor::getHeaderSize).orElse(2);

        final byte[] tagBytes = toTagBytes(dictionary, vendorId, type, tag);
        final int length = headerSize + tagBytes.length + value.length;
        final byte[] typeBytes = vendor
                .map(v -> v.toTypeBytes(type))
                .orElse(new byte[]{(byte) type});
        final byte[] lengthBytes = vendor
                .map(v -> v.toLengthBytes(length))
                .orElse(new byte[]{(byte) length});

        final ByteBuf byteBuf = Unpooled.buffer(length, length)
                .writeBytes(typeBytes)
                .writeBytes(lengthBytes)
                .writeBytes(tagBytes)
                .writeBytes(value);

        // todo test both vendor success / failure

        return byteBufConstructor.newInstance(dictionary, vendorId, byteBuf);
    }

    private static byte[] toTagBytes(Dictionary dictionary, int vendorId, int type, byte tag) {
        return dictionary.getAttributeTemplate(vendorId, type)
                .filter(AttributeTemplate::isTagged)
                .map(x -> new byte[]{tag})
                .orElse(new byte[0]);
    }

    public OctetsAttribute create(Dictionary dictionary, int vendorId, int type, byte tag, String value) {
        final byte[] bytes = stringParser.parse(dictionary, vendorId, type, value);
        return create(dictionary, vendorId, type, tag, bytes);
    }

    public static AttributeType fromDataType(String dataType) {
        switch (dataType) {
            case "vsa":
                return AttributeType.VSA;
            case "string":
                return AttributeType.STRING;
            case "integer":
            case "date":
                return AttributeType.INTEGER;
            case "ipaddr":
                return AttributeType.IPV4;
            case "ipv6addr":
                return AttributeType.IPV6;
            case "ipv6prefix":
                return AttributeType.IPV6_PREFIX;
            case "octets":
            case "ifid":
            case "integer64":
            case "ether":
            case "abinary":
            case "byte":
            case "short":
            case "signed":
            case "tlv":
            case "ipv4prefix":
            default:
                return AttributeType.OCTETS;
        }
    }

    private interface ByteBufConstructor {
        OctetsAttribute newInstance(Dictionary dictionary, int vendorId, ByteBuf value);
    }

    private interface StringParser {
        byte[] parse(Dictionary dictionary, int vendorId, int type, String value);
    }
}
