package org.tinyradius.dictionary;

import org.tinyradius.attribute.util.AttributeType;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Byte.toUnsignedInt;

/**
 * A dictionary that keeps the values and names in hash maps
 * in the memory. The dictionary has to be filled using the
 * methods <code>addAttributeType</code> and
 * <code>addVendor</code>.
 */
public class MemoryDictionary implements WritableDictionary {

    private final Map<Integer, String> vendorsByCode = new HashMap<>();
    private final Map<Integer, Map<Byte, AttributeType>> attributesByCode = new HashMap<>();
    private final Map<String, AttributeType> attributesByName = new HashMap<>();

    /**
     * Returns the specified AttributeType object.
     *
     * @param vendorCode vendor ID or -1 for "no vendor"
     * @param type   attribute type code
     * @return AttributeType or null
     */
    @Override
    public AttributeType getAttributeTypeByCode(int vendorCode, byte type) {
        Map<Byte, AttributeType> vendorAttributes = attributesByCode.get(vendorCode);
        return vendorAttributes == null ?
                null : vendorAttributes.get(type);
    }

    /**
     * Retrieves the attribute type with the given name.
     *
     * @param typeName name of the attribute type
     * @return AttributeType or null
     */
    public AttributeType getAttributeTypeByName(String typeName) {
        return attributesByName.get(typeName);
    }

    /**
     * Searches the vendor with the given name and returns its
     * code. This method is seldomly used.
     *
     * @param vendorName vendor name
     * @return vendor code or -1
     */
    public int getVendorId(String vendorName) {
        for (Map.Entry<Integer, String> v : vendorsByCode.entrySet()) {
            if (v.getValue().equals(vendorName))
                return v.getKey();
        }
        return -1;
    }

    /**
     * Retrieves the name of the vendor with the given code from
     * the cache.
     *
     * @param vendorId vendor number
     * @return vendor name or null
     */
    public String getVendorName(int vendorId) {
        return vendorsByCode.get(vendorId);
    }

    /**
     * Adds the given vendor to the cache.
     *
     * @param vendorId   vendor ID
     * @param vendorName name of the vendor
     * @throws IllegalArgumentException empty vendor name, invalid vendor ID
     */
    public void addVendor(int vendorId, String vendorName) {
        if (vendorId < 0)
            throw new IllegalArgumentException("Vendor ID must be positive");
        if (getVendorName(vendorId) != null)
            throw new IllegalArgumentException("Duplicate vendor code");
        if (vendorName == null || vendorName.isEmpty())
            throw new IllegalArgumentException("Vendor name empty");
        vendorsByCode.put(vendorId, vendorName);
    }

    /**
     * Adds an AttributeType object to the cache.
     *
     * @param attributeType AttributeType object
     * @throws IllegalArgumentException duplicate attribute name/type code
     */
    public void addAttributeType(AttributeType attributeType) {
        if (attributeType == null)
            throw new IllegalArgumentException("Attribute type must not be null");

        int vendorId = attributeType.getVendorId();
        byte typeCode = attributeType.getType();
        String attributeName = attributeType.getName();

        if (attributesByName.containsKey(attributeName))
            throw new IllegalArgumentException("Duplicate attribute name: " + attributeName);

        Map<Byte, AttributeType> vendorAttributes = attributesByCode
                .computeIfAbsent(vendorId, k -> new HashMap<>());
        if (vendorAttributes.containsKey(typeCode))
            throw new IllegalArgumentException("Duplicate type code: " + toUnsignedInt(typeCode));

        attributesByName.put(attributeName, attributeType);
        vendorAttributes.put(typeCode, attributeType);
    }
}
