package org.tinyradius.server.handler;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.server.RequestCtx;
import org.tinyradius.server.ServerResponseCtx;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.tinyradius.attribute.Attributes.createAttribute;
import static org.tinyradius.packet.PacketType.ACCOUNTING_REQUEST;
import static org.tinyradius.packet.PacketType.ACCOUNTING_RESPONSE;

@ExtendWith(MockitoExtension.class)
class SimpleAccountingHandlerTest {

    private final Dictionary dictionary = DefaultDictionary.INSTANCE;
    private final SecureRandom random = new SecureRandom();

    private final SimpleAccountingHandler handler = new SimpleAccountingHandler();

    @Mock
    private ChannelHandlerContext ctx;

    @Captor
    private ArgumentCaptor<ServerResponseCtx> responseCaptor;

    @Test
    void unhandledPacketType() throws Exception {
        final RadiusPacket packet = new AccessRequest(dictionary, 1, null);

        final boolean b = handler.acceptInboundMessage(new RequestCtx(packet, null));
        assertFalse(b);
    }

    @Test
    void handlePacket() throws Exception {
        final int id = random.nextInt(256);

        final AccountingRequest request = new AccountingRequest(dictionary, id, null, Arrays.asList(
                createAttribute(dictionary, -1, 33, "state1".getBytes(UTF_8)),
                createAttribute(dictionary, -1, 33, "state2".getBytes(UTF_8))));

        assertEquals(ACCOUNTING_REQUEST, request.getType());
        assertEquals(Arrays.asList("state1", "state2"), request.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

        final RequestCtx requestCtx = new RequestCtx(request, null);

        final boolean b = handler.acceptInboundMessage(requestCtx);
        assertTrue(b);

        handler.channelRead0(ctx, requestCtx);

        verify(ctx).writeAndFlush(responseCaptor.capture());

        final RadiusPacket response = responseCaptor.getValue().getResponse();

        assertEquals(id, response.getIdentifier());
        assertEquals(ACCOUNTING_RESPONSE, response.getType());
        assertEquals(Arrays.asList("state1", "state2"), response.getAttributes().stream()
                .map(RadiusAttribute::getValue)
                .map(String::new)
                .collect(Collectors.toList()));

    }
}