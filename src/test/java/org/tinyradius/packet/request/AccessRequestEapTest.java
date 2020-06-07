package org.tinyradius.packet.request;

import org.junit.jupiter.api.Test;
import org.tinyradius.attribute.util.Attributes;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.util.RadiusPacketException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.tinyradius.packet.request.AccessRequest.EAP_MESSAGE;
import static org.tinyradius.packet.util.MessageAuthSupport.MESSAGE_AUTHENTICATOR;

class AccessRequestEapTest {

    private static final Dictionary dictionary = DefaultDictionary.INSTANCE;

    @Test
    void encodeVerify() throws RadiusPacketException {
        String sharedSecret = "sharedSecret1";
        final AccessRequestEap accessRequestEap = new AccessRequestEap(dictionary, (byte) 1, null,
                Collections.singletonList(Attributes.create(dictionary, -1, EAP_MESSAGE, new byte[16])));

        final AccessRequestEap encoded = accessRequestEap.encodeRequest(sharedSecret);

        assertNotNull(encoded.getAuthenticator());
        encoded.verifyRequest(sharedSecret);
    }

    @Test
    void verifyAttributeCount() throws RadiusPacketException {
        String sharedSecret = "sharedSecret1";
        final AccessRequestEap request = new AccessRequestEap(dictionary, (byte) 1, new byte[16], Collections.emptyList());
        assertThrows(RadiusPacketException.class, () -> request.verifyRequest(sharedSecret));

        final AccessRequestEap request1 = request.addAttribute(Attributes.create(dictionary, -1, EAP_MESSAGE, new byte[16]));
        assertThrows(RadiusPacketException.class, () -> request1.verifyRequest(sharedSecret));

        // add one messageAuth
        final AccessRequestEap request2 = request1.encodeRequest(sharedSecret);
        request2.verifyRequest(sharedSecret);

        final AccessRequestEap request3 = request2.addAttribute(Attributes.create(dictionary, -1, MESSAGE_AUTHENTICATOR, new byte[16]));
        final RadiusPacketException e = assertThrows(RadiusPacketException.class, () -> request3.verifyRequest(sharedSecret));
        assertTrue(e.getMessage().toLowerCase().contains("at most one message-authenticator"));
    }
}
