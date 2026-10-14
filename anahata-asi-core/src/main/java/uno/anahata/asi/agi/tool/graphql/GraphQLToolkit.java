/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;
import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitorStub;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
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
import uno.anahata.asi.agi.tool.ToolPermission;
import uno.anahata.asi.agi.tool.ToolExecutionStatus;

/**
 * Embedded GraphQL infrastructure using the existing Java toolkit protocol.
 * Native resolvers perform work only for selected fields; this class provides
 * engine lifecycle, separate query/command validation and shared resource
 * limits.
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
     * This local conversion policy preserves explicit null variables; the
     * shared mapper is unchanged.
     */
    private static final ObjectMapper VARIABLES_MAPPER = SchemaProvider.OBJECT_MAPPER.copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    /**
     * Copies JSON variable values without turning explicit null into an
     * omitted/default value.
     */
    public final Map<String, Object> copyVariables(Object variables) {
        Map<String, Object> copied = VARIABLES_MAPPER.convertValue(variables, new TypeReference<Map<String, Object>>() {
        });
        return new GraphQLVariables(copied);
    }

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
    @AgiTool(value = "Returns this toolkit's GraphQL schema. Discover Query fields and any explicitly supported Mutation commands.")
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
        return executeDocument(query, variables, false);
    }

    /**
     * Executes a command document only when a concrete toolkit deliberately
     * supports mutations and exposes this method through its own @AgiTool.
     * Existing read-only subclasses acquire no additional advertised tool.
     */
    public GraphQLExecutionInfo mutate(String document, Map<String, Object> variables) {
        if (!supportsMutations()) {
            return failure("This toolkit does not support GraphQL commands.", "MutationUnsupported");
        }
        return executeDocument(document, variables, true);
    }

    /**
     * Opt-in for concrete command toolkits; the default remains query-only.
     */
    protected boolean supportsMutations() {
        return false;
    }

    /**
     * Explicit opt-in used by the Java protocol; ordinary toolkits are
     * unchanged.
     */
    public final boolean isCommandToolkit() {
        return supportsMutations();
    }

    /**
     * Validates all selected fields before any resolver, after native schema,
     * variable and directive processing. Domain overrides must not perform
     * effects.
     */
    protected void validateSelection(List<GraphQLFieldInfo> fields, GraphQLRequestContext request, boolean mutation) {
    }

    /**
     * Executes a single operation on its explicit channel.
     */
    private GraphQLExecutionInfo executeDocument(String query, Map<String, Object> variables, boolean mutation) {
        String validationError = validateDocument(query, mutation);
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
                                (environment, children) -> fieldComplexity(environment, children, maxComplexity)),
                        new SimplePerformantInstrumentation() {
                    @Override
                    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
                            InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
                        preflight(parameters.getExecutionContext());
                        return null;
                    }
                })))
                .build();
        schemaSdl = sdl;
        graphQL = engine;
    }

    /**
     * Native traversal resolves fragments, aliases, directives and coerced
     * arguments before any field executes. All commands are gated together.
     */
    private void preflight(ExecutionContext execution) {
        boolean mutation = execution.isMutationOperation();
        try {
            if (mutation) {
                var response = peekResponse();
                if (response == null || response.getStatus() != ToolExecutionStatus.EXECUTING
                        || !response.getCall().getToolName().endsWith(".mutate")
                        || response.getCall().getTool().getToolkitInstance() != this
                        || !response.getCall().getTool().getToolkit().isEnabled()
                        || response.getCall().getTool().getPermission() == null
                        || response.getCall().getTool().getPermission() == ToolPermission.DENY) {
                    throw new SafeGraphQLException("PermissionDenied", "Commands require an authorized invocation of this toolkit's mutate tool.");
                }
            }
            List<GraphQLFieldInfo> fields = new ArrayList<>();
            QueryTraverser.newQueryTraverser().schema(execution.getGraphQLSchema())
                    .document(execution.getDocument())
                    .operationName(execution.getOperationDefinition().getName())
                    .coercedVariables(execution.getCoercedVariables()).build()
                    .visitPreOrder(new QueryVisitorStub() {
                        @Override
                        public void visitField(QueryVisitorFieldEnvironment field) {
                            if (field.isTypeNameIntrospectionField()) {
                                return; // Native __typename has no fields container and performs no domain work.
                            }
                            fields.add(new GraphQLFieldInfo(field.getFieldsContainer() == null ? "" : field.getFieldsContainer().getName(),
                                    field.getFieldDefinition().getName(), field.getField().getResultKey(), field.getArguments()));
                        }
                    });
            GraphQLRequestContext request = execution.getGraphQLContext().get(GraphQLRequestContext.class);
            request.checkBudget();
            validateSelection(List.copyOf(fields), request, mutation);
        } catch (SafeGraphQLException failure) {
            throw new AbortExecutionException(List.of(GraphqlErrorBuilder.newError()
                    .message(failure.getMessage()).errorType(ErrorType.DataFetchingException)
                    .extensions(Map.of(SAFE_ERROR_EXTENSION, true, SAFE_CATEGORY_EXTENSION, failure.getCategory())).build()));
        }
    }

    /**
     * Checks executable operation definitions instead of matching string
     * prefixes.
     *
     * @param query Untrusted query text.
     * @return A safe validation error or null when one query operation exists.
     */
    private String validateDocument(String query, boolean mutation) {
        if (query == null || query.isBlank() || query.length() > getMaxQueryCharacters()) {
            return "Provide a non-empty GraphQL query within the document size limit.";
        }
        try {
            Document document = Parser.parse(query);
            int operations = 0;
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof OperationDefinition operation) {
                    OperationDefinition.Operation expected = mutation ? OperationDefinition.Operation.MUTATION : OperationDefinition.Operation.QUERY;
                    if (operation.getOperation() != expected) {
                        return mutation ? "mutate() accepts only Mutation operations; Query and Subscription are unavailable on this channel."
                                : "query() accepts only Query operations; use the explicit command channel for Mutation.";
                    }
                    operations++;
                } else if (!(definition instanceof FragmentDefinition)) {
                    return "Only executable GraphQL queries and fragments are allowed.";
                }
            }
            if (operations != 1) {
                return "Provide exactly one GraphQL operation; operationName selection is unavailable.";
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
    protected int fieldComplexity(FieldComplexityEnvironment environment, int childComplexity, int maxComplexity) {
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
