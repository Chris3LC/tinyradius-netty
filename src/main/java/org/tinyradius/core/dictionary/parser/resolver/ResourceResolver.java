package org.tinyradius.core.dictionary.parser.resolver;

import java.io.IOException;
import java.io.InputStream;

public interface ResourceResolver {

    String resolve(String currentResource, String nextResource);

    InputStream openStream(String resource) throws IOException;
}
