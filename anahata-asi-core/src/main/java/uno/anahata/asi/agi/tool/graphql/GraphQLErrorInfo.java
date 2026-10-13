/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Library-independent information about a failed GraphQL operation or field.
 * Paths contain response field names and list indices, never internal sources.
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
     * Sanitized message suitable for inclusion in the agent context.
     */
    private final String message;

    /**
     * Stable error classification represented as a plain string.
     */
    private final String category;

    /**
     * Response path, empty for errors that occur before field execution.
     */
    private final List<Object> path;
}
