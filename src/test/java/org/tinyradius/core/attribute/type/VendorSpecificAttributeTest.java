package org.tinyradius.core.attribute.type;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tinyradius.core.RadiusPacketException;
import org.tinyradius.core.dictionary.Dictionary;
import org.tinyradius.core.dictionary.parser.DictionaryParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.tinyradius.core.attribute.type.VendorSpecificAttribute.VENDOR_SPECIFIC;

class VendorSpecificAttributeTest {

    private final SecureRandom random = new SecureRandom();
    private static Dictionary dictionary;

    @BeforeAll
    static void setup() throws IOException {
        dictionary = DictionaryParser.newClasspathParser().parseDictionary("org/tinyradius/core/dictionary/test_dictionary");
    }

    @Test
    void parseChildVendorIdZero() {
        // childVendorId 4 bytes, smallest subattribute 2 bytes (type + length)
        final byte[] value = new byte[6];
        value[5] = 2; // subattribute length

        final VendorSpecificAttribute vsa = (VendorSpecificAttribute) dictionary.createAttribute(-1, VENDOR_SPECIFIC, value);

        assertEquals(26, vsa.getType());
        assertEquals(-1, vsa.getVendorId());
        assertEquals(0, vsa.getChildVendorId());
    }

    @Test
    void parseChildVendorIdUnsignedIntMax() {
        final byte[] value = {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // childVendorId
                (byte) 0x00, (byte) 0x02 // subattribute
        };
        final VendorSpecificAttribute vsa = (VendorSpecificAttribute) dictionary.createAttribute(-1, VENDOR_SPECIFIC, value);

        assertEquals(26, vsa.getType());
        assertEquals(-1, vsa.getVendorId());
        assertEquals(-1, vsa.getChildVendorId());
    }

    @Test
    void getVsaSubAttributeValueStringByName() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Collections.singletonList(
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId")
        ));

        assertFalse(vsa.getAttributes().isEmpty());
        assertEquals("myLocationId", vsa.getAttribute("WISPr-Location-ID").get().getValueString());
    }

    @Test
    void addSubAttributeOk() throws RadiusPacketException {
        final String data = "myLocationId";
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(
                dictionary, 14122, Collections.singletonList(dictionary.createAttribute("WISPr-Location-ID", "myLocationId")))
                .addAttribute(dictionary.createAttribute(14122, 2, data));

        assertEquals(2, vsa.getAttributes().size());
        assertEquals(data, vsa.getAttribute(2).get().getValueString());
    }

    @Test
    void addNonVsaSubAttribute() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Collections.singletonList(
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId")
        ));

        final Exception exception = assertThrows(RuntimeException.class, () -> vsa.addAttribute("User-Name", "test1"));
        assertTrue(exception.getMessage().toLowerCase().contains("vendor id doesn't match"));
    }

    @Test
    void addEmptySubAttribute() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Collections.singletonList(
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId")
        ));

        final Exception exception = assertThrows(RuntimeException.class, () -> vsa.addAttribute("", "myLocationId"));
        assertEquals("Unknown attribute type name: ''", exception.getMessage());
    }

    @Test
    void toFromByteArray() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Arrays.asList(
                dictionary.createAttribute(14122, 2, "hiii"),
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId")
        ));
        assertEquals(2, vsa.getAttributes().size());

        // convert to bytes
        final byte[] bytes = vsa.toByteArray();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        assertEquals(VENDOR_SPECIFIC, byteBuffer.get());
        assertEquals(bytes.length, byteBuffer.get());
        assertEquals(14122, byteBuffer.getInt());

        // parse
        final VendorSpecificAttribute parsedAttribute = (VendorSpecificAttribute) dictionary.createAttribute(-1, VENDOR_SPECIFIC, Unpooled.wrappedBuffer(bytes));
        assertArrayEquals(bytes, parsedAttribute.toByteArray());
        assertEquals(2, parsedAttribute.getAttributes().size());

        // convert to bytes again
        assertArrayEquals(bytes, parsedAttribute.toByteArray());

        // remove headers and create
        final byte[] vsaData = Unpooled.wrappedBuffer(bytes)
                .skipBytes(2) // skip type and length
                .copy().array();

        final VendorSpecificAttribute createdAttribute = (VendorSpecificAttribute) dictionary.createAttribute(-1, VENDOR_SPECIFIC, vsaData);
        assertArrayEquals(bytes, createdAttribute.toByteArray());
        assertEquals(2, createdAttribute.getAttributes().size());

        // convert to bytes again
        assertArrayEquals(bytes, createdAttribute.toByteArray());
    }

    @Test
    void customTypeSizeToFromByteArray() {
        // 4846 Lucent has format=2,1 - 'type' field uses 2 octets
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 4846, Arrays.asList(
                dictionary.createAttribute(4846, 2, ByteBuffer.allocate(Integer.BYTES).putInt(456).array()),
                dictionary.createAttribute(4846, 20119, ByteBuffer.allocate(Integer.BYTES).putInt(123).array()),
                // vendor defined, but attribute undefined
                dictionary.createAttribute(4846, 99999, ByteBuffer.allocate(Integer.BYTES).putInt(255).array())
        ));
        System.out.println(vsa);
        // todo test customTypeSize

        // todo test customLengthSize
    }

    /**
     * The String field is one or more octets.  The actual format of the
     * information is site or application specific, and a robust
     * implementation SHOULD support the field as undistinguished octets.
     */
    @Test
    void undistinguishedOctets() {


        // for when vendor not found
        // todo

    }

    @Test
    void toByteArrayLargestUnsignedVendorId() {
        final RadiusAttribute radiusAttribute = dictionary.createAttribute(Integer.parseUnsignedInt("4294967295"), 1, new byte[4]);
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(
                dictionary, Integer.parseUnsignedInt("4294967295"), Collections.singletonList(radiusAttribute));
        assertEquals(1, vsa.getAttributes().size());

        final byte[] bytes = vsa.toByteArray();
        assertEquals(12, bytes.length);
        assertEquals(-1, ByteBuffer.wrap(bytes).getInt(2));
        // int unsigned max == -1 signed
    }

    @Test
    void createTooLong() {
        final List<RadiusAttribute> attributes = Collections.singletonList(
                dictionary.createAttribute(14122, 26, new byte[253]));
        assertTrue(attributes.get(0) instanceof OctetsAttribute);
        final Exception exception = assertThrows(IllegalArgumentException.class,
                () -> new VendorSpecificAttribute(dictionary, 14122, attributes));
        assertTrue(exception.getMessage().contains("Attribute too long"));
    }

    @Test
    void createWithNoSubAttributes() {
        final List<RadiusAttribute> list = Collections.emptyList();
        final Exception exception = assertThrows(RuntimeException.class,
                () -> new VendorSpecificAttribute(dictionary, 14122, list));
        assertTrue(exception.getMessage().toLowerCase().contains("should be greater than 6 octets"));
    }

    @Test
    void testFlatten() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Arrays.asList(
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId"),
                dictionary.createAttribute("WISPr-Location-Name", "myLocationName")
        ));

        assertEquals("[WISPr-Location-ID: myLocationId, WISPr-Location-Name: myLocationName]",
                vsa.flatten().toString());
    }

    @Test
    void testToString() {
        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, 14122, Arrays.asList(
                dictionary.createAttribute("WISPr-Location-ID", "myLocationId"),
                dictionary.createAttribute("WISPr-Location-Name", "myLocationName")
        ));

        assertEquals("Vendor-Specific: Vendor ID 14122 (WISPr)\n" +
                "  WISPr-Location-ID: myLocationId\n" +
                "  WISPr-Location-Name: myLocationName", vsa.toString());
    }

    @Test
    void encodeDecode() throws RadiusPacketException {
        final int vendorId = 14122;
        final String secret = "mySecret";
        final byte tag = 123;
        final byte[] requestAuth = random.generateSeed(16);

        final VendorSpecificAttribute vsa = new VendorSpecificAttribute(dictionary, vendorId, Arrays.asList(
                dictionary.createAttribute(vendorId, 5, tag, "12345"),
                dictionary.createAttribute(vendorId, 6, tag, "12345"),
                dictionary.createAttribute(vendorId, 7, tag, "12345")
        ));
        assertEquals(-1, vsa.getVendorId());
        assertEquals(vendorId, vsa.getChildVendorId());

        final IntegerAttribute minUp = (IntegerAttribute) vsa.getAttribute("WISPr-Bandwidth-Min-Up").get();
        assertEquals(12345, ByteBuffer.wrap(minUp.getValue()).getInt());
        assertEquals(tag, minUp.getTag().get());

        final IntegerAttribute minDown = (IntegerAttribute) vsa.getAttribute("WISPr-Bandwidth-Min-Down").get();
        assertEquals(12345, ByteBuffer.wrap(minDown.getValue()).getInt());
        assertFalse(minDown.getTag().isPresent());

        final IntegerAttribute maxUp = (IntegerAttribute) vsa.getAttribute("WISPr-Bandwidth-Max-Up").get();
        assertEquals(12345, ByteBuffer.wrap(maxUp.getValue()).getInt());
        assertEquals(tag, maxUp.getTag().get());

        // encode
        final VendorSpecificAttribute encode = (VendorSpecificAttribute) vsa.encode(requestAuth, secret);
        assertEquals(-1, encode.getVendorId());
        assertEquals(vendorId, encode.getChildVendorId());

        final OctetsAttribute encodeMinUp = (OctetsAttribute) encode.getAttribute("WISPr-Bandwidth-Min-Up").get();
        assertEquals(12345, ByteBuffer.wrap(encodeMinUp.getValue()).getInt());
        assertEquals(tag, encodeMinUp.getTag().get());

        final EncodedAttribute encodeMinDown = (EncodedAttribute) encode.getAttribute("WISPr-Bandwidth-Min-Down").get();
        assertNotEquals(12345, ByteBuffer.wrap(encodeMinDown.getValue()).getInt());
        assertFalse(encodeMinDown.getTag().isPresent());

        final EncodedAttribute encodeMaxUp = (EncodedAttribute) encode.getAttribute("WISPr-Bandwidth-Max-Up").get();
        assertNotEquals(12345, ByteBuffer.wrap(encodeMaxUp.getValue()).getInt());
        assertEquals(tag, encodeMaxUp.getTag().get());

        // use different encoding
        assertNotEquals(ByteBuffer.wrap(encodeMinDown.getValue()).getInt(), ByteBuffer.wrap(encodeMaxUp.getValue()).getInt());

        // encode again
        final VendorSpecificAttribute encode1 = (VendorSpecificAttribute) encode.encode(requestAuth, secret);
        assertEquals(encode, encode1);

        // decode
        final VendorSpecificAttribute decode = (VendorSpecificAttribute) encode.decode(requestAuth, secret);
        assertEquals(-1, decode.getVendorId());
        assertEquals(vendorId, decode.getChildVendorId());

        final IntegerAttribute decodeMinUp = (IntegerAttribute) decode.getAttribute("WISPr-Bandwidth-Min-Up").get();
        assertEquals(12345, ByteBuffer.wrap(decodeMinUp.getValue()).getInt());
        assertEquals(tag, decodeMinUp.getTag().get());

        final IntegerAttribute decodeMinDown = (IntegerAttribute) decode.getAttribute("WISPr-Bandwidth-Min-Down").get();
        assertEquals(12345, ByteBuffer.wrap(decodeMinUp.getValue()).getInt());
        assertFalse(decodeMinDown.getTag().isPresent());

        final IntegerAttribute decodeMaxUp = (IntegerAttribute) decode.getAttribute("WISPr-Bandwidth-Max-Up").get();
        assertEquals(12345, ByteBuffer.wrap(decodeMinUp.getValue()).getInt());
        assertEquals(tag, decodeMaxUp.getTag().get());

        // decode again
        final VendorSpecificAttribute decode1 = (VendorSpecificAttribute) decode.decode(requestAuth, secret);
        assertEquals(decode1, decode);
    }
}