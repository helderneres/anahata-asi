/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import uno.anahata.asi.agi.tool.graphql.SafeGraphQLException;

/**
 * Resolves exact IDE connection identities shared by discovery and commands.
 * <p>
 * No connection is opened and no alternative registration is substituted. Opaque
 * ids avoid publishing registry identity strings, but are identifiers rather than
 * credentials or authorization tokens. No registry snapshot is kept between calls.
 * </p>
 */
final class DatabaseConnections {

    /**
     * Prevents construction of this stateless identity helper.
     */
    private DatabaseConnections() {
    }

    /**
     * Derives an opaque id from the IDE registry name using SHA-256.
     * <p>
     * The display name is used only when the registry name is absent. The id is stable
     * while that chosen identity is unchanged; it is not a database-global id or secret.
     * </p>
     *
     * @param connection the registered IDE handle; must not be {@code null}
     * @return {@code db-} followed by a lowercase SHA-256 hexadecimal digest
     * @throws IllegalStateException if the JVM does not provide SHA-256
     */
    static String id(DatabaseConnection connection) {
        String identity = connection.getName();
        if (identity == null) {
            identity = connection.getDisplayName();
        }
        try {
            return "db-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Objects.toString(identity, "unnamed").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by the JVM", failure);
        }
    }

    /**
     * Returns a discovery label while suppressing recognized credential-like names.
     * <p>
     * JDBC URL markers and common user/password assignments are replaced with a generic
     * label derived from the opaque id. This narrow filter does not redact arbitrary
     * database content.
     * </p>
     *
     * @param connection the registered IDE handle; must not be {@code null}
     * @return the original label, possibly {@code null}, or a generic replacement
     */
    static String displayName(DatabaseConnection connection) {
        String name = connection.getDisplayName();
        if (name != null && (name.toLowerCase(java.util.Locale.ROOT).contains("jdbc:")
                || name.matches("(?is).*\\b(password|passwd|username|user)\\s*=.*"))) {
            return "Registered connection " + id(connection).substring(3, 15);
        }
        return name;
    }

    /**
     * Finds one registration by opaque id or exact original display name.
     * <p>
     * Both forms are matched case-sensitively against the current registry. An inactive
     * match is returned without opening it. Missing and ambiguous matches fail instead
     * of falling back to another destination.
     * </p>
     *
     * @param selector a nonblank id or exact name of at most 2048 characters
     * @return the matched IDE-owned handle without connecting it
     * @throws SafeGraphQLException if the selector is invalid, missing, or ambiguous
     */
    static DatabaseConnection find(String selector) {
        if (selector == null || selector.isBlank() || selector.length() > 2048) {
            throw new SafeGraphQLException("InvalidArgument", "Provide one connection id or exact registered display name.");
        }
        DatabaseConnection found = null;
        DatabaseConnection[] registered = ConnectionManager.getDefault().getConnections();
        if (registered != null) {
            for (DatabaseConnection candidate : registered) {
                if (selector.equals(id(candidate)) || selector.equals(candidate.getDisplayName())) {
                    if (found != null) {
                        throw new SafeGraphQLException("ConnectionAmbiguous", "Several connections match this identity. Use a unique connection id or rename the registrations.");
                    }
                    found = candidate;
                }
            }
        }
        if (found == null) {
            throw new SafeGraphQLException("ConnectionNotFound", "The registered connection was not found. Discover connections again; no alternative destination was selected.");
        }
        return found;
    }
}
