/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. ForÃƒÂ§a BarÃƒÂ§a! */
package uno.anahata.asi.nb.tools.services.database;

import java.beans.PropertyChangeSupport;
import java.time.Duration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import uno.anahata.asi.nb.tools.services.database.policy.DatabaseExecutionMode;
import uno.anahata.asi.nb.tools.services.database.policy.DatabaseLoadingPolicy;
import uno.anahata.asi.nb.tools.services.database.policy.DatabaseMutationPolicy;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.agi.ExpandToolsPreference;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.ToolManager;
import uno.anahata.asi.agi.tool.ToolPermission;
import uno.anahata.asi.agi.tool.ToolExecutionStatus;
import uno.anahata.asi.agi.tool.graphql.GraphQLExecutionInfo;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodTool;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolCall;
import uno.anahata.asi.agi.tool.spi.java.JavaObjectToolkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Executes GraphQL commands through the actual Java tool response protocol.
 * Only the IDE registry, message/session boundary and JDBC driver are mocked;
 * no database, provider, session restoration or IDE installation is used.
 */
public class DatabaseTest {

    private MockedStatic<ConnectionManager> managerStatic;
    private ConnectionManager manager;
    private DatabaseConnection registered;
    private Connection jdbc;
    private Statement statement;
    private ResultSet cursor;
    private ResultSetMetaData metadata;
    private JavaObjectToolkit wrapper;
    private Database database;
    private AbstractModelMessage message;

    @BeforeEach
    void setUp() throws Exception {
        managerStatic = mockStatic(ConnectionManager.class);
        manager = mock(ConnectionManager.class);
        managerStatic.when(ConnectionManager::getDefault).thenReturn(manager);
        registered = mock(DatabaseConnection.class);
        jdbc = mock(Connection.class);
        statement = mock(Statement.class);
        cursor = mock(ResultSet.class);
        metadata = mock(ResultSetMetaData.class);
        when(registered.getName()).thenReturn("anahata-test-registration");
        when(registered.getDisplayName()).thenReturn("ANAHATA");
        when(registered.getJDBCConnection()).thenReturn(jdbc);
        when(manager.getConnections()).thenReturn(new DatabaseConnection[]{registered});
        when(jdbc.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(cursor);
        when(cursor.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(anyInt())).thenReturn("duplicate_alias");
        when(metadata.getColumnName(1)).thenReturn("first_column");
        when(metadata.getColumnName(2)).thenReturn("second_column");
        when(cursor.next()).thenReturn(true, false);
        when(cursor.getObject(1)).thenReturn(21);
        when(cursor.getObject(2)).thenReturn(null);
        ToolManager toolManager = mock(ToolManager.class);
        Agi agi = mock(Agi.class);
        AgiConfig config = mock(AgiConfig.class);
        when(agi.getConfig()).thenReturn(config);
        when(agi.getToolManager()).thenReturn(toolManager);
        when(config.getExpandTools()).thenReturn(ExpandToolsPreference.PROMPT);
        when(toolManager.getAgi()).thenReturn(agi);
        message = mock(AbstractModelMessage.class);
        when(message.getAgi()).thenReturn(agi);
        when(message.getPropertyChangeSupport()).thenReturn(new PropertyChangeSupport(message));
        wrapper = new JavaObjectToolkit(toolManager, Database.class);
        wrapper.initialize();
        database = (Database) wrapper.getToolkitInstance();
    }

    @AfterEach
    void tearDown() {
        managerStatic.close();
    }

    private JavaMethodTool tool(String method) {
        return wrapper.getAllTools().stream().filter(tool -> tool.getName().equals("Database." + method)).findFirst().orElseThrow();
    }

    private JavaMethodToolCall call(String document, Map<String, Object> variables) {
        return tool("mutate").createCall(message, UUID.randomUUID().toString(), Map.of("document", document, "variables", variables));
    }

    private GraphQLExecutionInfo execute(String document) {
        return execute(document, Map.of());
    }

    private GraphQLExecutionInfo execute(String document, Map<String, Object> variables) {
        JavaMethodToolCall call = call(document, variables);
        call.getResponse().execute();
        assertEquals(ToolExecutionStatus.EXECUTED, call.getResponse().getStatus(), call.getResponse().getErrors());
        return (GraphQLExecutionInfo) call.getResponse().getResult();
    }

    private GraphQLExecutionInfo success(String document) {
        GraphQLExecutionInfo result = execute(document);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void exactlyThreeToolsExposeTheDatabaseSchemaAndCommands() {
        assertEquals(List.of("Database.mutate", "Database.query", "Database.schema"), wrapper.getAllTools().stream().map(JavaMethodTool::getName).sorted().toList());
        for (String method : List.of("connections", "metadata", "tables", "columns", "connect", "disconnect", "executeQuery", "executeStatement")) {
            assertTrue(database.schema().contains(method));
        }
        wrapper.getAllTools().forEach(tool -> assertEquals(ToolPermission.PROMPT, tool.getPermission()));
    }

    @Test
    void queryCannotExecuteSqlOrMutations() throws Exception {
        assertFalse(database.query("mutation { executeStatement(connection:\"ANAHATA\",sql:\"UPDATE anahata SET enabled=1\") { success } }", Map.of()).getErrors().isEmpty());
        assertFalse(database.query("{ executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }", Map.of()).getErrors().isEmpty());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void directMutationRequiresTheToolProtocol() throws Exception {
        GraphQLExecutionInfo result = database.mutate("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }", Map.of());
        assertEquals("PermissionDenied", result.getErrors().getFirst().getCategory());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void approvedConnectRunsEvenForSuccessOnlyAndHasNoMetadataChildren() throws Exception {
        when(registered.getJDBCConnection()).thenReturn(null);
        when(manager.connect(registered)).thenAnswer(invocation -> {
            when(registered.getJDBCConnection()).thenReturn(jdbc);
            return true;
        });
        GraphQLExecutionInfo result = success("mutation { connect(connection:\"ANAHATA\") { success } }");
        assertEquals(true, object(result.getData().get("connect")).get("success"));
        verify(manager).connect(registered);
        verify(manager, never()).showConnectionDialog(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void explicitDisconnectAndIdempotenceUseTheManagerOnly() throws Exception {
        doAnswer(invocation -> {
            when(registered.getJDBCConnection()).thenReturn(null);
            return null;
        }).when(manager).disconnect(registered);
        success("mutation { disconnect(connection:\"ANAHATA\") { success connected } }");
        success("mutation { disconnect(connection:\"ANAHATA\") { success } }");
        verify(manager, times(1)).disconnect(registered);
        verify(jdbc, never()).close();
    }

    @Test
    void sqlSuccessOnlyExecutesButAvoidsMetadataIterationAndValues() throws Exception {
        success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { success } }");
        verify(statement).executeQuery("SELECT 1");
        verify(statement).setQueryTimeout(10);
        verify(statement).setMaxRows(3);
        verify(cursor, never()).getMetaData();
        verify(cursor, never()).next();
        verify(cursor, never()).getObject(anyInt());
        verify(cursor).close();
        verify(statement).close();
        verify(jdbc, never()).close();
    }

    @Test
    void rowsPreserveDuplicateAliasesNullsAndColumnPositions() throws Exception {
        GraphQLExecutionInfo result = success("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT first_column AS duplicate_alias, second_column AS duplicate_alias FROM anahata",limit:2) {
                  columns { index label name } rows { index cells { columnIndex value truncated } } returnedRowCount hasNextPage totalCount
                } }
                """);
        Map<String, Object> sql = object(result.getData().get("executeQuery"));
        List<Map<String, Object>> columns = items(sql.get("columns"));
        assertEquals("duplicate_alias", columns.get(0).get("label"));
        assertEquals("duplicate_alias", columns.get(1).get("label"));
        List<Map<String, Object>> cells = items(items(sql.get("rows")).getFirst().get("cells"));
        assertEquals(1, cells.get(0).get("columnIndex"));
        assertEquals(2, cells.get(1).get("columnIndex"));
        assertEquals(21, cells.get(0).get("value"));
        assertTrue(cells.get(1).containsKey("value"));
        assertNull(cells.get(1).get("value"));
        assertEquals(1, sql.get("returnedRowCount"));
        assertEquals(false, sql.get("hasNextPage"));
        assertNull(sql.get("totalCount"));
        String serialized = SchemaProvider.OBJECT_MAPPER.writeValueAsString(result);
        assertTrue(serialized.contains("\"value\":null"), serialized);
        assertTrue(serialized.contains("\"totalCount\":null"), serialized);
        verify(metadata, never()).getColumnType(anyInt());
        verify(jdbc, never()).close();
    }

    @Test
    void rowCountAndLookaheadDoNotReadCellValuesOrMetadata() throws Exception {
        when(cursor.next()).thenReturn(true, true, true, true);
        GraphQLExecutionInfo result = success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { returnedRowCount hasNextPage } }");
        Map<String, Object> sql = object(result.getData().get("executeQuery"));
        assertEquals(2, sql.get("returnedRowCount"));
        assertEquals(true, sql.get("hasNextPage"));
        verify(cursor, times(3)).next();
        verify(cursor, never()).getMetaData();
        verify(cursor, never()).getObject(anyInt());
    }

    @Test
    void exactlyLimitRowsHaveNoNextPage() throws Exception {
        when(cursor.next()).thenReturn(true, true, false);
        GraphQLExecutionInfo result = success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { returnedRowCount hasNextPage } }");
        assertEquals(false, object(result.getData().get("executeQuery")).get("hasNextPage"));
        verify(cursor, times(3)).next();
    }

    @Test
    void columnLabelsOnlyAvoidRowsValuesAndOtherMetadataGetters() throws Exception {
        success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { columns { label } } }");
        verify(metadata).getColumnLabel(1);
        verify(metadata, never()).getColumnName(anyInt());
        verify(metadata, never()).getColumnType(anyInt());
        verify(cursor, never()).next();
        verify(cursor, never()).getObject(anyInt());
    }

    @Test
    void rowPositionsOnlyAvoidReadingCellValues() throws Exception {
        success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { rows { cells { columnIndex } } } }");
        verify(metadata).getColumnCount();
        verify(metadata, never()).getColumnLabel(anyInt());
        verify(cursor, never()).getObject(anyInt());
    }

    @Test
    void variablesAliasesFragmentsAndDirectivesDriveTheSelectedWork() throws Exception {
        GraphQLExecutionInfo result = execute("""
                mutation($target:ID!,$sql:String!,$include:Boolean!) {
                  chosen:executeQuery(connection:$target,sql:$sql,limit:2) { ...Output }
                  skip:executeStatement(connection:$target,sql:"UPDATE anahata SET enabled=1") @include(if:$include) { success }
                }
                fragment Output on SqlQueryInfo { rows { cells { alias:value } } }
                """, Map.of("target", DatabaseConnections.id(registered), "sql", "SELECT 1", "include", false));
        assertTrue(result.getErrors().isEmpty(), result.toString());
        assertTrue(result.getData().containsKey("chosen"));
        assertFalse(result.getData().containsKey("skip"));
        assertEquals(21, items(items(object(result.getData().get("chosen")).get("rows")).getFirst().get("cells")).getFirst().get("alias"));
        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void invalidLaterCommandPreventsEarlierEffects() throws Exception {
        GraphQLExecutionInfo result = execute("""
                mutation { first:executeStatement(connection:"ANAHATA",sql:"UPDATE anahata SET enabled=1") { success }
                  second:executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:1001) { success } }
                """);
        assertFalse(result.getErrors().isEmpty());
        assertNull(result.getData());
        verify(jdbc, never()).createStatement();
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void validationRejectsBadVariablesSchemaChannelsAndMultipleOperationsBeforeEffects() throws Exception {
        for (String document : List.of("{ connections { offset } }", "mutation A { connect(connection:\"ANAHATA\") { success } } mutation B { connect(connection:\"ANAHATA\") { success } }", "mutation { unknown }")) {
            assertFalse(execute(document).getErrors().isEmpty());
        }
        assertFalse(execute("mutation($n:Int!) { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:$n) { success } }", Map.of("n", "invalid")).getErrors().isEmpty());
        verify(jdbc, never()).createStatement();
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void allConnectionIdentitiesAreCheckedBeforeAnyEffect() throws Exception {
        GraphQLExecutionInfo result = execute("mutation { first:disconnect(connection:\"ANAHATA\") { success } second:connect(connection:\"missing\") { success } }");
        assertEquals("ConnectionNotFound", result.getErrors().getFirst().getCategory());
        verify(manager, never()).disconnect(any());
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void ambiguousInactiveAndMismatchedConnectionsNeverFallback() throws Exception {
        when(registered.getJDBCConnection()).thenReturn(null);
        GraphQLExecutionInfo inactive = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }");
        assertEquals("ConnectionInactive", inactive.getErrors().getFirst().getCategory());
        verify(manager, never()).showConnectionDialog(any());
        when(registered.getJDBCConnection()).thenReturn(jdbc);
        when(jdbc.getCatalog()).thenReturn("anahata");
        GraphQLExecutionInfo mismatch = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",catalog:\"other\") { success } }");
        assertEquals("DestinationMismatch", mismatch.getErrors().getFirst().getCategory());
        verify(jdbc, never()).createStatement();
        DatabaseConnection duplicate = mock(DatabaseConnection.class);
        when(duplicate.getDisplayName()).thenReturn("ANAHATA");
        when(duplicate.getName()).thenReturn("other-registration");
        when(manager.getConnections()).thenReturn(new DatabaseConnection[]{registered, duplicate});
        GraphQLExecutionInfo ambiguous = execute("mutation { connect(connection:\"ANAHATA\") { success } }");
        assertEquals("ConnectionAmbiguous", ambiguous.getErrors().getFirst().getCategory());
    }

    @Test
    void statementEffectsDoNotDependOnSelectingAffectedRows() throws Exception {
        when(statement.executeUpdate(anyString())).thenReturn(3);
        GraphQLExecutionInfo result = success("mutation { executeStatement(connection:\"ANAHATA\",sql:\"UPDATE anahata SET enabled=1\") { success } }");
        assertEquals(true, object(result.getData().get("executeStatement")).get("success"));
        verify(statement).executeUpdate("UPDATE anahata SET enabled=1");
        verify(statement).close();
        verify(jdbc, never()).close();
        verify(jdbc, never()).commit();
        verify(jdbc, never()).rollback();
        verify(jdbc, never()).setAutoCommit(anyBoolean());
    }

    @Test
    void multipleCommandsAreSerialWithPartialErrorsAndNoCombinedRollbackOrRetry() throws Exception {
        Statement first = mock(Statement.class);
        Statement second = mock(Statement.class);
        when(jdbc.createStatement()).thenReturn(first, second);
        when(first.executeUpdate("UPDATE anahata SET enabled=1")).thenReturn(1);
        when(second.executeUpdate("UPDATE missing SET enabled=1")).thenThrow(new SQLException("jdbc:private user=secret", "42S02"));
        GraphQLExecutionInfo result = execute("""
                mutation { first:executeStatement(connection:"ANAHATA",sql:"UPDATE anahata SET enabled=1") { affectedRows }
                  second:executeStatement(connection:"ANAHATA",sql:"UPDATE missing SET enabled=1") { success } }
                """);
        assertEquals(1, object(result.getData().get("first")).get("affectedRows"));
        assertNull(result.getData().get("second"));
        assertEquals(List.of("second"), result.getErrors().getFirst().getPath());
        assertEquals("SqlTableNotFound", result.getErrors().getFirst().getCategory());
        assertFalse(result.getErrors().getFirst().getMessage().contains("secret"));
        var order = inOrder(first, second);
        order.verify(first).executeUpdate(anyString());
        order.verify(second).executeUpdate(anyString());
        verify(first).close();
        verify(second).close();
        verify(jdbc, never()).rollback();
        verify(jdbc, never()).commit();
        verify(second, times(1)).executeUpdate(anyString());
    }

    @Test
    void deniedAndDisabledToolsCannotBeExecutedManually() throws Exception {
        JavaMethodToolCall call = call("mutation { connect(connection:\"ANAHATA\") { success } }", Map.of());
        tool("mutate").setPermission(ToolPermission.DENY);
        call.getResponse().execute();
        assertEquals(ToolExecutionStatus.FAILED, call.getResponse().getStatus());
        tool("mutate").setPermission(ToolPermission.PROMPT);
        wrapper.setEnabled(false);
        JavaMethodToolCall disabled = call("mutation { connect(connection:\"ANAHATA\") { success } }", Map.of());
        disabled.getResponse().execute();
        assertEquals(ToolExecutionStatus.FAILED, disabled.getResponse().getStatus());
        verify(manager, never()).showConnectionDialog(any());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void safeErrorsDistinguishTimeoutColumnAndPermissionsWithoutDriverMessages() throws Exception {
        for (SQLException failure : List.of(new SQLTimeoutException("secret"), new SQLException("secret", "42S22"), new SQLException("secret", "42501"))) {
            doThrow(failure).when(statement).executeQuery(anyString());
            GraphQLExecutionInfo result = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }");
            assertFalse(result.getErrors().isEmpty());
            assertFalse(result.getErrors().getFirst().getMessage().contains("secret"));
            assertTrue(result.getErrors().getFirst().getMessage().contains("reference"));
        }
        verify(jdbc, never()).close();
    }

    @Test
    void driverWithoutTimeoutSupportDoesNotStartSql() throws Exception {
        doThrow(new SQLFeatureNotSupportedException("private")).when(statement).setQueryTimeout(anyInt());
        GraphQLExecutionInfo result = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }");
        assertEquals("TimeoutUnsupported", result.getErrors().getFirst().getCategory());
        verify(statement, never()).executeQuery(anyString());
        verify(statement).close();
    }

    @Test
    void cellTruncationAndBinaryEncodingAreExplicit() throws Exception {
        when(cursor.getObject(1)).thenReturn("x".repeat(5000));
        when(cursor.getObject(2)).thenReturn(new byte[]{1, 2, 3});
        GraphQLExecutionInfo result = success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { rows { cells { value truncated encoding } } } }");
        List<Map<String, Object>> cells = items(items(object(result.getData().get("executeQuery")).get("rows")).getFirst().get("cells"));
        assertEquals(4096, ((String) cells.get(0).get("value")).length());
        assertEquals(true, cells.get(0).get("truncated"));
        assertEquals("AQID", cells.get(1).get("value"));
        assertEquals("base64", cells.get(1).get("encoding"));
    }

    @Test
    void unknownDriverObjectsNeverReachThePayloadAndResourcesClose() throws Exception {
        when(cursor.getObject(1)).thenReturn(new Object());
        GraphQLExecutionInfo result = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { rows { cells { value } } } }");
        assertEquals("SqlValueUnsupported", result.getErrors().getFirst().getCategory());
        verify(cursor).close();
        verify(statement).close();
        verify(jdbc, never()).close();
    }

    @Test
    void interruptedIterationCancelsOwnedStatementAndClosesResources() throws Exception {
        when(cursor.next()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return true;
        });
        try {
            GraphQLExecutionInfo result = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\",limit:2) { returnedRowCount } }");
            assertFalse(result.getErrors().isEmpty());
        } finally {
            Thread.interrupted();
        }
        verify(statement).cancel();
        verify(cursor).close();
        verify(statement).close();
        verify(jdbc, never()).close();
    }

    @Test
    void opaqueDiscoveryIdSuppressesSensitiveRegisteredLabels() throws Exception {
        when(registered.getDisplayName()).thenReturn("jdbc:private user=secret");
        GraphQLExecutionInfo result = database.query("{ connections { items { id displayName } } }", Map.of());
        assertTrue(result.getErrors().isEmpty());
        String json = SchemaProvider.OBJECT_MAPPER.writeValueAsString(result);
        assertFalse(json.contains("jdbc:"));
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("db-"));
        verify(registered, never()).getPassword();
        verify(registered, never()).getDatabaseURL();
        verify(registered, never()).getUser();
    }

    @Test
    void nullableDriverFlagsAndEmptyResultStayDistinct() throws Exception {
        when(metadata.isNullable(anyInt())).thenReturn(ResultSetMetaData.columnNullableUnknown);
        when(cursor.next()).thenReturn(false);
        GraphQLExecutionInfo result = success("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:2) {
                  columns { nullable } rows { index } returnedRowCount hasNextPage
                } }
                """);
        Map<String, Object> sql = object(result.getData().get("executeQuery"));
        assertNull(items(sql.get("columns")).getFirst().get("nullable"));
        assertTrue(items(sql.get("rows")).isEmpty());
        assertEquals(0, sql.get("returnedRowCount"));
        assertEquals(false, sql.get("hasNextPage"));
    }

    @Test
    void invalidSqlTimeoutAndNullLimitAreRejectedBeforeAnyStatement() throws Exception {
        List<Map<String, Object>> cases = List.of(Map.of("sql", ""), Map.of("timeout", 0), Map.of("timeout", -1),
                java.util.Collections.singletonMap("n", null), Map.of("n", 0));
        for (Map<String, Object> invalid : cases) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("sql", "SELECT 1");
            variables.putAll(invalid);
            GraphQLExecutionInfo result = execute("""
                    mutation($sql:String!,$timeout:Int=10,$n:Int=100) {
                      executeQuery(connection:"ANAHATA",sql:$sql,timeoutSeconds:$timeout,limit:$n) { success }
                    }
                    """, variables);
            assertFalse(result.getErrors().isEmpty(), "Invalid case: " + invalid);
        }
        verify(jdbc, never()).createStatement();
    }

    @Test
    void commandCountLimitAppliesBeforeAnyConnectionEffect() {
        StringBuilder document = new StringBuilder("mutation {");
        for (int i = 0; i < 9; i++) {
            document.append("c").append(i).append(":").append("""
                    connect(connection:"ANAHATA") { success }
                    """);
        }
        document.append("}");
        assertEquals("LimitExceeded", execute(document.toString()).getErrors().getFirst().getCategory());
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void selectedConnectionOutputUsesConcreteInfoWithoutMetadataAcquisition() throws Exception {
        GraphQLExecutionInfo result = success("""
                mutation { connect(connection:"ANAHATA") { success connected connection { id displayName } } }
                """);
        Map<String, Object> command = object(result.getData().get("connect"));
        assertEquals(true, command.get("connected"));
        assertEquals(DatabaseConnections.id(registered), object(command.get("connection")).get("id"));
        verify(jdbc, never()).getMetaData();
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void correctedSqlDocumentWithObjectVariablesReachesTheFixtureAndKeepsSelection() throws Exception {
        GraphQLExecutionInfo result = execute("""
                mutation ExecuteSql($conn: ID!, $sql: String!) {
                  executeQuery(connection: $conn, sql: $sql, limit: 100) {
                    success columns { index label }
                    rows { cells { columnIndex value } }
                    returnedRowCount hasNextPage elapsedTimeMs
                  }
                }
                """, Map.of("conn", "ANAHATA", "sql", "SELECT 1"));
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        assertEquals(1, object(result.getData().get("executeQuery")).get("returnedRowCount"));
        verify(statement).executeQuery("SELECT 1");
        verify(metadata, never()).getColumnName(anyInt());
        verify(cursor).close();
        verify(statement).close();
        verify(jdbc, never()).close();
    }

    @Test
    void missingSqlSubselectionsAreActionableAndStartNoEffects() throws Exception {
        GraphQLExecutionInfo result = execute("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT private_literal") { columns rows } }
                """);
        assertEquals(2, result.getErrors().size());
        for (var error : result.getErrors()) {
            assertEquals("SubselectionRequired", error.getType());
            assertEquals("NOT_STARTED", error.getEffects());
            assertFalse(error.getLocations().isEmpty());
            assertFalse(error.getMessage().contains("private_literal"));
        }
        verify(jdbc, never()).createStatement();
        verifyNoInteractions(statement);
    }

    @Test
    void extendedModeHasNoPolicyPageMaximumOrAdditionalTimeoutAndNoIntegerOverflow() throws Exception {
        database.getExecutionPolicies().putMode(DatabaseExecutionMode.EXTENDED_WORK);
        GraphQLExecutionInfo result = success("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:2147483647) {
                  returnedRowCount hasNextPage limit
                } }
                """);
        assertEquals(Integer.MAX_VALUE, object(result.getData().get("executeQuery")).get("limit"));
        verify(statement).setLargeMaxRows(2147483648L);
        verify(statement, never()).setQueryTimeout(anyInt());
        verify(cursor, times(2)).next();
        verify(cursor, never()).getMetaData();
        verify(cursor, never()).getObject(anyInt());
        verify(jdbc, never()).close();
    }

    @Test
    void positiveSubsecondTimeoutIsNeverAdaptedToJdbcUnlimited() throws Exception {
        database.getExecutionPolicies().putMutationPolicy(new DatabaseMutationPolicy().putTimeout(Duration.ofMillis(900)));
        success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }");
        verify(statement).setQueryTimeout(1);
    }

    @Test
    void explicitTimeoutCanExceedLegacyThirtySecondsWithinOneCallDeadline() throws Exception {
        database.getExecutionPolicies().putMode(DatabaseExecutionMode.EXTENDED_WORK);
        success("""
                mutation {
                  a:executeStatement(connection:"ANAHATA",sql:"UPDATE anahata SET enabled=1",timeoutSeconds:60) { success }
                  b:executeQuery(connection:"ANAHATA",sql:"SELECT 1",timeoutSeconds:90) { success }
                }
                """);
        verify(statement, times(2)).setQueryTimeout(intThat(value -> value > 30 && value <= 60));
    }

    @Test
    void configurationChangesDuringSqlAffectOnlyLaterCalls() throws Exception {
        database.getExecutionPolicies().putMutationPolicy(new DatabaseMutationPolicy()
                .putResultLoadingPolicy(new DatabaseLoadingPolicy().putDefaultPageSize(2)));
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            database.getExecutionPolicies().putMutationPolicy(new DatabaseMutationPolicy()
                    .putResultLoadingPolicy(new DatabaseLoadingPolicy().putDefaultPageSize(7)));
            return cursor;
        });
        GraphQLExecutionInfo first = success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { limit } }");
        assertEquals(2, object(first.getData().get("executeQuery")).get("limit"));
        GraphQLExecutionInfo second = success("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { limit } }");
        assertEquals(7, object(second.getData().get("executeQuery")).get("limit"));
    }

    @Test
    void inconsistentComposedPolicyRejectsTheDocumentBeforeStatements() throws Exception {
        database.getExecutionPolicies().putMutationPolicy(new DatabaseMutationPolicy()
                .putResultLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(1)));
        GraphQLExecutionInfo result = execute("mutation { executeQuery(connection:\"ANAHATA\",sql:\"SELECT 1\") { success } }");
        assertEquals("InvalidPolicy", result.getErrors().getFirst().getCategory());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void nestedMetadataInMutationUsesResultLoadingPolicy() throws Exception {
        database.getExecutionPolicies().putMutationPolicy(new DatabaseMutationPolicy()
                .putResultLoadingPolicy(new DatabaseLoadingPolicy().putDefaultPageSize(7)));
        GraphQLExecutionInfo result = success("""
                mutation { connect(connection:"ANAHATA") { connection { tables { limit } } } }
                """);
        Map<String, Object> connection = object(object(result.getData().get("connect")).get("connection"));
        assertEquals(7, object(connection.get("tables")).get("limit"));
        verify(jdbc, never()).getMetaData();
        verify(manager, never()).connect(any());
    }

    @Test
    void oversizedGraphqlIntegerIsRejectedBeforeSqlAndOverflowLookaheadFallsBackSafely() throws Exception {
        database.getExecutionPolicies().putMode(DatabaseExecutionMode.EXTENDED_WORK);
        GraphQLExecutionInfo invalid = execute("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:2147483648) { success } }
                """);
        assertFalse(invalid.getErrors().isEmpty());
        verify(jdbc, never()).createStatement();
        doThrow(new SQLFeatureNotSupportedException()).when(statement).setLargeMaxRows(anyLong());
        GraphQLExecutionInfo result = success("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:2147483647) { returnedRowCount } }
                """);
        assertEquals(1, object(result.getData().get("executeQuery")).get("returnedRowCount"));
        verify(cursor).close();
        verify(jdbc, never()).close();
    }

    @Test
    void actualSqlRowBudgetRemainsExplicitAfterPageMaximumIsRemoved() throws Exception {
        database.getExecutionPolicies().putMode(DatabaseExecutionMode.EXTENDED_WORK);
        when(cursor.next()).thenReturn(true);
        GraphQLExecutionInfo result = execute("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:20000) { rows { index } } }
                """);
        assertEquals("LimitExceeded", result.getErrors().getFirst().getCategory());
        assertNull(result.getErrors().getFirst().getEffects());
        assertTrue(result.getErrors().getFirst().getMessage().contains("10000 rows"));
        verify(cursor).close();
        verify(statement).close();
        verify(jdbc, never()).close();
    }

    @Test
    void ordinaryToolkitsKeepTheirRegistrationRebindAndInvocation() throws Exception {
        JavaObjectToolkit ordinary = new JavaObjectToolkit(wrapper.getToolManager(), OrdinaryToolkit.class);
        ordinary.initialize();
        ordinary.rebind();
        ordinary.postActivate();
        assertEquals(List.of("OrdinaryToolkit.echo"), ordinary.getAllTools().stream().map(JavaMethodTool::getName).toList());
        JavaMethodToolCall call = ordinary.getAllTools().getFirst().createCall(message, "ordinary-call", Map.of("text", "original"));
        call.setModifiedArgument("text", "edited");
        call.getResponse().execute();
        assertEquals(ToolExecutionStatus.EXECUTED, call.getResponse().getStatus());
        assertEquals("edited", call.getResponse().getResult());
        assertEquals(ToolPermission.PROMPT, ordinary.getAllTools().getFirst().getPermission());
    }

    @Test
    void explicitNullVariablesSurviveConversionAndCallSerialization() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", null);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("n", null);
        variables.put("nested", nested);
        JavaMethodToolCall call = call("""
                mutation($n:Int=100) { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:$n) { success } }
                """, variables);
        assertTrue(object(call.getArgs().get("variables")).containsKey("n"));
        assertNull(object(call.getArgs().get("variables")).get("n"));
        var serialized = SchemaProvider.OBJECT_MAPPER.readTree(SchemaProvider.OBJECT_MAPPER.writeValueAsString(call.getRawArgs()));
        assertTrue(serialized.get("variables").has("n"));
        assertTrue(serialized.get("variables").get("n").isNull());
        assertTrue(serialized.get("variables").get("nested").get("inner").isNull());
        call.getResponse().execute();
        GraphQLExecutionInfo result = (GraphQLExecutionInfo) call.getResponse().getResult();
        assertEquals("InvalidArgument", result.getErrors().getFirst().getCategory());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void invalidVariableObjectIsSafelyRejectedAndCannotExecutePartialArguments() throws Exception {
        JavaMethodToolCall rejected = tool("mutate").createCall(message, "bad-variables", Map.of("document", """
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1") { success } }
                """, "variables", "jdbc:private user=secret"));
        assertEquals(ToolExecutionStatus.FAILED, rejected.getResponse().getStatus());
        assertFalse(rejected.getResponse().getErrors().contains("secret"));
        rejected.getResponse().execute();
        assertEquals(ToolExecutionStatus.FAILED, rejected.getResponse().getStatus());
        assertNull(rejected.getResponse().getResult());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void editedVariableSnapshotKeepsNullAndDoesNotActivateTheDefaultLimit() throws Exception {
        JavaMethodToolCall call = call("""
                mutation($n:Int=100) { executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:$n) { success } }
                """, Map.of("n", 2));
        call.setModifiedArgument("variables", java.util.Collections.singletonMap("n", null));
        call.getResponse().execute();
        GraphQLExecutionInfo result = (GraphQLExecutionInfo) call.getResponse().getResult();
        assertEquals("InvalidArgument", result.getErrors().getFirst().getCategory());
        var json = SchemaProvider.OBJECT_MAPPER.readTree(SchemaProvider.OBJECT_MAPPER.writeValueAsString(call.getResponse().getModifiedArgs()));
        assertTrue(json.get("variables").has("n"));
        assertTrue(json.get("variables").get("n").isNull());
        verify(jdbc, never()).createStatement();
    }

    @Test
    void malformedEditedVariableObjectIsRejectedWithoutStacktraceOrSql() throws Exception {
        JavaMethodToolCall call = call("""
                mutation { executeQuery(connection:"ANAHATA",sql:"SELECT 1") { success } }
                """, Map.of());
        call.setModifiedArgument("variables", "jdbc:private user=secret");
        call.getResponse().execute();
        assertEquals(ToolExecutionStatus.FAILED, call.getResponse().getStatus());
        assertFalse(call.getResponse().getErrors().contains("secret"));
        assertFalse(call.getResponse().getErrors().contains(".java:"));
        verify(jdbc, never()).createStatement();
    }

    @uno.anahata.asi.agi.tool.AgiToolkit("Ordinary Java toolkit regression fixture")
    public static class OrdinaryToolkit extends uno.anahata.asi.agi.tool.AnahataToolkit {

        @AgiTool("Echoes the selected text")
        public String echo(String text) {
            return text;
        }
    }

}
