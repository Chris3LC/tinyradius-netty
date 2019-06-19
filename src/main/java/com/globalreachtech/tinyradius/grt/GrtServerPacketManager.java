package com.globalreachtech.tinyradius.grt;

import com.globalreachtech.tinyradius.RadiusServer;
import com.globalreachtech.tinyradius.packet.RadiusPacket;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class GrtServerPacketManager implements RadiusServer.PacketManager {

    private RadiusQueue<ReceivedPacket> receivedPackets = new RadiusQueue<>();
    private long duplicateInterval = 30000; // 30 s  todo setters

    @Override
    public boolean isPacketDuplicate(RadiusPacket packet, InetSocketAddress address) {
        long now = System.currentTimeMillis();
        long intervalStart = now - duplicateInterval;

        byte[] authenticator = packet.getAuthenticator();
        for (ReceivedPacket p : receivedPackets.get(packet.getPacketIdentifier())) {
            if (p.receiveTime < intervalStart) {
                // packet is older than duplicate interval
                receivedPackets.remove(p, p.packetIdentifier);
            } else {
                if (p.address.equals(address)) {
                    if (authenticator != null && p.authenticator != null) {
                        // packet is duplicate if stored authenticator is equal
                        // to the packet authenticator
                        return Arrays.equals(p.authenticator, authenticator);
                    } else {
                        // should not happen, packet is duplicate
                        return true;
                    }
                }
            }
        }

        // add packet to receive list
        ReceivedPacket rp = new ReceivedPacket();
        rp.address = address;
        rp.packetIdentifier = packet.getPacketIdentifier();
        rp.receiveTime = now;
        rp.authenticator = authenticator;
        receivedPackets.add(rp, rp.packetIdentifier);

        return false;
    }

    /**
     * This internal class represents a packet that has been received by
     * the server.
     */
    class ReceivedPacket {

        /**
         * The identifier of the packet.
         */
        public int packetIdentifier;

        /**
         * The time the packet was received.
         */
        public long receiveTime;

        /**
         * The address of the host who sent the packet.
         */
        public InetSocketAddress address;

        /**
         * Authenticator of the received packet.
         */
        public byte[] authenticator;
    }
}
