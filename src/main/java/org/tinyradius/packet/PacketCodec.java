package org.tinyradius.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusPacketException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static java.lang.Byte.toUnsignedInt;
import static org.tinyradius.attribute.Attributes.extractAttributes;
import static org.tinyradius.packet.RadiusPacket.HEADER_LENGTH;

/**
 * To encode/decode packets to/from Datagram.
 * <p>
 * A dictionary is required in constructor to avoid passing in dictionary
 * on every conversion - in typical use cases, only one instance of this
 * class will be required as the server/proxy/client will be using one Dictionary.
 */
public class PacketCodec {

    private static final int MAX_PACKET_LENGTH = 4096;

    /**
     * @param packet    packet to convert
     * @param recipient destination socket
     * @return converted DatagramPacket
     * @throws RadiusPacketException if packet could not be encoded/serialized to datagram
     */
    public static DatagramPacket toDatagram(BaseRadiusPacket packet, InetSocketAddress recipient) throws RadiusPacketException {
        return new DatagramPacket(toByteBuf(packet), recipient);
    }

    /**
     * @param packet    packet to convert
     * @param recipient destination socket
     * @param sender    source socket, nullable
     * @return converted DatagramPacket
     * @throws RadiusPacketException if packet could not be encoded/serialized to datagram
     */
    public static DatagramPacket toDatagram(BaseRadiusPacket packet, InetSocketAddress recipient, InetSocketAddress sender) throws RadiusPacketException {
        return new DatagramPacket(toByteBuf(packet), recipient, sender);
    }

    public static ByteBuf toByteBuf(BaseRadiusPacket packet) throws RadiusPacketException {
        byte[] attributes = packet.getAttributeBytes();
        int length = HEADER_LENGTH + attributes.length;
        if (length > MAX_PACKET_LENGTH)
            throw new RadiusPacketException("Packet too long");
        if (packet.getAuthenticator() == null)
            throw new RadiusPacketException("Missing authenticator");
        if (packet.getAuthenticator().length != 16)
            throw new RadiusPacketException("Authenticator must be length 16");

        return Unpooled.buffer(length, length)
                .writeByte(packet.getType())
                .writeByte(packet.getIdentifier())
                .writeShort(length)
                .writeBytes(packet.getAuthenticator())
                .writeBytes(attributes);
    }

    /**
     * Reads a Radius packet from the given input stream and
     * creates an appropriate RadiusPacket/subclass.
     * <p>
     * Makes no distinction between reading requests/responses, and
     * does not attempt to decode attributes/verify authenticators.
     * RadiusPacket should be separately verified after this to check
     * authenticators are valid and required attributes present.
     * <p>
     * Typically used to decode a response where the corresponding request
     * (specifically the authenticator/identifier) are not available or
     * you don't care about validating the created object.
     *
     * @param dictionary dictionary to use for attributes
     * @param datagram   DatagramPacket to read packet from
     * @return new RadiusPacket object
     * @throws RadiusPacketException malformed packet
     */
    public static BaseRadiusPacket fromDatagram(Dictionary dictionary, DatagramPacket datagram) throws RadiusPacketException {
        return fromByteBuf(dictionary, datagram.content());
    }

    /**
     * Reads a request from the given input stream and
     * creates an appropriate RadiusPacket/subclass.
     * <p>
     * Decodes the encrypted fields and attributes of the packet, and checks
     * authenticator if appropriate.
     *
     * @param dictionary   dictionary to use for attributes
     * @param packet       DatagramPacket to read packet from
     * @param sharedSecret shared secret to be used to decode this packet
     * @return new RadiusPacket object
     * @throws RadiusPacketException malformed packet
     */
    public static BaseRadiusPacket fromDatagram(Dictionary dictionary, DatagramPacket packet, String sharedSecret) throws RadiusPacketException {
        final BaseRadiusPacket radiusPacket = fromByteBuf(dictionary, packet.content());
        radiusPacket.verifyRequest(sharedSecret);
        return radiusPacket;
    }

    /**
     * Reads a response from the given input stream and
     * creates an appropriate RadiusPacket/subclass.
     * <p>
     * Decodes the encrypted fields and attributes of the packet, and checks
     * authenticator if appropriate.
     *
     * @param datagram     DatagramPacket to read packet from
     * @param sharedSecret shared secret to be used to decode this packet
     * @param request      associated request packet for parsing response
     * @return new RadiusPacket object
     * @throws RadiusPacketException malformed packet
     */
    public static BaseRadiusPacket fromDatagram(Dictionary dictionary, DatagramPacket datagram, String sharedSecret, BaseRadiusPacket request)
            throws RadiusPacketException {
        final BaseRadiusPacket radiusPacket = fromByteBuf(dictionary, datagram.content(), request.getIdentifier());
        radiusPacket.verifyResponse(sharedSecret, request.getAuthenticator());
        return radiusPacket;
    }

    public static BaseRadiusPacket fromByteBuf(Dictionary dictionary, ByteBuf byteBuf) throws RadiusPacketException {
        return fromByteBuf(dictionary, byteBuf, -1);
    }

    /**
     * Reads a Radius packet from the given input stream and
     * creates an appropriate RadiusPacket descendant object.
     * <p>
     * Decodes the encrypted fields and attributes of the packet, and checks
     * authenticator if appropriate.
     *
     * @param dictionary dictionary to use for attributes
     * @param byteBuf    DatagramPacket to read packet from
     * @param requestId  id that packet identifier has to match, otherwise -1 if skipping checks
     * @return new RadiusPacket object
     * @throws RadiusPacketException malformed packet
     */
    private static BaseRadiusPacket fromByteBuf(Dictionary dictionary, ByteBuf byteBuf, int requestId) throws RadiusPacketException {

        final ByteBuffer content = byteBuf.nioBuffer();
        if (content.remaining() < HEADER_LENGTH) {
            throw new RadiusPacketException("Readable bytes is less than header length");
        }

        int type = toUnsignedInt(content.get());
        int packetId = toUnsignedInt(content.get());
        int length = content.getShort();

        if (requestId != -1 && requestId != packetId)
            throw new RadiusPacketException("Bad packet: invalid packet identifier - request: " + requestId + ", response: " + packetId);
        if (length < HEADER_LENGTH)
            throw new RadiusPacketException("Bad packet: packet too short (" + length + " bytes)");
        if (length > MAX_PACKET_LENGTH)
            throw new RadiusPacketException("Bad packet: packet too long (" + length + " bytes)");

        byte[] authenticator = new byte[16];
        content.get(authenticator);

        if (content.remaining() != length - HEADER_LENGTH)
            throw new RadiusPacketException("Bad packet: packet length mismatch");

        byte[] attributes = new byte[content.remaining()];
        content.get(attributes);

        return RadiusPackets.create(dictionary, type, packetId, authenticator,
                extractAttributes(dictionary, -1, attributes, 0));
    }

}
