package org.tinyradius.attribute;

import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusException;

import java.util.StringTokenizer;

/**
 * This class represents a Radius attribute for an IP number.
 */
public class IpAttribute extends RadiusAttribute {

    public static IpAttribute parse(Dictionary dictionary, int vendorId, byte[] data, int offset) throws RadiusException {
        final int length = readLength(data, offset);
        if (length != 6)
            throw new RadiusException("IP attribute: expected length 6, packet declared " + length);

        return new IpAttribute(dictionary, vendorId, readType(data, offset), readData(data, offset));
    }

    public IpAttribute(Dictionary dictionary, int vendorId, int type, byte[] data) {
        super(dictionary, vendorId, type, data);
    }

    public IpAttribute(Dictionary dictionary, int vendorId, int type, String value) {
        this(dictionary, vendorId, type, convertValue(value));
    }

    /**
     * @param type  attribute type code
     * @param value 32 bit unsigned int
     */
    public IpAttribute(Dictionary dictionary, int vendorId, int type, long value) {
        this(dictionary, vendorId, type, convertValue(value));
    }

    /**
     * Returns the attribute value (IP number) as a string of the
     * format "xx.xx.xx.xx".
     */
    @Override
    public String getDataString() {
        StringBuilder ip = new StringBuilder();
        byte[] data = getData();
        if (data == null || data.length != 4)
            throw new RuntimeException("ip attribute: expected 4 bytes attribute data");

        ip.append(data[0] & 0x0ff);
        ip.append(".");
        ip.append(data[1] & 0x0ff);
        ip.append(".");
        ip.append(data[2] & 0x0ff);
        ip.append(".");
        ip.append(data[3] & 0x0ff);

        return ip.toString();
    }

    /**
     * Returns the IP number as a 32 bit unsigned number. The number is
     * returned in a long because Java does not support unsigned ints.
     *
     * @return IP number
     */
    public long getDataLong() {
        byte[] data = getData();
        if (data == null || data.length != 4)
            throw new RuntimeException("expected 4 bytes attribute data");
        return ((long) (data[0] & 0x0ff)) << 24 | (data[1] & 0x0ff) << 16 |
                (data[2] & 0x0ff) << 8 | (data[3] & 0x0ff);
    }

    /**
     * Sets the attribute value (IP number). String format:
     * "xx.xx.xx.xx".
     *
     * @throws IllegalArgumentException bad IP address
     */
    private static byte[] convertValue(String value) {
        if (value == null || value.length() < 7 || value.length() > 15)
            throw new IllegalArgumentException("bad IP number");

        StringTokenizer tok = new StringTokenizer(value, ".");
        if (tok.countTokens() != 4)
            throw new IllegalArgumentException("bad IP number: 4 numbers required");

        byte[] data = new byte[4];
        for (int i = 0; i < 4; i++) {
            int num = Integer.parseInt(tok.nextToken());
            if (num < 0 || num > 255)
                throw new IllegalArgumentException("bad IP number: num out of bounds");
            data[i] = (byte) num;
        }

        return (data);
    }

    /**
     * Sets the IP number represented by this IpAttribute
     * as a 32 bit unsigned number.
     *
     * @param ip IP address as 32-bit unsigned number
     */
    private static byte[] convertValue(long ip) {
        byte[] data = new byte[4];
        data[0] = (byte) ((ip >> 24) & 0x0ff);
        data[1] = (byte) ((ip >> 16) & 0x0ff);
        data[2] = (byte) ((ip >> 8) & 0x0ff);
        data[3] = (byte) (ip & 0x0ff);
        return data;
    }

}
