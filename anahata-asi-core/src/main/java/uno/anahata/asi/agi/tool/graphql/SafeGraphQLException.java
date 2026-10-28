/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import lombok.Getter;

/**
 * Marks an explicitly authored resolver message as safe for public output.
 * The GraphQL error boundary preserves this message and category instead of
 * replacing them with a generic failure. This class performs no redaction:
 * callers must never supply raw driver messages, SQL, connection URLs,
 * credentials or implementation stack traces.
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
     * Creates a public field failure classified as {@code FieldUnavailable}.
     *
     * @param safeMessage actionable message authored for public disclosure
     */
    public SafeGraphQLException(String safeMessage) {
        this("FieldUnavailable", safeMessage);
    }

    /**
     * Creates a public failure with an application-defined classification.
     * Neither the category nor message is validated or sanitized here.
     *
     * @param category non-sensitive public classification, such as ConnectionInactive
     * @param safeMessage actionable message authored for public disclosure
     */
    public SafeGraphQLException(String category, String safeMessage) {
        super(safeMessage);
        this.category = category;
    }
}
