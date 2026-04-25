/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a registered database connection in the IDE.
 * <p>
 * This DTO decouples the ASI from the internal NetBeans
 * {@code DatabaseConnection} class, providing a clean, JSON-serializable
 * structure for the context window.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class DatabaseConnectionInfo {

    private String displayName;
    private String databaseUrl;
    private String driverClass;
    private String user;
    private String schema;
    private boolean connected;
}
