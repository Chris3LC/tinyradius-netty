package org.tinyradius.packet;

import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.tinyradius.packet.AccessRequest.AUTH_PAP;

class RadiusPacketEncoderTest {

    private static final SecureRandom random = new SecureRandom();
    private static Dictionary dictionary = DefaultDictionary.INSTANCE;
    private static final InetSocketAddress remoteAddress = new InetSocketAddress(0);

    private byte[] authenticator = new byte[16];

    @BeforeEach
    void setup() {
        random.nextBytes(authenticator);
    }

    @Test
    void getNextPacketIdentifier() {
        int lastId, nextId = 0;
        for (int i = 0; i < 1000; i++) {
            lastId = nextId;
            nextId = RadiusPacketEncoder.nextPacketId();
            try {
                assertEquals(nextId, (lastId + 1) % 256);
            } catch (Exception e) {
                System.out.println(nextId);
                System.out.println(lastId);
                throw e;
            }
            assertTrue(nextId < 256);
        }
    }


    @Test
    void encodeRadiusPacket() throws IOException {
        AccessRequest request = new AccessRequest(dictionary, 1, authenticator);
        DatagramPacket datagramPacket = RadiusPacketEncoder.toDatagram(request, remoteAddress);

        assertTrue(datagramPacket.content().isReadable());
        assertEquals(remoteAddress, datagramPacket.recipient());
    }

    @Test
    void getRadiusPacketFromDatagram() throws IOException, RadiusException {
        String user = "user1";
        String plaintextPw = "myPassword1";
        String sharedSecret = "sharedSecret1";

        AccessRequest request = new AccessRequest(dictionary, 1, null, user, plaintextPw);
        request.setAuthProtocol(AUTH_PAP);
        AccessRequest encodedRequest = request.encodeRequest(sharedSecret);

        DatagramPacket datagramPacket = RadiusPacketEncoder.toDatagram(encodedRequest, remoteAddress);
        RadiusPacket radiusPacket = RadiusPacketEncoder.fromRequestDatagram(dictionary, datagramPacket, sharedSecret);
        String expectedPlaintextPw = ((AccessRequest) radiusPacket).getUserPassword();

        assertArrayEquals(encodedRequest.getAttribute("User-Password").getData(), radiusPacket.getAttribute("User-Password").getData());
        assertEquals(plaintextPw, expectedPlaintextPw);
        assertEquals(encodedRequest.getAttribute("User-Name").getDataString(), radiusPacket.getAttribute("User-Name").getDataString());
    }

    @Test
    void getRadiusPacketFromResponseDatagram() throws IOException, RadiusException {
        String user = "user2";
        String plaintextPw = "myPassword2";
        String sharedSecret = "sharedSecret2";

        AccessRequest request = new AccessRequest(dictionary, 2, null, user, plaintextPw);
        request.setAuthProtocol(AUTH_PAP);
        AccessRequest encodedRequest = request.encodeRequest(sharedSecret);

        DatagramPacket datagramPacket = RadiusPacketEncoder.toDatagram(encodedRequest, remoteAddress);
        RadiusPacket radiusPacket = RadiusPacketEncoder.fromResponseDatagram(dictionary, datagramPacket, sharedSecret, encodedRequest);
        String expectedPlaintextPw = ((AccessRequest) radiusPacket).getUserPassword();

        assertArrayEquals(encodedRequest.getAttribute("User-Password").getData(), radiusPacket.getAttribute("User-Password").getData());
        assertEquals(plaintextPw, expectedPlaintextPw);
        assertEquals(encodedRequest.getAttribute("User-Name").getDataString(), radiusPacket.getAttribute("User-Name").getDataString());
    }

}