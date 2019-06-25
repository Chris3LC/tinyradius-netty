package org.tinyradius.dictionary;

import org.tinyradius.attribute.RadiusAttribute;

/**
 * A dictionary that is not read-only. Provides methods
 * to add entries to the dictionary.
 */
public interface WritableDictionary extends Dictionary {

    /**
     * Adds the given vendor to the dictionary.
     *
     * @param vendorId   vendor ID
     * @param vendorName name of the vendor
     */
    void addVendor(int vendorId, String vendorName);

    /**
     * Adds an AttributeType object to the dictionary.
     *
     * @param attributeType AttributeType object
     */
    void addAttributeType(AttributeType<? extends RadiusAttribute> attributeType);

}
