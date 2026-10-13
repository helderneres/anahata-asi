/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import lombok.Getter;

/**
 * A resolver failure whose explicitly authored message is safe to return. Never
 * pass driver exception messages, connection URLs or credentials here.
 *
 * @author anahata
 */
@Getter
public class SafeGraphQLException extends RuntimeException {

    /**
     * Java serialization identity of the local failure.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Portable domain classification without an internal exception type.
     */
    private final String category;

    /**
     * Creates a field failure with an actionable, non-sensitive message.
     *
     * @param safeMessage An authored message, never a raw exception message.
     */
    public SafeGraphQLException(String safeMessage) {
        this("FieldUnavailable", safeMessage);
    }

    /**
     * Creates an actionable failure with a stable domain classification.
     *
     * @param category Public classification such as ConnectionInactive.
     * @param safeMessage An authored message, never a raw exception message.
     */
    public SafeGraphQLException(String category, String safeMessage) {
        super(safeMessage);
        this.category = category;
    }
}
