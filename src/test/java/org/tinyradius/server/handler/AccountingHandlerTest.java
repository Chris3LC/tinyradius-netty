package org.tinyradius.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.PacketEncoder;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusException;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.tinyradius.attribute.Attributes.createAttribute;
import static org.tinyradius.packet.PacketType.ACCOUNTING_REQUEST;
import static org.tinyradius.packet.PacketType.ACCOUNTING_RESPONSE;

@ExtendWith(MockitoExtension.class)
class AccountingHandlerTest {

    private final Dictionary dictionary = DefaultDictionary.INSTANCE;
    private final PacketEncoder packetEncoder = new PacketEncoder(dictionary);
    private final SecureRandom random = new SecureRandom();

    @Mock
    private ChannelHandlerContext ctx;

    @Test
    void unhandledPacketType() throws RadiusException {
        final String secret = "mySecret";
        final RadiusPacket radiusPacket = new AccountingRequest(dictionary, 1, null).encodeRequest(secret);
        final DatagramPacket datagramPacket = packetEncoder.toDatagram(radiusPacket, new InetSocketAddress(0));

        final ServerPacketCodec serverPacketCodec = new ServerPacketCodec(packetEncoder, address -> secret);

        final RadiusException exception = assertThrows(RadiusException.class,
                () -> serverPacketCodec.decode(null, datagramPacket));

        assertTrue(exception.getMessage().toLowerCase().contains("handler only accepts accessrequest"));
    }

    @Test
    void handlePacket() {
        final int id = random.nextInt(256);
        final NioEventLoopGroup eventExecutors = new NioEventLoopGroup(4);

        final NioDatagramChannel datagramChannel = new NioDatagramChannel();
        eventExecutors.register(datagramChannel).syncUninterruptibly();

        final AccountingRequest request = new AccountingRequest(dictionary, id, null, Arrays.asList(
                createAttribute(dictionary, -1, 33, "state1".getBytes(UTF_8)),
                createAttribute(dictionary, -1, 33, "state2".getBytes(UTF_8))));

        assertEquals(ACCOUNTING_REQUEST, request.getType());
        assertEquals(Arrays.asList("state1", "state2"), request.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

        final RadiusPacket response = new AcctHandler().handlePacket(datagramChannel, request, null, a -> "")
                .syncUninterruptibly().getNow();

        assertEquals(id, response.getIdentifier());
        assertEquals(ACCOUNTING_RESPONSE, response.getType());
        assertEquals(Arrays.asList("state1", "state2"), response.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

        eventExecutors.shutdownGracefully().syncUninterruptibly();
    }
}