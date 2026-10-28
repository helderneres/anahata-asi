/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Contains selected GraphQL data and public errors without engine types.
 * Data and errors are independent: partial data can coexist with field errors,
 * and returning this object does not establish that every field succeeded.
 * Engine, JDBC and IDE handles do not belong in the response graph.
 *
 * <p>The Lombok-generated constructor and getters retain the supplied map and
 * list references rather than copying them. Producers must supply a non-null
 * error list, using an empty list on success. Data may be null for an
 * operation-level rejection or an execution result with no data.</p>
 *
 * @author anahata
 */
@JsonSerialize(using = GraphQLExecutionInfo.ResponseSerializer.class)
@Getter
@RequiredArgsConstructor
public class GraphQLExecutionInfo implements Serializable {

    /**
     * Java serialization identity, independent of the transient engine.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Selected response keys and portable scalar, map or list values.
     * Null denotes absence of operation data; selected fields may also be null.
     */
    private final Map<String, Object> data;

    /**
     * Non-null public errors, empty when no validation or field errors occurred.
     */
    private final List<GraphQLErrorInfo> errors;

    /**
     * Returns a compact UI hint containing data keys and the error count.
     * The hint omits field values and does not replace inspection of both data
     * and errors.
     *
     * @return diagnostic summary, not a success assertion
     */
    @Override
    public String toString() {
        return "GraphQL response: data=" + (data == null ? "null" : data.keySet())
                + "; errors=" + errors.size() + ". Inspect both data and errors.";
    }

    /**
     * Local transport serializer preserving selected nulls even when the shared
     * mapper omits null map values. No serialization policy of other tools
     * changes.
     */
    public static final class ResponseSerializer extends StdSerializer<GraphQLExecutionInfo> {

        /**
         * Creates the serializer for the concrete GraphQL response type.
         */
        public ResponseSerializer() {
            super(GraphQLExecutionInfo.class);
        }

        /**
         * Writes data and errors while preserving explicit nulls in selected data.
         *
         * @param response non-null response with a non-null error list
         * @param generator destination JSON generator, owned by the caller
         * @param provider Jackson provider for error DTOs and remaining scalar values
         * @throws IOException if the generator or a delegated serializer cannot write
         */
        @Override
        public void serialize(GraphQLExecutionInfo response, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeStartObject();
            generator.writeFieldName("data");
            writeSelected(response.data, generator, provider);
            generator.writeFieldName("errors");
            provider.defaultSerializeValue(response.errors, generator);
            generator.writeEndObject();
        }

        /**
         * Writes maps and lists recursively without dropping null entries.
         * Map keys must be strings. Other non-null values are delegated to Jackson;
         * this helper does not itself validate that they are portable public values.
         * It does not close or flush the caller's generator.
         *
         * @param value JSON-style value, including null
         * @param generator destination JSON generator
         * @param provider provider used for values not handled directly
         * @throws IOException if JSON output or delegated serialization fails
         */
        static void writeSelected(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value == null) {
                generator.writeNull();
            } else if (value instanceof Map<?, ?> map) {
                generator.writeStartObject();
                for (var entry : map.entrySet()) {
                    generator.writeFieldName((String) entry.getKey());
                    writeSelected(entry.getValue(), generator, provider);
                }
                generator.writeEndObject();
            } else if (value instanceof List<?> list) {
                generator.writeStartArray();
                for (Object item : list) {
                    writeSelected(item, generator, provider);
                }
                generator.writeEndArray();
            } else {
                provider.defaultSerializeValue(value, generator);
            }
        }
    }
}
