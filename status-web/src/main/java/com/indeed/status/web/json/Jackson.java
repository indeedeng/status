package com.indeed.status.web.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/** @author matts */
public class Jackson {
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    public static String prettyPrint(final Object o) throws IOException {
        return prettyPrint(o, DEFAULT_MAPPER);
    }

    public static String prettyPrint(final Object o, final ObjectMapper mapper) throws IOException {
        final Writer sw = new StringWriter();
        prettyPrint(sw, o, mapper);
        sw.close();

        return sw.toString();
    }

    public static void prettyPrint(final Writer out, final Object o, final ObjectMapper mapper)
            throws IOException {
        final JsonFactory factory = mapper.getJsonFactory();
        final JsonGenerator generator = factory.createJsonGenerator(out).useDefaultPrettyPrinter();
        mapper.writeValue(generator, o);
    }
}
