/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Carries a public GraphQL error independently of engine exception types.
 * Messages and classifications are authored by the toolkit boundary; this DTO
 * does not sanitize arbitrary strings supplied to its constructor. Structural
 * diagnostics retain field identifiers and line/column numbers, without copying
 * variable values, driver messages, source names or stack traces.
 *
 * <p>
 * Paths use field names or aliases and integer indices. An empty path denotes
 * an operation-level error. The generated constructor retains its collection
 * references, so producers must supply stable collections. A null effects value
 * deliberately makes no assertion about command effects or rollback.</p>
 *
 * @author anahata
 */
@Getter
@RequiredArgsConstructor
public class GraphQLErrorInfo implements Serializable {

    /**
     * Java serialization identity for the public response model.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Public message already sanitized by the producer.
     */
    private final String message;

    /**
     * Portable category such as ValidationError, LimitExceeded or domain
     * failure.
     */
    private final String category;

    /**
     * Response or validation path; empty when no field path is available.
     */
    private final List<Object> path;

    /**
     * Validation subtype, such as SubselectionRequired, or the public category.
     */
    private final String type;

    /**
     * Last field identifier on the path, or null for an operation-level error.
     */
    private final String field;

    /**
     * Source coordinates containing only positive line and column numbers.
     */
    private final List<Map<String, Integer>> locations;

    /**
     * NOT_STARTED only when the producer proves rejection before resolver
     * effects; null when no such assertion can be made.
     */
    private final String effects;

    /**
     * Creates an error without additional structural or effects assertions.
     *
     * @param message already safe public message
     * @param category public classification
     * @param path stable response path, or an empty list
     */
    public GraphQLErrorInfo(String message, String category, List<Object> path) {
        this(message, category, path, category, null, List.of(), null);
    }
}
