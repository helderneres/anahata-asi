/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Public discovery attributes of one connection registered in the IDE.
 * <p>
 * This snapshot describes registration and observed connection state, not an
 * open JDBC resource owned by the caller. URL, user and password are not
 * stored here. Child metadata and table fields use a private IDE handle;
 * reading or modifying this DTO does not connect, disconnect or reconfigure
 * the registered connection.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class DatabaseConnectionInfo {

    /**
     * Opaque hash-derived identifier of the registry identity. It is a lookup selector, not a
     * credential or authorization token; changing the registry identity can change it.
     */
    private String id;
    /**
     * Friendly IDE label, or a replacement label when the registered name matches the
     * credential/JDBC patterns suppressed by discovery. May be {@code null} if the driver or
     * registry supplies none.
     */
    private String displayName;
    /**
     * JDBC driver implementation class name, or {@code null} when no driver descriptor is
     * available.
     */
    private String driverClass;
    /**
     * Default schema reported by the IDE registration, possibly {@code null}; not a
     * verification of the active connection's current schema.
     */
    private String schema;
    /**
     * Whether the registry exposed a non-null JDBC connection at discovery time; no network
     * liveness check is performed.
     */
    private boolean connected;
}
