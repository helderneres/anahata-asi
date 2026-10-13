/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.AgiToolParam;
import uno.anahata.asi.agi.tool.AnahataToolkit;

/**
 * Embedded GraphQL infrastructure using the existing Java toolkit protocol.
 * Native resolvers perform work only for selected fields; this class provides
 * engine lifecycle, query-only validation and shared request resource limits.
 * <p>
 * No HTTP server, generic selection planner or field authorization layer is
 * created. Existing Anahata toolkit permissions govern the inherited tools.
 * </p>
 *
 * @author anahata
 */
@Slf4j
public abstract class GraphQLToolkit extends AnahataToolkit {

    /**
     * Default page size used by the reference Database toolkit.
     */
    public static final int DEFAULT_PAGE_LIMIT = 25;

    /**
     * Maximum page size used by the reference Database toolkit.
     */
    public static final int MAX_PAGE_LIMIT = 100;

    /**
     * Marker belonging exclusively to errors authored by our safe handler.
     */
    private static final String SAFE_ERROR_EXTENSION = "anahataSafeError";

    /**
     * Stable category attached only to safe authored resolver failures.
     */
    private static final String SAFE_CATEGORY_EXTENSION = "anahataSafeCategory";

    /**
     * Executable engine rebuilt after activation instead of being persisted.
     */
    private transient volatile GraphQL graphQL;

    /**
     * SDL corresponding exactly to the currently built executable engine.
     */
    private transient String schemaSdl;

    /**
     * {@inheritDoc}
     * <p>
     * Restores the executable schema and transient engine after
     * persistence.</p>
     */
    @Override
    public void rebind() {
        super.rebind();
        buildEngine();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Builds the schema after the JavaObjectToolkit has attached its
     * context.</p>
     */
    @Override
    public void initialize() {
        super.initialize();
        buildEngine();
    }

    /**
     * Returns the executable schema so an agent can choose fields and
     * arguments.
     *
     * @return The exact SDL used by the embedded engine.
     */
    @AgiTool(value = "Returns this toolkit's GraphQL schema. Use it to choose fields and arguments for query().")
    public String schema() {
        return schemaSdl;
    }

    /**
     * Executes exactly one GraphQL query operation. Mutations, subscriptions
     * and documents with multiple operations are rejected before resolver
     * execution. Errors are explicit public information, allowing partial data
     * without leaking internal driver exception messages or implementation
     * stack traces.
     *
     * @param query One named or abbreviated GraphQL query.
     * @param variables Variable values, or null for an empty variable map.
     * @return Selected data and safe errors.
     */
    @AgiTool(value = "Executes one read-only GraphQL query with optional variables. Only selected fields perform work. "
            + "Mutation/subscription and multiple operations are rejected. Limits apply to nesting, cost and metadata work.")
    public GraphQLExecutionInfo query(
            @AgiToolParam("One GraphQL query operation; abbreviated { ... } queries are supported.") String query,
            @AgiToolParam(value = "GraphQL variable values, or an empty map when none are needed.", required = false) Map<String, Object> variables) {
        String validationError = validateDocument(query);
        if (validationError != null) {
            return failure(validationError, "InvalidQuery");
        }
        GraphQLRequestContext request = new GraphQLRequestContext(getMaxMetadataOperations(), getMaxDurationMillis());
        try {
            ExecutionInput input = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables == null ? Map.of() : variables)
                    .graphQLContext(Map.of(GraphQLRequestContext.class, request))
                    .build();
            ExecutionResult result = graphQL.execute(input);
            List<GraphQLErrorInfo> errors = result.getErrors().stream()
                    .map(GraphQLToolkit::publicError)
                    .toList();
            Map<String, Object> data = result.getData();
            return new GraphQLExecutionInfo(data == null ? null : normalizeMap(data), errors);
        } catch (RuntimeException failure) {
            log.warn("GraphQL execution failed with {}", failure.getClass().getSimpleName());
            return failure("GraphQL execution failed; reduce the query or retry after reactivating the toolkit.", "ExecutionFailed");
        }
    }

    /**
     * Supplies a schema using concrete public Info attributes and private
     * sources.
     *
     * @return Schema definition language for this toolkit.
     */
    protected abstract String getSchemaSdl();

    /**
     * Supplies native data fetchers for domain operations selected by a query.
     *
     * @return Executable wiring, without a custom resolver SPI.
     */
    protected abstract RuntimeWiring buildRuntimeWiring();

    /**
     * Configures the maximum number of nested field levels.
     *
     * @return Default depth of twelve.
     */
    protected int getMaxQueryDepth() {
        return 12;
    }

    /**
     * Configures complexity, including page and nested list cardinality
     * estimates.
     *
     * @return Maximum calculated query complexity.
     */
    protected int getMaxQueryComplexity() {
        return 10_000;
    }

    /**
     * Bounds parser input size independently of field complexity.
     *
     * @return Maximum number of characters in one query document.
     */
    protected int getMaxQueryCharacters() {
        return 20_000;
    }

    /**
     * Bounds actual metadata calls, including aliases and independent child
     * fields.
     *
     * @return Maximum metadata operations in a request.
     */
    protected int getMaxMetadataOperations() {
        return 250;
    }

    /**
     * Configures a cooperative deadline; it cannot interrupt blocking JDBC
     * calls.
     *
     * @return Time allowance in milliseconds.
     */
    protected long getMaxDurationMillis() {
        return 10_000;
    }

    /**
     * Replaces all transient engine state using the toolkit's two native hooks.
     * The volatile engine publication happens after SDL and wiring are
     * complete.
     */
    private void buildEngine() {
        String sdl = getSchemaSdl();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl), buildRuntimeWiring());
        int maxComplexity = getMaxQueryComplexity();
        GraphQL engine = GraphQL.newGraphQL(schema)
                .defaultDataFetcherExceptionHandler(GraphQLToolkit::handleFetchException)
                .instrumentation(new ChainedInstrumentation(List.of(
                        new MaxQueryDepthInstrumentation(getMaxQueryDepth()),
                        new MaxQueryComplexityInstrumentation(maxComplexity,
                                (environment, children) -> fieldComplexity(environment, children, maxComplexity)))))
                .build();
        schemaSdl = sdl;
        graphQL = engine;
    }

    /**
     * Checks executable operation definitions instead of matching string
     * prefixes.
     *
     * @param query Untrusted query text.
     * @return A safe validation error or null when one query operation exists.
     */
    private String validateDocument(String query) {
        if (query == null || query.isBlank() || query.length() > getMaxQueryCharacters()) {
            return "Provide a non-empty GraphQL query within the document size limit.";
        }
        try {
            Document document = Parser.parse(query);
            int operations = 0;
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof OperationDefinition operation) {
                    if (operation.getOperation() != OperationDefinition.Operation.QUERY) {
                        return "Only query operations are allowed; mutation and subscription are unavailable.";
                    }
                    operations++;
                } else if (!(definition instanceof FragmentDefinition)) {
                    return "Only executable GraphQL queries and fragments are allowed.";
                }
            }
            if (operations != 1) {
                return "Provide exactly one GraphQL query operation; operationName selection is unavailable.";
            }
            return null;
        } catch (InvalidSyntaxException failure) {
            log.debug("Rejected invalid GraphQL syntax");
            return "Invalid GraphQL query syntax.";
        } catch (RuntimeException failure) {
            log.debug("GraphQL parser rejected input with {}", failure.getClass().getSimpleName());
            return "GraphQL document parsing limit reached; reduce the query.";
        }
    }

    /**
     * Estimates list fan-out without executing any resolver. A paginated parent
     * multiplies its children by limit; the items field avoids double counting.
     * Unpaginated lists use a conservative estimate of one hundred elements;
     * actual work remains subject to request and domain row budgets.
     *
     * @param environment Coerced field arguments and native schema definition.
     * @param childComplexity Calculated child cost.
     * @param maxComplexity Configured rejection threshold.
     * @return Field cost capped just above the threshold, avoiding subtree
     * overflow.
     */
    private static int fieldComplexity(FieldComplexityEnvironment environment, int childComplexity, int maxComplexity) {
        long multiplier = 1;
        Object limit = environment.getArguments().get("limit");
        if (limit instanceof Number requested) {
            multiplier = Math.max(1L, requested.longValue());
        } else if (GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(environment.getFieldDefinition().getType()))) {
            FieldComplexityEnvironment parent = environment.getParentEnvironment();
            if (parent == null || !parent.getArguments().containsKey("limit")) {
                multiplier = MAX_PAGE_LIMIT;
            }
        }
        return (int) Math.min((long) maxComplexity + 1, 1L + multiplier * Math.max(1, childComplexity));
    }

    /**
     * Sanitizes every uncaught resolver failure before the engine stores it.
     *
     * @param parameters Native failure details, retained only locally.
     * @return A completed handler response with one safe field error.
     */
    private static CompletableFuture<DataFetcherExceptionHandlerResult> handleFetchException(
            DataFetcherExceptionHandlerParameters parameters) {
        Throwable failure = parameters.getException();
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        String message = "The selected field is unavailable; verify the source and retry.";
        String category = "FieldUnavailable";
        if (failure instanceof SafeGraphQLException safe) {
            message = safe.getMessage();
            category = safe.getCategory();
        }
        log.debug("GraphQL field failed with {}", failure.getClass().getSimpleName());
        GraphQLError error = GraphqlErrorBuilder.newError()
                .message(message)
                .path(parameters.getPath())
                .errorType(ErrorType.DataFetchingException)
                .extensions(Map.of(SAFE_ERROR_EXTENSION, true, SAFE_CATEGORY_EXTENSION, category))
                .build();
        return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }

    /**
     * Converts native errors to public DTOs without copying arbitrary
     * extensions, validation values, driver details or stack traces.
     *
     * @param error Native engine error.
     * @return The safe public error.
     */
    private static GraphQLErrorInfo publicError(GraphQLError error) {
        String message = "GraphQL validation or execution limits prevented this selection.";
        String category = error.getErrorType().toString();
        if ("ExecutionAborted".equals(category)) {
            category = "LimitExceeded";
        }
        if (error.getExtensions() != null && Boolean.TRUE.equals(error.getExtensions().get(SAFE_ERROR_EXTENSION))) {
            message = error.getMessage();
            category = String.valueOf(error.getExtensions().get(SAFE_CATEGORY_EXTENSION));
        }
        return new GraphQLErrorInfo(message, category,
                error.getPath() == null ? List.of() : new ArrayList<>(error.getPath()));
    }

    /**
     * Creates a whole-operation failure without throwing into tool stacktrace
     * handling.
     *
     * @param message Safe public message.
     * @param category Stable classification.
     * @return An execution response without data.
     */
    private static GraphQLExecutionInfo failure(String message, String category) {
        return new GraphQLExecutionInfo(null, List.of(new GraphQLErrorInfo(message, category, List.of())));
    }

    /**
     * Copies a response map to a portable ordered map, checking every value.
     *
     * @param source Engine-selected values only.
     * @return JSON-compatible data without internal GraphQL model objects.
     */
    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new SafeGraphQLException("A selected field returned an unsupported response value.");
            }
            result.put(name, normalizeValue(value));
        });
        return result;
    }

    /**
     * Normalizes selected scalar, list and map values recursively.
     *
     * @param value One selected response value.
     * @return A portable JSON-compatible value.
     */
    private static Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(normalizeValue(item));
            }
            return result;
        }
        throw new SafeGraphQLException("A selected field returned an unsupported response value.");
    }
}
