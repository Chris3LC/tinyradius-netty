package org.tinyradius.packet.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tinyradius.attribute.AttributeTemplate;
import org.tinyradius.attribute.type.RadiusAttribute;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusPacketException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Partial implementation for encoding/verifying Message-Authenticator (RFC 2869)
 *
 * @param <T> same type as implementation
 */
public interface MessageAuthSupport<T extends RadiusPacket<T>> extends RadiusPacket<T> {

    Logger msgAuthLogger = LogManager.getLogger();
    byte MESSAGE_AUTHENTICATOR = 80;

    static byte[] calcMessageAuthInput(RadiusPacket<?> packet, byte[] requestAuth) {
        final ByteBuf buf = Unpooled.buffer()
                .writeByte(packet.getType())
                .writeByte(packet.getId())
                .writeShort(0) // placeholder
                .writeBytes(requestAuth);

        for (RadiusAttribute attribute : packet.getAttributes()) {
            if (attribute.getVendorId() == -1 && attribute.getType() == MESSAGE_AUTHENTICATOR)
                buf.writeByte(MESSAGE_AUTHENTICATOR)
                        .writeByte(18)
                        .writeBytes(new byte[16]);
            else
                buf.writeBytes(attribute.toByteArray());
        }

        return buf.setShort(2, buf.readableBytes()).copy().array();
    }

    static Mac getHmacMd5(String key) {
        try {
            final String HMAC_MD5 = "HmacMD5";
            final SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), HMAC_MD5);
            final Mac mac = Mac.getInstance(HMAC_MD5);
            mac.init(secretKeySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalArgumentException(e); // never happens
        }
    }

    default void verifyMessageAuth(String sharedSecret, byte[] requestAuth) throws RadiusPacketException {
        final List<RadiusAttribute> msgAuthAttr = filterAttributes(MESSAGE_AUTHENTICATOR);

        if (msgAuthAttr.isEmpty())
            return;

        if (msgAuthAttr.size() > 1)
            throw new RadiusPacketException("Packet should have at most one Message-Authenticator attribute, has " + msgAuthAttr.size());

        final byte[] messageAuth = msgAuthAttr.get(0).getValue();

        if (!Arrays.equals(messageAuth, computeMessageAuth(this, sharedSecret, requestAuth))) {
            // find attributes that should be encoded but aren't
            final boolean decodedAlready = getAttributes().stream()
                    .filter(a -> a.getAttributeTemplate()
                            .map(AttributeTemplate::encryptEnabled)
                            .orElse(false))
                    .anyMatch(a -> !a.isEncoded());

            if (decodedAlready)
                msgAuthLogger.info("Skipping Message-Authenticator check - attributes have been decrypted already");
            else
                throw new RadiusPacketException("Message-Authenticator check failed");
        }
    }

    default byte[] computeMessageAuth(RadiusPacket<?> packet, String sharedSecret, byte[] requestAuth) {
        Objects.requireNonNull(requestAuth, "Request Authenticator cannot be null for Message-Authenticator hashing");
        final byte[] messageAuthInput = calcMessageAuthInput(packet, requestAuth);
        return getHmacMd5(sharedSecret).doFinal(messageAuthInput);
    }

    /**
     * Creates packet with an encoded Message-Authenticator attribute.
     * <p>
     * Note: 'this' packet authenticator is ignored, only requestAuth param is used.
     *
     * @param sharedSecret shared secret
     * @param requestAuth  current packet auth if encoding request,
     *                     otherwise corresponding request auth
     * @return encoded copy of packet
     */
    default T encodeMessageAuth(String sharedSecret, byte[] requestAuth) {
        // When the message integrity check is calculated the signature
        // string should be considered to be sixteen octets of zero.
        final ByteBuffer buffer = ByteBuffer.allocate(16);

        final T newPacket = this
                .removeAttributes(MESSAGE_AUTHENTICATOR)
                .addAttribute(getDictionary().createAttribute(-1, MESSAGE_AUTHENTICATOR, (byte) 0, buffer.array()));

        buffer.put(computeMessageAuth(newPacket, sharedSecret, requestAuth));

        return newPacket;
    }
}
