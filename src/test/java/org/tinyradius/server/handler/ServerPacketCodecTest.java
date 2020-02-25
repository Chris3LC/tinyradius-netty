package org.tinyradius.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.PacketCodec;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.server.RequestCtx;
import org.tinyradius.util.RadiusPacketException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerPacketCodecTest {

    private final Dictionary dictionary = DefaultDictionary.INSTANCE;
    private final PacketCodec packetCodec = new PacketCodec(dictionary);

    private final InetSocketAddress address = new InetSocketAddress(0);

    @Mock
    private ChannelHandlerContext ctx;

    @Test
    void decodeUnknownSecret() {
        final ServerPacketCodec codec = new ServerPacketCodec(packetCodec, address -> null);
        final DatagramPacket datagram = new DatagramPacket(Unpooled.buffer(0), address);

        final List<Object> out = new ArrayList<>();
        codec.decode(ctx, datagram, out);

        assertEquals(0, out.size());
    }

    @Test
    void decodeExceptionDropPacket() throws RadiusPacketException {
        final RadiusPacket request = new RadiusPacket(dictionary, 4, 1).encodeRequest("mySecret");
        final DatagramPacket datagram = packetCodec.toDatagram(request, address);
        final ServerPacketCodec codec = new ServerPacketCodec(packetCodec, x -> "bad secret");

        final List<Object> out1 = new ArrayList<>();
        codec.decode(ctx, datagram, out1);

        assertEquals(0, out1.size());
    }

    @Test
    void decodeEncodeSuccess() throws RadiusPacketException {
        final String secret = "mySecret";
        final ServerPacketCodec codec = new ServerPacketCodec(packetCodec, address -> secret);
        when(ctx.channel()).thenReturn(mock(Channel.class));

        // create datagram
        final RadiusPacket requestPacket = new RadiusPacket(dictionary, 3, 1).encodeRequest(secret);
        final InetSocketAddress remoteAddress = new InetSocketAddress(123);
        final DatagramPacket request = packetCodec.toDatagram(requestPacket, address, remoteAddress);

        // decode
        final ArrayList<Object> out1 = new ArrayList<>();
        codec.decode(ctx, request, out1);
        assertEquals(1, out1.size());

        // check decoded
        final RequestCtx requestCtx = (RequestCtx) out1.get(0);
        assertEquals(remoteAddress, requestCtx.getEndpoint().getAddress());
        assertEquals(secret, requestCtx.getEndpoint().getSecret());
        assertEquals(requestPacket, requestCtx.getRequest());

        final RadiusPacket responsePacket = new RadiusPacket(dictionary, 4, 1);

        // encode
        final List<Object> out2 = new ArrayList<>();
        codec.encode(ctx, requestCtx.withResponse(responsePacket), out2);
        assertEquals(1, out2.size());

        // check encoded
        final DatagramPacket response = (DatagramPacket) out2.get(0);
        assertArrayEquals(response.content().array(),
                packetCodec.toDatagram(responsePacket.encodeResponse(secret, requestPacket.getAuthenticator()), remoteAddress, address).content().array());
    }
}