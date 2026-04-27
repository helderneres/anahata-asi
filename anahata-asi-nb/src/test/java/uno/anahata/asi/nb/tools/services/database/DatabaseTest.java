/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.JDBCDriver;
import uno.anahata.asi.agi.tool.AgiToolException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import uno.anahata.asi.nb.test.AsiToolTestBase;

/**
 * Unit tests for the {@link Database} toolkit.
 * <p>
 * This class uses Mockito to simulate NetBeans Database Explorer APIs and JDBC
 * interfaces, ensuring the toolkit correctly transforms IDE objects and SQL
 * results into Anahata DTOs.
 * </p>
 */
public class DatabaseTest extends AsiToolTestBase {

    private Database databaseToolkit;
    private MockedStatic<ConnectionManager> mockedConnectionManager;
    private ConnectionManager mockManager;

    @Override
    protected void doSetUp() {
        databaseToolkit = new Database();
        mockedConnectionManager = mockStatic(ConnectionManager.class);
        mockManager = mock(ConnectionManager.class);
        mockedConnectionManager.when(ConnectionManager::getDefault).thenReturn(mockManager);
    }

    @Override
    protected void doTearDown() {
        mockedConnectionManager.close();
    }

    private DatabaseConnection createMockConnection(String name, boolean connected) {
        DatabaseConnection mockConn = mock(DatabaseConnection.class);
        when(mockConn.getDisplayName()).thenReturn(name);
        when(mockConn.getDatabaseURL()).thenReturn("jdbc:mock://localhost/" + name);

        if (connected) {
            Connection jdbcConn = mock(Connection.class);
            when(mockConn.getJDBCConnection()).thenReturn(jdbcConn);
        } else {
            when(mockConn.getJDBCConnection()).thenReturn(null);
        }
        return mockConn;
    }

    @Test
    void test_givenConnectionsExist_whenListConnections_thenReturnsConnections() {
        // Arrange
        DatabaseConnection mockConn = mock(DatabaseConnection.class);
        JDBCDriver mockDriver = mock(JDBCDriver.class);
        when(mockConn.getDisplayName()).thenReturn("MockDB");
        when(mockConn.getDatabaseURL()).thenReturn("jdbc:mock://localhost");
        when(mockConn.getJDBCDriver()).thenReturn(mockDriver);
        when(mockDriver.getClassName()).thenReturn("org.mock.Driver");
        when(mockConn.getJDBCConnection()).thenReturn(null);

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        List<DatabaseConnectionInfo> result = wrapException(() -> databaseToolkit.listConnections());

        // Assert
        assertEquals(1, result.size());
        assertEquals("MockDB", result.get(0).getDisplayName());
        assertFalse(result.get(0).isConnected());
    }

    @Test
    void test_givenConnectionExists_whenConnect_thenReturnsSuccess() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", false);
        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Simulate connection being established after showConnectionDialog
        doAnswer(invocation -> {
            when(mockConn.getJDBCConnection()).thenReturn(mock(Connection.class));
            return null;
        }).when(mockManager).showConnectionDialog(mockConn);

        // Act
        String result = wrapException(() -> databaseToolkit.connect("TestDB"));

        // Assert
        assertTrue(result.contains("Successfully connected"));
        verify(mockManager).showConnectionDialog(mockConn);
    }

    @Test
    void test_givenConnectionConnected_whenDisconnect_thenReturnsSuccess() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        String result = wrapException(() -> databaseToolkit.disconnect("TestDB"));

        // Assert
        assertTrue(result.contains("Successfully disconnected"));
        verify(mockManager).disconnect(mockConn);
    }

    @Test
    void test_givenActiveConnection_whenGetMetadata_thenReturnsMetadata() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        Connection jdbcConn = mockConn.getJDBCConnection();
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);

        wrapException(() -> {
            when(jdbcConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getDatabaseProductName()).thenReturn("MockDB");
            when(mockMeta.getDatabaseProductVersion()).thenReturn("1.0");
            when(mockMeta.getDriverName()).thenReturn("MockDriver");
            when(mockMeta.getDriverVersion()).thenReturn("2.0");
        });

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        DatabaseMetadataInfo info = wrapException(() -> databaseToolkit.getMetadata("TestDB"));

        // Assert
        assertNotNull(info);
        assertEquals("MockDB", info.getDatabaseProductName());
        assertEquals("1.0", info.getDatabaseProductVersion());
    }

    @Test
    void test_givenActiveConnection_whenListTables_thenReturnsTables() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        Connection jdbcConn = mockConn.getJDBCConnection();
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);
        ResultSet mockRs = mock(ResultSet.class);

        wrapException(() -> {
            when(jdbcConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getTables(null, "public", "%", new String[]{"TABLE", "VIEW"})).thenReturn(mockRs);

            when(mockRs.next()).thenReturn(true, false);
            when(mockRs.getString("TABLE_NAME")).thenReturn("users");
            when(mockRs.getString("TABLE_TYPE")).thenReturn("TABLE");
        });

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        List<TableInfo> tables = wrapException(() -> databaseToolkit.listTables("TestDB", "public"));

        // Assert
        assertEquals(1, tables.size());
        assertEquals("users", tables.get(0).getTableName());
    }

    @Test
    void test_givenActiveConnection_whenExecuteQuery_thenReturnsQueryResult() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        Connection jdbcConn = mockConn.getJDBCConnection();
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);
        ResultSetMetaData mockRsMeta = mock(ResultSetMetaData.class);

        wrapException(() -> {
            when(jdbcConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(anyString())).thenReturn(mockRs);
            when(mockRs.getMetaData()).thenReturn(mockRsMeta);

            when(mockRsMeta.getColumnCount()).thenReturn(1);
            when(mockRsMeta.getColumnName(1)).thenReturn("id");

            when(mockRs.next()).thenReturn(true, false);
            when(mockRs.getObject(1)).thenReturn(108);
        });

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        QueryResult result = wrapException(() -> databaseToolkit.executeQuery("TestDB", "SELECT 1", 10));

        // Assert
        assertEquals(1, result.getRowCount());
        assertEquals("id", result.getHeaders().get(0));
        assertEquals(108, result.getRows().get(0).get("id"));
    }

    @Test
    void test_givenActiveConnection_whenExecuteStatement_thenReturnsStatementResult() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        Connection jdbcConn = mockConn.getJDBCConnection();
        Statement mockStmt = mock(Statement.class);

        wrapException(() -> {
            when(jdbcConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeUpdate(anyString())).thenReturn(5);
        });

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        StatementResult result = wrapException(() -> databaseToolkit.executeStatement("TestDB", "UPDATE users SET active = 1"));

        // Assert
        assertEquals(5, result.getAffectedRows());
        assertTrue(result.getMessage().contains("successfully"));
    }

    @Test
    void test_givenActiveConnection_whenGetTableColumns_thenReturnsColumns() {
        // Arrange
        DatabaseConnection mockConn = createMockConnection("TestDB", true);
        Connection jdbcConn = mockConn.getJDBCConnection();
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);
        ResultSet mockPkRs = mock(ResultSet.class);
        ResultSet mockColRs = mock(ResultSet.class);

        wrapException(() -> {
            when(jdbcConn.getMetaData()).thenReturn(mockMeta);

            // Mock Primary Keys
            when(mockMeta.getPrimaryKeys(null, null, "users")).thenReturn(mockPkRs);
            when(mockPkRs.next()).thenReturn(true, false);
            when(mockPkRs.getString("COLUMN_NAME")).thenReturn("id");

            // Mock Columns
            when(mockMeta.getColumns(null, null, "users", "%")).thenReturn(mockColRs);
            when(mockColRs.next()).thenReturn(true, false);
            when(mockColRs.getString("COLUMN_NAME")).thenReturn("id");
            when(mockColRs.getString("TYPE_NAME")).thenReturn("INTEGER");
            when(mockColRs.getInt("COLUMN_SIZE")).thenReturn(11);
            when(mockColRs.getString("IS_NULLABLE")).thenReturn("NO");
            when(mockColRs.getString("IS_AUTOINCREMENT")).thenReturn("YES");
        });

        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[]{mockConn});

        // Act
        List<ColumnInfo> columns = wrapException(() -> databaseToolkit.getTableColumns("TestDB", "users"));

        // Assert
        assertEquals(1, columns.size());
        ColumnInfo col = columns.get(0);
        assertEquals("id", col.getColumnName());
        assertTrue(col.isPrimaryKey());
        assertTrue(col.isAutoIncrement());
        assertFalse(col.isNullable());
    }

    @Test
    void test_givenNoConnection_whenConnect_thenThrowsException() {
        // Arrange
        when(mockManager.getConnections()).thenReturn(new DatabaseConnection[0]);

        // Act & Assert
        assertThrows(AgiToolException.class, () -> {
            databaseToolkit.connect("NonExistent");
        });
    }

    @Test
    void test_givenToolkit_whenInitialize_thenRegistersConnectionListener() {
        // Act
        databaseToolkit.initialize();

        // Assert
        verify(mockManager).addConnectionListener(databaseToolkit);
    }
}
