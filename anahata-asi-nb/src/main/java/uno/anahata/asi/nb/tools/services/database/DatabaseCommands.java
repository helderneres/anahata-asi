/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import java.io.IOException;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import uno.anahata.asi.agi.tool.graphql.GraphQLFieldInfo;
import uno.anahata.asi.agi.tool.graphql.GraphQLRequestContext;
import uno.anahata.asi.agi.tool.graphql.SafeGraphQLException;

/**
 * Explicit top-level commands. Native GraphQL validates selected arguments before
 * any effects. Result selection controls acquisition, never command execution.
 * JDBC connections remain owned by NetBeans; statements and cursors are local.
 */
@Slf4j
final class DatabaseCommands {
    static final int MAX_COLUMNS = 64;
    private static final int MAX_COMMANDS = 8;
    private static final int MAX_CELL_CHARACTERS = 4096;
    private static final int MAX_CELLS = 20_000;
    private static final int MAX_RESPONSE_CHARACTERS = 1_000_000;

    static final String SCHEMA = """
            scalar SqlValue
            type Mutation {
              connect(connection: ID!): ConnectionCommandInfo
              disconnect(connection: ID!): ConnectionCommandInfo
              executeQuery(connection: ID!, sql: String!, limit: Int = 100, timeoutSeconds: Int = 10, catalog: String, schema: String): SqlQueryInfo
              executeStatement(connection: ID!, sql: String!, timeoutSeconds: Int = 10, catalog: String, schema: String): SqlStatementInfo
            }
            type ConnectionCommandInfo {
              success: Boolean!
              connected: Boolean!
              connection: DatabaseConnectionInfo
            }
            type SqlQueryInfo {
              success: Boolean!
              columns: [SqlColumnInfo!]
              rows: [SqlRowInfo!]
              returnedRowCount: Int
              limit: Int!
              hasNextPage: Boolean
              totalCount: Int
              elapsedTimeMs: Float
            }
            type SqlColumnInfo {
              index: Int!
              label: String
              name: String
              jdbcType: Int
              typeName: String
              nullable: Boolean
            }
            type SqlRowInfo {
              index: Int!
              cells: [SqlCellInfo!]!
            }
            type SqlCellInfo {
              columnIndex: Int!
              value: SqlValue
              truncated: Boolean!
              encoding: String
            }
            type SqlStatementInfo {
              success: Boolean!
              affectedRows: Int!
              elapsedTimeMs: Float
            }
            """;

    private DatabaseCommands() { }

    /** Uses native fetchers only for operations; DTO fields use ordinary getters. */
    static RuntimeWiring.Builder wire(RuntimeWiring.Builder builder) {
        return builder.scalar(SqlValueScalar.TYPE)
                .type(TypeRuntimeWiring.newTypeWiring("Mutation")
                        .dataFetcher("connect", environment -> connectionCommand(environment, true))
                        .dataFetcher("disconnect", environment -> connectionCommand(environment, false))
                        .dataFetcher("executeQuery", DatabaseCommands::executeQuery)
                        .dataFetcher("executeStatement", DatabaseCommands::executeStatement))
                .type(TypeRuntimeWiring.newTypeWiring("ConnectionCommandInfo")
                        .defaultDataFetcher(environment -> {
                            ConnectionCommandSource source = environment.getSource();
                            return graphql.schema.PropertyDataFetcher.fetching(environment.getFieldDefinition().getName())
                                    .get(graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment).source(source.info()).build());
                        })
                        .dataFetcher("connection", environment -> {
                            ConnectionCommandSource source = environment.getSource();
                            return DatabaseGraphQL.connectionSource(source.registered());
                        }));
    }

    /**
     * Preflights all root commands, including fragment/directive selections and
     * coerced variables. No SQL, dialog or transaction operation runs here.
     * Permission approval belongs to the enclosing mutate tool, not to fields.
     */
    static void validate(List<GraphQLFieldInfo> fields, GraphQLRequestContext request) {
        Set<CommandKey> commands = new HashSet<>();
        for (GraphQLFieldInfo field : fields) {
            if (!"Mutation".equals(field.getParentType()) || field.getFieldName().startsWith("__")) {
                continue;
            }
            CommandKey key = new CommandKey(field.getFieldName(), field.getResponseName());
            if (!commands.add(key)) {
                continue; // GraphQL merges compatible fragment selections of the same response field.
            }
            if (commands.size() > MAX_COMMANDS) {
                throw new SafeGraphQLException("LimitExceeded", "At most 8 commands are allowed per document; none were started.");
            }
            Map<String, Object> args = field.getArguments();
            DatabaseConnection registered = DatabaseConnections.find((String) args.get("connection"));
            boolean sql = field.getFieldName().equals("executeQuery") || field.getFieldName().equals("executeStatement");
            if (sql) {
                String text = (String) args.get("sql");
                if (text == null || text.isBlank() || text.length() > 20_000) {
                    throw new SafeGraphQLException("InvalidArgument", "Provide non-empty SQL within 20000 characters; none of the commands were started.");
                }
                boundedInteger(args, "timeoutSeconds", 10, 30);
                if (field.getFieldName().equals("executeQuery")) {
                    boundedInteger(args, "limit", 100, 1000);
                }
                validateIdentity((String) args.get("catalog"));
                validateIdentity((String) args.get("schema"));
                Connection active = registered.getJDBCConnection();
                if (active != null) {
                    verifyDestination(active, (String) args.get("catalog"), (String) args.get("schema"));
                }
            }
            request.getOrCompute(key, () -> registered); // Pins the exact handle before effects.
        }
    }

    private static int boundedInteger(Map<String, Object> args, String name, int defaultValue, int maximum) {
        Object value = args.get(name);
        int number = value instanceof Integer integer ? integer : defaultValue;
        if ((args.containsKey(name) && value == null) || number < 1 || number > maximum) {
            throw new SafeGraphQLException("InvalidArgument", name + " must be between 1 and " + maximum + ".");
        }
        return number;
    }

    private static void validateIdentity(String value) {
        if (value != null && (value.isBlank() || value.length() > 1024)) {
            throw new SafeGraphQLException("InvalidArgument", "Catalog/schema assertions must be non-empty and at most 1024 characters.");
        }
    }

    private static void verifyDestination(Connection jdbc, String catalog, String schema) {
        try {
            if (catalog != null && !Objects.equals(catalog, jdbc.getCatalog())
                    || schema != null && !Objects.equals(schema, jdbc.getSchema())) {
                throw new SafeGraphQLException("DestinationMismatch", "The active catalog/schema differs from the requested destination. No automatic switch was made.");
            }
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    private static DatabaseConnection target(DataFetchingEnvironment environment) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        // Supplier is intentionally non-resolving: only a preflight-pinned destination may execute.
        return request.getOrCompute(new CommandKey(environment.getFieldDefinition().getName(), environment.getField().getResultKey()),
                () -> { throw new SafeGraphQLException("PermissionDenied", "Command preflight is required."); });
    }

    private static Connection active(DataFetchingEnvironment environment) {
        Connection jdbc = target(environment).getJDBCConnection();
        if (jdbc == null) {
            throw new SafeGraphQLException("ConnectionInactive", "Connection is inactive. Connect explicitly in the IDE or with a separate mutate connect command.");
        }
        verifyDestination(jdbc, environment.getArgument("catalog"), environment.getArgument("schema"));
        return jdbc;
    }

    /** Connection effects belong exclusively to this top-level resolver. */
    private static ConnectionCommandSource connectionCommand(DataFetchingEnvironment environment, boolean connect) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        request.checkBudget();
        DatabaseConnection registered = target(environment);
        ConnectionManager manager = ConnectionManager.getDefault();
        if (connect && registered.getJDBCConnection() == null) {
            manager.showConnectionDialog(registered); // Explicitly authorized UI operation only.
        } else if (!connect && registered.getJDBCConnection() != null) {
            manager.disconnect(registered);
        }
        boolean connected = registered.getJDBCConnection() != null;
        if (connected != connect) {
            throw new SafeGraphQLException("ConnectionCommandFailed", "The explicit connection command did not reach the requested state. Check IDE Services before retrying.");
        }
        request.checkBudget();
        return new ConnectionCommandSource(ConnectionCommandInfo.builder().success(true).connected(connected).build(), registered);
    }

    /** SQL executes even if no rows, columns or count fields are selected. */
    private static SqlQueryInfo executeQuery(DataFetchingEnvironment environment) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        Connection jdbc = active(environment);
        int limit = boundedInteger(environment.getArguments(), "limit", 100, 1000);
        long started = System.nanoTime();
        try (Statement statement = jdbc.createStatement()) {
            configure(statement, environment, limit + 1);
            try (ResultSet rows = statement.executeQuery(environment.getArgument("sql"))) {
                request.checkBudget();
                boolean selectedColumns = environment.getSelectionSet().contains("columns");
                boolean selectedRows = environment.getSelectionSet().contains("rows");
                boolean selectedCells = environment.getSelectionSet().contains("rows/cells");
                boolean selectedValue = environment.getSelectionSet().contains("rows/cells/value")
                        || environment.getSelectionSet().contains("rows/cells/truncated")
                        || environment.getSelectionSet().contains("rows/cells/encoding");
                ResultSetMetaData metadata = selectedColumns || selectedCells ? rows.getMetaData() : null;
                int count = metadata == null ? 0 : metadata.getColumnCount();
                if (count > MAX_COLUMNS) {
                    throw new SafeGraphQLException("LimitExceeded", "SQL column limit (64) reached. Select fewer columns in the SQL; the command already executed.");
                }
                List<SqlColumnInfo> columns = selectedColumns ? columns(metadata, count, environment) : null;
                boolean traverse = selectedRows || environment.getSelectionSet().contains("returnedRowCount")
                        || environment.getSelectionSet().contains("hasNextPage");
                List<SqlRowInfo> resultRows = selectedRows ? new ArrayList<>() : null;
                int returned = 0;
                boolean hasNext = false;
                if (traverse) {
                    while (next(rows, request, statement)) {
                        if (returned == limit) {
                            hasNext = true;
                            break;
                        }
                        if (selectedRows) {
                            List<SqlCellInfo> cells = new ArrayList<>();
                            if (selectedCells) {
                                for (int column = 1; column <= count; column++) {
                                    request.checkBudget();
                                    budget(request).consumeCell();
                                    CellValue value = selectedValue ? cellValue(rows.getObject(column), request) : new CellValue(null, false, null);
                                    cells.add(SqlCellInfo.builder().columnIndex(column).value(value.value())
                                            .truncated(value.truncated()).encoding(value.encoding()).build());
                                }
                            }
                            resultRows.add(SqlRowInfo.builder().index(returned).cells(cells).build());
                        }
                        returned++;
                    }
                }
                request.checkBudget();
                return SqlQueryInfo.builder().success(true).columns(columns).rows(resultRows)
                        .returnedRowCount(traverse ? returned : null).limit(limit).hasNextPage(traverse ? hasNext : null)
                        .totalCount(null).elapsedTimeMs((System.nanoTime() - started) / 1_000_000L).build();
            }
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    private static List<SqlColumnInfo> columns(ResultSetMetaData metadata, int count, DataFetchingEnvironment environment) throws SQLException {
        List<SqlColumnInfo> result = new ArrayList<>();
        var selection = environment.getSelectionSet();
        for (int index = 1; index <= count; index++) {
            GraphQLRequestContext.from(environment).checkBudget();
            SqlColumnInfo.SqlColumnInfoBuilder builder = SqlColumnInfo.builder().index(index);
            if (selection.contains("columns/label")) { builder.label(metadata.getColumnLabel(index)); }
            if (selection.contains("columns/name")) { builder.name(metadata.getColumnName(index)); }
            if (selection.contains("columns/jdbcType")) { builder.jdbcType(metadata.getColumnType(index)); }
            if (selection.contains("columns/typeName")) { builder.typeName(metadata.getColumnTypeName(index)); }
            if (selection.contains("columns/nullable")) {
                int nullable = metadata.isNullable(index);
                builder.nullable(switch (nullable) {
                    case ResultSetMetaData.columnNoNulls -> false;
                    case ResultSetMetaData.columnNullable -> true;
                    default -> null;
                });
            }
            result.add(builder.build());
        }
        return result;
    }

    private static SqlStatementInfo executeStatement(DataFetchingEnvironment environment) {
        Connection jdbc = active(environment);
        long started = System.nanoTime();
        try (Statement statement = jdbc.createStatement()) {
            configure(statement, environment, 0);
            int affected = statement.executeUpdate(environment.getArgument("sql"));
            GraphQLRequestContext.from(environment).checkBudget();
            return SqlStatementInfo.builder().success(true).affectedRows(affected)
                    .elapsedTimeMs((System.nanoTime() - started) / 1_000_000L).build();
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    /** Driver timeout is independent of the cooperative document deadline. */
    private static void configure(Statement statement, DataFetchingEnvironment environment, int maxRows) throws SQLException {
        GraphQLRequestContext.from(environment).checkBudget();
        int timeout = boundedInteger(environment.getArguments(), "timeoutSeconds", 10, 30);
        try {
            statement.setQueryTimeout(timeout);
        } catch (java.sql.SQLFeatureNotSupportedException unsupported) {
            throw new SafeGraphQLException("TimeoutUnsupported", "This JDBC driver cannot configure a statement timeout; SQL was not started.");
        }
        if (maxRows > 0) { statement.setMaxRows(maxRows); }
        GraphQLRequestContext.from(environment).checkBudget();
    }

    private static boolean next(ResultSet rows, GraphQLRequestContext request, Statement statement) throws SQLException {
        try {
            request.checkBudget();
            boolean next = rows.next();
            request.checkBudget();
            return next;
        } catch (SafeGraphQLException interrupted) {
            try { statement.cancel(); } catch (SQLException ignored) { log.debug("JDBC cancellation unavailable"); }
            throw interrupted;
        }
    }

    private static SqlBudget budget(GraphQLRequestContext request) {
        return request.getOrCompute(SqlBudget.class, SqlBudget::new);
    }

    /** Bounds retained LOB data and frees only owned cell resources. */
    private static CellValue cellValue(Object value, GraphQLRequestContext request) throws SQLException {
        if (value == null) { return new CellValue(null, false, null); }
        if (value instanceof Blob blob) {
            try {
                long length = blob.length();
                return binary(blob.getBytes(1, (int) Math.min(length, MAX_CELL_CHARACTERS)), length > MAX_CELL_CHARACTERS, request);
            } finally { blob.free(); }
        }
        if (value instanceof Clob clob) {
            try (Reader reader = clob.getCharacterStream()) {
                char[] buffer = new char[MAX_CELL_CHARACTERS + 1];
                int read = 0;
                while (read < buffer.length) {
                    request.checkBudget();
                    int n = reader.read(buffer, read, buffer.length - read);
                    if (n < 0) { break; }
                    if (n == 0) { throw new SafeGraphQLException("SqlValueUnavailable", "LOB reader did not progress; command already executed."); }
                    read += n;
                }
                return text(new String(buffer, 0, Math.min(read, MAX_CELL_CHARACTERS)), read > MAX_CELL_CHARACTERS, request);
            } catch (IOException failure) {
                throw new SafeGraphQLException("SqlValueUnavailable", "LOB value could not be read; command already executed.");
            } finally { clob.free(); }
        }
        if (value instanceof byte[] bytes) { return binary(bytes, bytes.length > MAX_CELL_CHARACTERS, request); }
        if (value instanceof CharSequence chars) { return text(chars.toString(), false, request); }
        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f)) {
                return text(number.toString(), false, request);
            }
            return new CellValue(number, false, null);
        }
        if (value instanceof Boolean) { return new CellValue(value, false, null); }
        if (value instanceof java.sql.Timestamp timestamp) { return text(timestamp.toLocalDateTime().toString(), false, request); }
        if (value instanceof java.sql.Date date) { return text(date.toLocalDate().toString(), false, request); }
        if (value instanceof java.sql.Time time) { return text(time.toLocalTime().toString(), false, request); }
        if (value instanceof java.time.temporal.TemporalAccessor || value instanceof UUID) { return text(value.toString(), false, request); }
        throw new SafeGraphQLException("SqlValueUnsupported", "A selected SQL cell has an unsupported driver value; command already executed. Cast it to a portable scalar in SQL.");
    }

    private static CellValue text(String text, boolean truncated, GraphQLRequestContext request) {
        boolean shortened = truncated || text.length() > MAX_CELL_CHARACTERS;
        String bounded = text.substring(0, Math.min(text.length(), MAX_CELL_CHARACTERS));
        budget(request).consumeCharacters(bounded.length());
        return new CellValue(bounded, shortened, null);
    }

    private static CellValue binary(byte[] bytes, boolean truncated, GraphQLRequestContext request) {
        byte[] bounded = java.util.Arrays.copyOf(bytes, Math.min(bytes.length, MAX_CELL_CHARACTERS));
        String encoded = Base64.getEncoder().encodeToString(bounded);
        budget(request).consumeCharacters(encoded.length());
        return new CellValue(encoded, truncated, "base64");
    }

    /** Safe categories, not driver messages/SQL/URLs; correlation retained locally. */
    private static SafeGraphQLException sqlFailure(SQLException failure) {
        String reference = UUID.randomUUID().toString();
        String state = Objects.toString(failure.getSQLState(), "");
        String category = failure instanceof SQLTimeoutException || state.startsWith("HYT") ? "SqlTimeout"
                : state.equals("42S02") || state.equals("42P01") ? "SqlTableNotFound"
                : state.equals("42S22") || state.equals("42703") ? "SqlColumnNotFound"
                : state.startsWith("08") ? "SqlConnectionFailure"
                : state.startsWith("28") || state.equals("42501") ? "SqlPermissionDenied"
                : "SqlExecutionFailed";
        log.warn("Database command failure reference={} category={} driverCode={}", reference, category, failure.getErrorCode());
        return new SafeGraphQLException(category, "Database command failed (" + category + "; reference " + reference
                + "). Check destination, discovered schema and IDE permissions. Earlier effects are not rolled back; verify before retrying.");
    }

    private record ConnectionCommandSource(ConnectionCommandInfo info, DatabaseConnection registered) { }
    private record CommandKey(String field, String responseName) { }
    private record CellValue(Object value, boolean truncated, String encoding) { }

    /** Request-local SQL output ceiling across all command aliases. */
    private static final class SqlBudget {
        private final AtomicInteger cells = new AtomicInteger();
        private final AtomicInteger characters = new AtomicInteger();
        void consumeCell() {
            if (cells.incrementAndGet() > MAX_CELLS) { throw new SafeGraphQLException("LimitExceeded", "SQL cell budget reached; reduce rows/columns. Commands already started are not rolled back."); }
        }
        void consumeCharacters(int count) {
            if (characters.addAndGet(count) > MAX_RESPONSE_CHARACTERS) { throw new SafeGraphQLException("LimitExceeded", "SQL output budget reached; reduce rows/cells. Commands already started are not rolled back."); }
        }
    }
}
