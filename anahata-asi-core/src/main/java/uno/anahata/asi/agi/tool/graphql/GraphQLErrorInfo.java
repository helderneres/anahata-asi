/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Carries a public GraphQL error independently of engine exception types.
 * Messages and classifications are authored by the toolkit's error boundary;
 * this DTO does not sanitize arbitrary strings supplied to its constructor.
 * Paths consist of response field names or aliases and integer list indices,
 * without private resolver sources.
 *
 * <p>The Lombok-generated constructor and getters retain their references;
 * the path is not defensively copied. Producers should supply a non-null
 * message, category and path, using an empty path for operation-level errors.</p>
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
     * Public message already sanitized by the producer, without driver details.
     */
    private final String message;

    /**
     * Portable classification identifying validation, limits or domain failure.
     */
    private final String category;

    /**
     * Response field path, including aliases and zero-based list indices.
     * An empty list represents an error without a field path.
     */
    private final List<Object> path;
}
