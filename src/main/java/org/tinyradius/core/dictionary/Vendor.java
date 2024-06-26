package org.tinyradius.core.dictionary;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.nio.ByteBuffer;

/**
 * Vendor definition
 */
@Getter
@EqualsAndHashCode
public class Vendor {

    private final int id;
    private final String name;
    private final int typeSize;
    private final int lengthSize;

    /**
     * @param id         Vendor ID
     * @param name       Vendor Name
     * @param typeSize   number of octets for vendor 'type' field
     * @param lengthSize number of octets for vendor 'length' field
     */
    public Vendor(int id, @NonNull String name, int typeSize, int lengthSize) {
        this.id = id;
        this.name = name;
        this.typeSize = typeSize;
        this.lengthSize = lengthSize;

        if (id < 0)
            throw new IllegalArgumentException("Vendor ID must be positive: " + id + " (" + name + ")");
        if (name.isEmpty())
            throw new IllegalArgumentException("Vendor name empty: " + name + " (vendorId" + id + ")");
        if (typeSize != 1 && typeSize != 2 && typeSize != 4)
            throw new IllegalArgumentException("Vendor typeSize must be 1, 2, or 4");
        if (lengthSize != 0 && lengthSize != 1 && lengthSize != 2)
            throw new IllegalArgumentException("Vendor lengthSize must be 0, 1, or 2");
    }

    public int getHeaderSize() {
        return typeSize + lengthSize;
    }

    public byte[] toTypeBytes(int type) {
        switch (typeSize) {
            case 2:
                return ByteBuffer.allocate(Short.BYTES).putShort((short) type).array();
            case 4:
                return ByteBuffer.allocate(Integer.BYTES).putInt(type).array();
            case 1:
            default:
                return new byte[]{(byte) type};
        }
    }

    public byte[] toLengthBytes(int len) {
        switch (lengthSize) {
            case 0:
                return new byte[0];
            case 2:
                return ByteBuffer.allocate(Short.BYTES).putShort((short) len).array();
            case 1:
            default:
                return new byte[]{(byte) len};
        }
    }

    @Override
    public String toString() {
        return "Vendor{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", typeSize=" + typeSize +
                ", lengthSize=" + lengthSize +
                '}';
    }
}
