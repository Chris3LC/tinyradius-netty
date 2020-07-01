package org.tinyradius.attribute.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.tinyradius.util.RadiusPacketException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Attribute is encrypted with the method as defined in RFC2865 for the User-Password attribute
 */
class UserPasswordCodec extends BaseCodec {

    @Override
    public byte[] encode(byte[] data, String sharedSecret, byte[] requestAuth) {
        return encodeData(data, requestAuth, sharedSecret.getBytes(UTF_8));
    }

    @Override
    public byte[] decode(byte[] data, String sharedSecret, byte[] requestAuth) throws RadiusPacketException {
        return decodeData(data, sharedSecret.getBytes(UTF_8), requestAuth);
    }

    private byte[] encodeData(byte[] data, byte[] auth, byte[] secret) {
        // todo add length checks
        Objects.requireNonNull(data, "Data to encode cannot be null");
        Objects.requireNonNull(secret, "Shared secret cannot be null");

        final byte[] str = pad16x(data);
        final ByteBuffer buffer = ByteBuffer.allocate(str.length);

        byte[] C = auth;

        for (int i = 0; i < str.length; i += 16) {
            C = xor16(str, i, md5(secret, C));
            buffer.put(C);
        }

        return buffer.array();
    }

    // todo same arg order
    private byte[] decodeData(byte[] encodedData, byte[] secret, byte[] auth) throws RadiusPacketException {
        if (encodedData.length < 16)
            throw new RadiusPacketException("Malformed attribute while decoding with RFC2865 User-Password method - " +
                    "data must be at least 16 octets, actual: " + encodedData.length);

        if (encodedData.length % 16 != 0)
            throw new RadiusPacketException("Malformed attribute while decoding with RFC2865 User-Password method - " +
                    "data octets must be multiple of 16, actual: " + encodedData.length);

        final ByteBuf buf = Unpooled.buffer(encodedData.length, encodedData.length);
        byte[] C = auth;

        for (int i = 0; i < encodedData.length; i += 16) {
            buf.writeBytes(xor16(encodedData, i, md5(secret, C)));
            C = Arrays.copyOfRange(encodedData, i, 16);
        }

        final int nullIndex = buf.indexOf(0, encodedData.length - 1, (byte) 0);

        return nullIndex == -1 ?
                buf.copy().array() :
                buf.writerIndex(nullIndex).copy().array();
    }
}
