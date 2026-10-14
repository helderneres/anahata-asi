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
 * Exact IDE identities shared by discovery and commands, without fallback.
 */
final class DatabaseConnections {

    private DatabaseConnections() {
    }

    /**
     * Hashes the registry identity; no URL, user or password is published.
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
     * Keeps friendly names but suppresses labels containing JDBC credentials.
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
     * Finds one opaque id or exact display name, rejecting missing/duplicate
     * matches.
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
