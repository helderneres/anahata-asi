/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.JDBCDriver;
import uno.anahata.asi.agi.message.RagMessage;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.AgiToolException;
import uno.anahata.asi.agi.tool.AgiToolkit;
import uno.anahata.asi.agi.tool.AnahataToolkit;

/**
 * Database Integration Toolkit.
 * <p>
 * Provides comprehensive database interaction capabilities, allowing the ASI to
 * list connections, inspect schemas, and execute queries natively within the
 * IDE.
 * </p>
 *
 * @author anahata
 */
@AgiToolkit("Provides database connections, schema inspection, and SQL execution capabilities.")
public class Database extends AnahataToolkit {

    @Override
    public void populateMessage(RagMessage message) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("## Database Connections\n");
        DatabaseConnection[] connections = ConnectionManager.getDefault().getConnections();
        if (connections == null || connections.length == 0) {
            sb.append("- No database connections registered.\n");
        } else {
            for (DatabaseConnection conn : connections) {
                boolean connected = conn.getJDBCConnection() != null;
                sb.append("- **").append(conn.getDisplayName()).append("** ")
                        .append("(Connected: ").append(connected).append(")\n")
                        .append("  URL: ").append(conn.getDatabaseURL()).append("\n");
            }
        }
        message.addTextPart(sb.toString());
    }

    private DatabaseConnection findConnection(String name) throws AgiToolException {
        DatabaseConnection[] connections = ConnectionManager.getDefault().getConnections();
        if (connections != null) {
            for (DatabaseConnection conn : connections) {
                if (conn.getDisplayName().equals(name)) {
                    return conn;
                }
            }
        }
        throw new AgiToolException("Connection not found: " + name);
    }

    private Connection getActiveConnection(String name) throws AgiToolException {
        DatabaseConnection dbConn = findConnection(name);
        Connection jdbcConn = dbConn.getJDBCConnection();
        if (jdbcConn == null) {
            // Force connection via IDE API if available, but for ASI programmatic access
            // it's usually better to instruct the user to connect via UI, or we try to show dialog.
            ConnectionManager.getDefault().showConnectionDialog(dbConn);
            jdbcConn = dbConn.getJDBCConnection();
            if (jdbcConn == null) {
                throw new AgiToolException("Failed to establish connection to: " + name + ". Ensure credentials are saved or connect via IDE Services tab first.");
            }
        }
        return jdbcConn;
    }

    /**
     * Lists all available database connections registered in the IDE.
     *
     * @return a list of connection info DTOs
     */
    @AgiTool("Lists all available database connections registered in the IDE.")
    public List<DatabaseConnectionInfo> listConnections() {
        DatabaseConnection[] connections = ConnectionManager.getDefault().getConnections();
        List<DatabaseConnectionInfo> result = new ArrayList<>();
        if (connections != null) {
            for (DatabaseConnection conn : connections) {
                JDBCDriver driver = conn.getJDBCDriver();
                result.add(DatabaseConnectionInfo.builder()
                        .displayName(conn.getDisplayName())
                        .databaseUrl(conn.getDatabaseURL())
                        .driverClass(driver != null ? driver.getClassName() : "Unknown")
                        .user(conn.getUser())
                        .schema(conn.getSchema())
                        .connected(conn.getJDBCConnection() != null)
                        .build());
            }
        }
        return result;
    }

    /**
     * Connects to a specific database registered in the IDE.
     *
     * @param connectionName the display name of the connection
     * @return a success message
     * @throws AgiToolException if the connection fails
     */
    @AgiTool("Connects to a specific database registered in the IDE.")
    public String connect(String connectionName) throws AgiToolException {
        DatabaseConnection dbConn = findConnection(connectionName);
        if (dbConn.getJDBCConnection() != null) {
            return "Already connected to " + connectionName;
        }
        ConnectionManager.getDefault().showConnectionDialog(dbConn);
        if (dbConn.getJDBCConnection() != null) {
            return "Successfully connected to " + connectionName;
        }
        throw new AgiToolException("Could not connect to " + connectionName);
    }

    /**
     * Disconnects from a specific database.
     *
     * @param connectionName the display name of the connection
     * @return a success message
     * @throws AgiToolException if the disconnection fails
     */
    @AgiTool("Disconnects from a specific database.")
    public String disconnect(String connectionName) throws AgiToolException {
        DatabaseConnection dbConn = findConnection(connectionName);
        if (dbConn.getJDBCConnection() == null) {
            return "Already disconnected from " + connectionName;
        }
        ConnectionManager.getDefault().disconnect(dbConn);
        return "Successfully disconnected from " + connectionName;
    }

    /**
     * Retrieves the metadata for a specific database connection.
     *
     * @param connectionName the display name of the connection
     * @return the database metadata DTO
     * @throws AgiToolException if metadata extraction fails
     */
    @AgiTool("Retrieves the metadata for a specific database connection (e.g. Server version, product name).")
    public DatabaseMetadataInfo getMetadata(String connectionName) throws AgiToolException {
        try {
            Connection conn = getActiveConnection(connectionName);
            DatabaseMetaData metaData = conn.getMetaData();
            return DatabaseMetadataInfo.builder()
                    .databaseProductName(metaData.getDatabaseProductName())
                    .databaseProductVersion(metaData.getDatabaseProductVersion())
                    .driverName(metaData.getDriverName())
                    .driverVersion(metaData.getDriverVersion())
                    .build();
        } catch (SQLException ex) {
            throw new AgiToolException("Failed to get metadata: " + ex.getMessage());
        }
    }

    /**
     * Lists all tables and views for a specific connection and schema.
     *
     * @param connectionName the display name of the connection
     * @param schema the schema pattern, or null for all schemas
     * @return a list of tables and views
     * @throws AgiToolException if extraction fails
     */
    @AgiTool("Lists all tables and views for a specific connection and schema.")
    public List<TableInfo> listTables(String connectionName, String schema) throws AgiToolException {
        try {
            Connection conn = getActiveConnection(connectionName);
            DatabaseMetaData metaData = conn.getMetaData();
            List<TableInfo> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(TableInfo.builder()
                            .tableName(rs.getString("TABLE_NAME"))
                            .tableType(rs.getString("TABLE_TYPE"))
                            .schema(rs.getString("TABLE_SCHEM"))
                            .remarks(rs.getString("REMARKS"))
                            .build());
                }
            }
            return tables;
        } catch (SQLException ex) {
            throw new AgiToolException("Failed to list tables: " + ex.getMessage());
        }
    }

    /**
     * Retrieves the column definitions for a specific table.
     *
     * @param connectionName the display name of the connection
     * @param tableName the exact table name
     * @return a list of column definitions
     * @throws AgiToolException if extraction fails
     */
    @AgiTool("Retrieves the column definitions (schema) for a specific table.")
    public List<ColumnInfo> getTableColumns(String connectionName, String tableName) throws AgiToolException {
        try {
            Connection conn = getActiveConnection(connectionName);
            DatabaseMetaData metaData = conn.getMetaData();
            List<ColumnInfo> columns = new ArrayList<>();

            // Get Primary Keys
            List<String> pkColumns = new ArrayList<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String isAutoIncrementStr = rs.getString("IS_AUTOINCREMENT");
                    columns.add(ColumnInfo.builder()
                            .columnName(colName)
                            .dataType(rs.getString("TYPE_NAME"))
                            .columnSize(rs.getInt("COLUMN_SIZE"))
                            .isNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")))
                            .isPrimaryKey(pkColumns.contains(colName))
                            .isAutoIncrement("YES".equalsIgnoreCase(isAutoIncrementStr))
                            .build());
                }
            }
            return columns;
        } catch (SQLException ex) {
            throw new AgiToolException("Failed to get columns for table " + tableName + ": " + ex.getMessage());
        }
    }

    /**
     * Executes a SQL SELECT query and returns the results.
     *
     * @param connectionName the display name of the connection
     * @param sql the SELECT query to execute
     * @param maxRows the maximum number of rows to return (default 100, max
     * 1000)
     * @return the query result DTO
     * @throws AgiToolException if execution fails
     */
    @AgiTool("Executes a SQL SELECT query and returns the results. Safe for ASI context.")
    public QueryResult executeQuery(String connectionName, String sql, Integer maxRows) throws AgiToolException {
        int limit = (maxRows != null && maxRows > 0) ? Math.min(maxRows, 1000) : 100;
        long startTime = System.currentTimeMillis();
        try {
            Connection conn = getActiveConnection(connectionName);
            try (Statement stmt = conn.createStatement()) {
                stmt.setMaxRows(limit);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int colCount = rsMeta.getColumnCount();

                    List<String> headers = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        headers.add(rsMeta.getColumnName(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(rsMeta.getColumnName(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }

                    return QueryResult.builder()
                            .headers(headers)
                            .rows(rows)
                            .rowCount(rows.size())
                            .executionTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                }
            }
        } catch (SQLException ex) {
            throw new AgiToolException("Query execution failed: " + ex.getMessage());
        }
    }

    /**
     * Executes a SQL DML/DDL statement (INSERT, UPDATE, DELETE, CREATE).
     *
     * @param connectionName the display name of the connection
     * @param sql the statement to execute
     * @return the statement result DTO
     * @throws AgiToolException if execution fails
     */
    @AgiTool("Executes a SQL DML/DDL statement (INSERT, UPDATE, DELETE, CREATE, ALTER).")
    public StatementResult executeStatement(String connectionName, String sql) throws AgiToolException {
        long startTime = System.currentTimeMillis();
        try {
            Connection conn = getActiveConnection(connectionName);
            try (Statement stmt = conn.createStatement()) {
                int affectedRows = stmt.executeUpdate(sql);
                return StatementResult.builder()
                        .message("Statement executed successfully.")
                        .affectedRows(affectedRows)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }
        } catch (SQLException ex) {
            throw new AgiToolException("Statement execution failed: " + ex.getMessage());
        }
    }
}
