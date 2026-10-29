/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import java.io.IOException;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.time.Duration;
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
 * Native resolvers for explicitly selected top-level database commands.
 * <p>
 * Preflight validates arguments and exact destinations before any command
 * resolver. Result selection controls metadata, cursor traversal, and value
 * acquisition; it never suppresses an included command's effect. GraphQL
 * executes Mutation roots serially, without a shared transaction, rollback, or
 * automatic retry.
 * </p>
 * <p>
 * JDBC work may block and belongs outside the Swing event dispatch thread.
 * NetBeans owns each connection. These resolvers close statements, result sets,
 * and owned LOB resources; an explicit disconnect delegates connection
 * ownership to the IDE. Raw SQL is not classified as safe or read-only by this
 * helper.
 * </p>
 */
@Slf4j
final class DatabaseCommands {

    /**
     * Maximum SQL result columns when selected output requires result metadata.
     */
    static final int MAX_COLUMNS = 64;
    /**
     * Maximum distinct selected Mutation response fields in one document.
     */
    private static final int MAX_COMMANDS = 8;
    /**
     * Maximum UTF-16 code units of retained text, or source bytes of binary,
     * per cell.
     */
    private static final int MAX_CELL_CHARACTERS = 4096;
    /**
     * Maximum materialized cells across all command aliases in one request.
     */
    private static final int MAX_CELLS = 20_000;
    /**
     * Actual traversed SQL rows per document, including look-ahead rows.
     */
    private static final int MAX_RESULT_ROWS = 10_000;
    /**
     * Maximum retained text and base64 code units across one request.
     * <p>
     * This does not count numeric/boolean values, labels, JSON syntax, or the
     * entire serialized response. It is a cell-value retention budget, not a
     * wire-size limit.
     * </p>
     */
    private static final int MAX_RESPONSE_CHARACTERS = 1_000_000;

    /**
     * Explicit Mutation fields and concrete SQL output types appended to
     * discovery SDL.
     */
    static final String SCHEMA = """
            scalar SqlValue
            type Mutation {
              connect(connection: ID!): ConnectionCommandInfo
              disconnect(connection: ID!): ConnectionCommandInfo
              executeQuery(connection: ID!, sql: String!, limit: Int, timeoutSeconds: Int, catalog: String, schema: String): SqlQueryInfo
              executeStatement(connection: ID!, sql: String!, timeoutSeconds: Int, catalog: String, schema: String): SqlStatementInfo
            }
            enum ConnectionCommandStatus { SUCCEEDED PENDING REQUIRES_INTERACTION FAILED UNKNOWN }
            type ConnectionCommandInfo {
              status: ConnectionCommandStatus!
              message: String!
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

    /**
     * Prevents construction of this stateless resolver collection.
     */
    private DatabaseCommands() {
    }

    /**
     * Registers command resolvers and the SQL value scalar on native wiring.
     * <p>
     * Already-acquired DTO properties use native property fetchers. The
     * connection child of a connection command exposes a private handle through
     * the discovery source adapter and does not initiate another connection
     * effect.
     * </p>
     *
     * @param builder the mutable wiring builder to extend
     * @return the same builder with command and result wiring installed
     */
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
     * Preflights selected Mutation roots and pins each exact destination
     * handle.
     * <p>
     * Compatible fragments with one response name count once; separate aliases
     * count as separate commands. Permission approval belongs to the enclosing
     * mutate tool. No SQL, connection dialog, or transaction operation is
     * started here. Active catalog/schema assertions may call JDBC getters. An
     * inactive registration may pass this stage, but SQL still requires an
     * active connection at execution time. Validation cannot establish SQL
     * correctness or prevent later runtime failures.
     * </p>
     *
     * @param fields selected schema fields with native-coerced arguments and
     * directives
     * @param request the request-local cache and cooperative deadline
     * @throws SafeGraphQLException if identities, argument bounds, command
     * count, catalog/schema assertions, or request budgets are invalid
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
                applyExplicitTimeout(args, request);
                if (field.getFieldName().equals("executeQuery")) {
                    DatabasePolicySupport.pageLimit(args, request, true, "executeQuery");
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

    /**
     * Refines the one document deadline with an optional explicit SQL
     * allowance. Omission preserves the effective policy; explicit null and
     * nonpositive values are invalid. All selected SQL overrides are applied
     * before any command starts, so their shortest allowance governs the entire
     * call, measured from its start.
     *
     * @param args native-coerced command arguments
     * @param request request whose existing deadline may only be tightened
     * @throws SafeGraphQLException if an explicit allowance is invalid or
     * elapsed
     */
    private static void applyExplicitTimeout(Map<String, Object> args, GraphQLRequestContext request) {
        if (!args.containsKey("timeoutSeconds")) {
            return;
        }
        Object value = args.get("timeoutSeconds");
        if (!(value instanceof Integer seconds) || seconds <= 0) {
            throw new SafeGraphQLException("InvalidArgument", "timeoutSeconds must be a positive GraphQL Int; omit it to use the effective policy.");
        }
        request.limitTimeout(Duration.ofSeconds(seconds));
    }

    /**
     * Validates an optional catalog or schema assertion without normalizing it.
     *
     * @param value a literal identity, or {@code null} to leave that assertion
     * absent
     * @throws SafeGraphQLException if a supplied identity is blank or exceeds
     * 1024 characters
     */
    private static void validateIdentity(String value) {
        if (value != null && (value.isBlank() || value.length() > 1024)) {
            throw new SafeGraphQLException("InvalidArgument", "Catalog/schema assertions must be non-empty and at most 1024 characters.");
        }
    }

    /**
     * Checks supplied qualifiers against the active JDBC connection.
     * <p>
     * Null assertions are ignored. This helper never calls setCatalog or
     * setSchema, and matching an assertion does not sandbox the SQL that will
     * execute later.
     * </p>
     *
     * @param jdbc the active, IDE-owned connection
     * @param catalog literal expected catalog, or {@code null}
     * @param schema literal expected schema, or {@code null}
     * @throws SafeGraphQLException if an assertion differs or a qualifier
     * cannot be read
     */
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

    /**
     * Obtains the exact IDE handle pinned by command preflight.
     *
     * @param environment the current root field and request context
     * @return the preflight-selected registration without another registry
     * lookup
     * @throws SafeGraphQLException if preflight did not pin this response field
     * or the cooperative request budget has expired
     */
    private static DatabaseConnection target(DataFetchingEnvironment environment) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        // Supplier is intentionally non-resolving: only a preflight-pinned destination may execute.
        return request.getOrCompute(new CommandKey(environment.getFieldDefinition().getName(), environment.getField().getResultKey()),
                () -> {
                    throw new SafeGraphQLException("PermissionDenied", "Command preflight is required.");
                });
    }

    /**
     * Requires an active connection and rechecks the destination immediately
     * before SQL.
     *
     * @param environment the selected SQL command and optional qualifier
     * assertions
     * @return the IDE-owned JDBC connection; the caller must not close it
     * @throws SafeGraphQLException if the connection is inactive, preflight is
     * absent, assertions fail, or the request budget is exhausted
     */
    private static Connection active(DataFetchingEnvironment environment) {
        Connection jdbc = target(environment).getJDBCConnection();
        if (jdbc == null) {
            throw new SafeGraphQLException("ConnectionInactive", "Connection is inactive. Connect explicitly in the IDE or with a separate mutate connect command.");
        }
        verifyDestination(jdbc, environment.getArgument("catalog"), environment.getArgument("schema"));
        return jdbc;
    }

    /**
     * Performs an included root connect or disconnect operation through the
     * IDE.
     * <p>
     * Connect uses the public synchronous IDE API outside the EDT and never
     * opens a dialog. Pending, interaction-required, confirmed failure and
     * unknown outcomes are explicit result states. A registration already in
     * the requested state requires no lifecycle operation. Result fields do not
     * control whether the command runs, and a later budget failure does not
     * undo its effect.
     * </p>
     *
     * @param environment the preflight-validated root command
     * @param connect {@code true} to connect, {@code false} to disconnect
     * @return public outcome attributes and the private registration for
     * selected children
     * @throws SafeGraphQLException if the request budget is exhausted or
     * preflight is unavailable; a later error does not undo a lifecycle
     * operation already started
     */
    private static ConnectionCommandSource connectionCommand(DataFetchingEnvironment environment, boolean connect) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        request.checkBudget();
        DatabaseConnection registered = target(environment);
        ConnectionCommandInfo info = DatabaseConnectionCommands.execute(ConnectionManager.getDefault(), registered, connect);
        request.checkBudget();
        return new ConnectionCommandSource(info, registered);
    }

    /**
     * Executes SQL once for this selected root and acquires only requested
     * output.
     * <p>
     * SQL runs even for a success-only selection. Rows/count/continuation
     * require bounded cursor traversal; column or cell selections require
     * metadata. Reading cell values is deferred until value, truncation, or
     * encoding is selected. At most limit plus one rows are traversed, and
     * total count is not scanned separately. Statements and cursors close on
     * success or failure; the IDE connection stays open.
     * </p>
     *
     * @param environment selected SQL, destination, limits, and result fields
     * @return bounded positional output; unacquired collections/counts remain
     * null
     * @throws SafeGraphQLException if argument, connection, driver, value,
     * timeout, or result budgets fail; SQL may already have produced effects
     */
    private static SqlQueryInfo executeQuery(DataFetchingEnvironment environment) {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        Connection jdbc = active(environment);
        int limit = DatabasePolicySupport.pageLimit(environment.getArguments(), request, true, "executeQuery");
        long started = System.nanoTime();
        try (Statement statement = jdbc.createStatement()) {
            configure(statement, environment, (long) limit + 1L);
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
                        budget(request).consumeRow();
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

    /**
     * Acquires only selected column descriptors, retaining positional identity.
     *
     * @param metadata result metadata already obtained by the parent resolver
     * @param count the bounded JDBC column count
     * @param environment selected descriptor attributes and request budget
     * @return descriptors in one-based JDBC column order; unrequested
     * attributes stay null
     * @throws SQLException if a selected descriptor cannot be read
     * @throws SafeGraphQLException if the cooperative budget is exhausted
     */
    private static List<SqlColumnInfo> columns(ResultSetMetaData metadata, int count, DataFetchingEnvironment environment) throws SQLException {
        List<SqlColumnInfo> result = new ArrayList<>();
        var selection = environment.getSelectionSet();
        for (int index = 1; index <= count; index++) {
            GraphQLRequestContext.from(environment).checkBudget();
            SqlColumnInfo.SqlColumnInfoBuilder builder = SqlColumnInfo.builder().index(index);
            if (selection.contains("columns/label")) {
                builder.label(metadata.getColumnLabel(index));
            }
            if (selection.contains("columns/name")) {
                builder.name(metadata.getColumnName(index));
            }
            if (selection.contains("columns/jdbcType")) {
                builder.jdbcType(metadata.getColumnType(index));
            }
            if (selection.contains("columns/typeName")) {
                builder.typeName(metadata.getColumnTypeName(index));
            }
            if (selection.contains("columns/nullable")) {
                int nullable = metadata.isNullable(index);
                builder.nullable(switch (nullable) {
                    case ResultSetMetaData.columnNoNulls ->
                        false;
                    case ResultSetMetaData.columnNullable ->
                        true;
                    default ->
                        null;
                });
            }
            result.add(builder.build());
        }
        return result;
    }

    /**
     * Executes an update-style statement regardless of selected outcome
     * attributes.
     * <p>
     * The affected-row count comes from executeUpdate. No commit, rollback,
     * retry, or autocommit change is added, and the statement closes without
     * closing the connection.
     * </p>
     *
     * @param environment the selected SQL command, destination, and timeout
     * @return the successful statement outcome and elapsed milliseconds
     * @throws SafeGraphQLException if validation, connection, timeout, driver
     * execution, or request budgets fail; earlier or current effects are not
     * rolled back
     */
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

    /**
     * Adapts the remaining shared deadline to JDBC before SQL starts. Positive
     * fractions round up to one second, never zero/unlimited. Very large
     * durations are clamped to JDBC's positive int seconds representation. A
     * null deadline leaves independent driver/database defaults unchanged. No
     * legacy thirty-second ceiling is imposed. The driver must honor its own
     * timeout; cooperative checks do not forcibly interrupt it or promise
     * rollback.
     *
     * <p>
     * The row hint includes look-ahead using long arithmetic. If the largest
     * GraphQL Int page needs a long row hint unsupported by the driver, cursor
     * traversal still stops at the requested page plus look-ahead. The actual
     * row, cell and response budgets apply independently of page policy.</p>
     *
     * @param statement resolver-owned statement
     * @param environment request deadline and argument context
     * @param maxRows positive row hint, or zero to leave driver row defaults
     * @throws SQLException if a supported JDBC setting fails
     * @throws SafeGraphQLException if the configured timeout is unsupported or
     * elapsed
     */
    private static void configure(Statement statement, DataFetchingEnvironment environment, long maxRows) throws SQLException {
        GraphQLRequestContext request = GraphQLRequestContext.from(environment);
        Duration remaining = request.getRemainingTimeout();
        if (remaining != null) {
            long seconds = remaining.getSeconds();
            if (remaining.getNano() > 0 && seconds < Integer.MAX_VALUE) {
                seconds++;
            }
            int timeout = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, seconds));
            try {
                statement.setQueryTimeout(timeout);
            } catch (java.sql.SQLFeatureNotSupportedException unsupported) {
                throw new SafeGraphQLException("TimeoutUnsupported", "This JDBC driver cannot configure the requested statement timeout; SQL was not started.");
            }
        }
        if (maxRows > 0 && maxRows <= Integer.MAX_VALUE) {
            statement.setMaxRows((int) maxRows);
        } else if (maxRows > Integer.MAX_VALUE) {
            try {
                statement.setLargeMaxRows(maxRows);
            } catch (java.sql.SQLFeatureNotSupportedException unsupported) {
                // This source-side hint is optional; the cursor loop and actual budgets remain bounded.
            }
        }
        request.checkBudget();
    }

    /**
     * Advances the cursor with cooperative checks and best-effort cancellation.
     * <p>
     * Cancellation is attempted if a surrounding budget check fails. A blocked
     * driver call cannot be forcibly preempted by these checks, and
     * cancellation failure is logged locally without replacing the original
     * safe failure.
     * </p>
     *
     * @param rows the resolver-owned SQL cursor
     * @param request the current cooperative budget
     * @param statement the owned statement used for cancellation
     * @return whether the cursor reached another row
     * @throws SQLException if cursor advancement fails
     * @throws SafeGraphQLException if time expires or interruption is observed
     */
    private static boolean next(ResultSet rows, GraphQLRequestContext request, Statement statement) throws SQLException {
        try {
            request.checkBudget();
            boolean next = rows.next();
            request.checkBudget();
            return next;
        } catch (SafeGraphQLException interrupted) {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
                log.debug("JDBC cancellation unavailable");
            }
            throw interrupted;
        }
    }

    /**
     * Shares retained-cell and text counters across command aliases.
     *
     * @param request the current execution-local cache
     * @return this request's SQL output budget
     * @throws SafeGraphQLException if the cooperative budget is exhausted
     */
    private static SqlBudget budget(GraphQLRequestContext request) {
        return request.getOrCompute(SqlBudget.class, SqlBudget::new);
    }

    /**
     * Converts one selected driver value to a bounded portable scalar.
     * <p>
     * SQL null, finite numbers, and booleans retain their scalar meaning. Text
     * is bounded; binary is base64; JDBC temporal values use portable textual
     * forms. Non-finite floating values become text. Blob/Clob resources are
     * read in bounded form and freed here; unsupported driver objects are
     * rejected rather than exposed.
     * </p>
     *
     * @param value the selected JDBC value, possibly {@code null}
     * @param request shared output counters and cooperative deadline
     * @return a scalar with explicit truncation and optional encoding
     * information
     * @throws SQLException if a LOB cannot be read or released
     * @throws SafeGraphQLException if a driver value is unsupported, a reader
     * fails, or request/output budgets are exhausted
     */
    private static CellValue cellValue(Object value, GraphQLRequestContext request) throws SQLException {
        if (value == null) {
            return new CellValue(null, false, null);
        }
        if (value instanceof Blob blob) {
            try {
                long length = blob.length();
                return binary(blob.getBytes(1, (int) Math.min(length, MAX_CELL_CHARACTERS)), length > MAX_CELL_CHARACTERS, request);
            } finally {
                blob.free();
            }
        }
        if (value instanceof Clob clob) {
            try (Reader reader = clob.getCharacterStream()) {
                char[] buffer = new char[MAX_CELL_CHARACTERS + 1];
                int read = 0;
                while (read < buffer.length) {
                    request.checkBudget();
                    int n = reader.read(buffer, read, buffer.length - read);
                    if (n < 0) {
                        break;
                    }
                    if (n == 0) {
                        throw new SafeGraphQLException("SqlValueUnavailable", "LOB reader did not progress; command already executed.");
                    }
                    read += n;
                }
                return text(new String(buffer, 0, Math.min(read, MAX_CELL_CHARACTERS)), read > MAX_CELL_CHARACTERS, request);
            } catch (IOException failure) {
                throw new SafeGraphQLException("SqlValueUnavailable", "LOB value could not be read; command already executed.");
            } finally {
                clob.free();
            }
        }
        if (value instanceof byte[] bytes) {
            return binary(bytes, bytes.length > MAX_CELL_CHARACTERS, request);
        }
        if (value instanceof CharSequence chars) {
            return text(chars.toString(), false, request);
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f)) {
                return text(number.toString(), false, request);
            }
            return new CellValue(number, false, null);
        }
        if (value instanceof Boolean) {
            return new CellValue(value, false, null);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return text(timestamp.toLocalDateTime().toString(), false, request);
        }
        if (value instanceof java.sql.Date date) {
            return text(date.toLocalDate().toString(), false, request);
        }
        if (value instanceof java.sql.Time time) {
            return text(time.toLocalTime().toString(), false, request);
        }
        if (value instanceof java.time.temporal.TemporalAccessor || value instanceof UUID) {
            return text(value.toString(), false, request);
        }
        throw new SafeGraphQLException("SqlValueUnsupported", "A selected SQL cell has an unsupported driver value; command already executed. Cast it to a portable scalar in SQL.");
    }

    /**
     * Bounds text by UTF-16 code units and accounts for retained characters.
     *
     * @param text the non-null source text
     * @param truncated whether an earlier read already observed omitted source
     * data
     * @param request shared cell-text budget
     * @return at most 4096 code units, with an explicit truncation flag
     * @throws SafeGraphQLException if the retained text budget is exhausted
     */
    private static CellValue text(String text, boolean truncated, GraphQLRequestContext request) {
        boolean shortened = truncated || text.length() > MAX_CELL_CHARACTERS;
        String bounded = text.substring(0, Math.min(text.length(), MAX_CELL_CHARACTERS));
        budget(request).consumeCharacters(bounded.length());
        return new CellValue(bounded, shortened, null);
    }

    /**
     * Bounds binary source bytes and encodes the retained prefix as base64.
     *
     * @param bytes the non-null binary source
     * @param truncated whether the source contains omitted bytes
     * @param request shared retained-character budget
     * @return at most 4096 source bytes encoded as base64 with the supplied
     * truncation flag
     * @throws SafeGraphQLException if the base64 text exceeds the shared output
     * budget
     */
    private static CellValue binary(byte[] bytes, boolean truncated, GraphQLRequestContext request) {
        byte[] bounded = java.util.Arrays.copyOf(bytes, Math.min(bytes.length, MAX_CELL_CHARACTERS));
        String encoded = Base64.getEncoder().encodeToString(bounded);
        budget(request).consumeCharacters(encoded.length());
        return new CellValue(encoded, truncated, "base64");
    }

    /**
     * Maps known SQLStates to a safe category and adds a local correlation id.
     * <p>
     * Driver messages, SQL text, and connection URLs are not copied into the
     * authored public error. SQLState conventions vary by driver, so
     * unrecognized states retain a generic execution category. Logging records
     * the reference, category, and code.
     * </p>
     *
     * @param failure the driver failure
     * @return a safe exception whose message warns that effects are not rolled
     * back
     */
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

    /**
     * Pairs public connection-command output with its private IDE registration.
     *
     * @param info public success/state properties
     * @param registered exact IDE-owned handle for selected child discovery
     */
    private record ConnectionCommandSource(ConnectionCommandInfo info, DatabaseConnection registered) {

    }

    /**
     * Identifies one merged Mutation response field within a request.
     *
     * @param field the schema command name
     * @param responseName the result key, including any alias
     */
    private record CommandKey(String field, String responseName) {

    }

    /**
     * A portable cell value and the information needed to interpret retained
     * data.
     *
     * @param value portable scalar, or {@code null} for SQL null or unacquired
     * value
     * @param truncated whether source text/bytes were omitted
     * @param encoding {@code base64} for binary output, otherwise {@code null}
     */
    private record CellValue(Object value, boolean truncated, String encoding) {

    }

    /**
     * Mutable request-local output counters shared by all command aliases.
     * <p>
     * Atomic counters enforce aggregate ceilings but do not serialize SQL
     * execution or establish a transaction boundary.
     * </p>
     */
    private static final class SqlBudget {

        /**
         * Traversed SQL rows, including look-ahead, across the document.
         */
        private final AtomicInteger rows = new AtomicInteger();
        /**
         * Materialized positional cells, including positions without values.
         */
        private final AtomicInteger cells = new AtomicInteger();
        /**
         * Retained text/base64 code units; excludes JSON structure and numeric
         * scalars.
         */
        private final AtomicInteger characters = new AtomicInteger();

        /**
         * Reserves one traversed SQL row across this request.
         *
         * @throws SafeGraphQLException if the 10000-row traversal ceiling is
         * exceeded
         */
        void consumeRow() {
            if (rows.incrementAndGet() > MAX_RESULT_ROWS) {
                throw new SafeGraphQLException("LimitExceeded", "SQL traversal budget (10000 rows/document) reached; request a smaller result. Commands already started are not rolled back.");
            }
        }

        /**
         * Reserves one materialized positional cell.
         *
         * @throws SafeGraphQLException if the 20000-cell ceiling is exceeded
         */
        void consumeCell() {
            if (cells.incrementAndGet() > MAX_CELLS) {
                throw new SafeGraphQLException("LimitExceeded", "SQL cell budget reached; reduce rows/columns. Commands already started are not rolled back.");
            }
        }

        /**
         * Reserves retained text or base64 code units across this request.
         *
         * @param count nonnegative number of newly retained code units
         * @throws SafeGraphQLException if the 1000000-code-unit ceiling is
         * exceeded
         */
        void consumeCharacters(int count) {
            if (characters.addAndGet(count) > MAX_RESPONSE_CHARACTERS) {
                throw new SafeGraphQLException("LimitExceeded", "SQL output budget reached; reduce rows/cells. Commands already started are not rolled back.");
            }
        }
    }
}
