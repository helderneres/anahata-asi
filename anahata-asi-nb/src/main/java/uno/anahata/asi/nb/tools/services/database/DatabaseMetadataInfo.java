/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the metadata of a database server.
 * <p>
 * Extracts and serializes key information from {@link java.sql.DatabaseMetaData}
 * to provide the ASI with context about the target database dialect and capabilities.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class DatabaseMetadataInfo {
    private String databaseProductName;
    private String databaseProductVersion;
    private String driverName;
    private String driverVersion;
}
