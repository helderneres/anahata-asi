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
 * Hosts an embedded GraphQL engine within the existing Java toolkit protocol.
 * Subclasses provide SDL and native resolver wiring, keeping domain operations
 * in resolvers and public results free of JDBC or IDE handles. Query and command
 * channels share validation, response normalization and cooperative budgets.
 *
 * <p>The framework invokes {@link #initialize()} or {@link #rebind()} before
 * use. Both rebuild the transient engine; subclasses overriding them must call
 * the superclass implementation. Coordinate lifecycle changes with application
 * execution rather than assuming a concurrent schema rebuild is atomic with
 * all subclass state.</p>
 *
 * <p>Execution is synchronous for the caller and may block in resolvers. The
 * caller must use an appropriate background execution context for JDBC or other
 * blocking work instead of running it on the UI event dispatch thread. A fresh
 * {@link GraphQLRequestContext} separates each request's cache and budgets;
 * subclass resolvers remain responsible for their own shared-state safety.</p>
 *
 * <p>Mutations require explicit opt-in and normal tool authorization; there
 * is no HTTP server, generic planner, per-field authorization layer or shared
 * transaction. Resource ownership and effects remain domain responsibilities.</p>
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
     * Converts JSON-style variables to a mutable map without omitting nulls.
     * Conversion uses a local mapper copy and does not alter the shared mapper's
     * configuration. The resulting map preserves explicit nulls when it is later
     * serialized as part of a tool call.
     *
     * @param variables non-null object convertible to a string-keyed variable map
     * @return new mutable variable map with converted nested JSON-style values
     * @throws IllegalArgumentException if the value cannot be converted to the map type
     * @throws NullPointerException if conversion produces a null map
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
     * <p>Rebuilds the schema and transient engine after restoring toolkit context.
     * The domain hooks are called again; rebuilding the engine does not execute a
     * query or open a domain connection.</p>
     *
     * @throws RuntimeException if SDL parsing, schema construction or wiring fails
     */
    @Override
    public void rebind() {
        super.rebind();
        buildEngine();
    }

    /**
     * {@inheritDoc}
     * <p>Builds the executable schema after the Java toolkit context is attached.
     * Subclass lifecycle overrides must call this method before using the engine.</p>
     *
     * @throws RuntimeException if SDL parsing, schema construction or wiring fails
     */
    @Override
    public void initialize() {
        super.initialize();
        buildEngine();
    }

    /**
     * Returns the SDL used to build the currently initialized engine.
     * This discovery call does not fetch domain metadata or evaluate resolvers.
     *
     * @return current SDL, or null before successful initialization or rebinding
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
    /**
     * Executes one named or abbreviated Query operation with optional variables.
     * Mutation, Subscription, multiple operations and non-executable definitions
     * are rejected before resolver execution. Native validation and request limits
     * apply; resolvers acquire only the work associated with selected fields.
     *
     * <p>The caller waits for completion. Partial data can coexist with errors,
     * and a returned response is not an assertion that every field succeeded.</p>
     *
     * @param query GraphQL document; null, blank or oversized input is rejected in the
     *     response
     * @param variables variable map, or null to supply no variables; explicit null
     *     entries remain distinct from omission
     * @return non-null response containing selected data and safe errors
     */
    public GraphQLExecutionInfo query(
            @AgiToolParam("One GraphQL query operation; abbreviated { ... } queries are supported.") String query,
            @AgiToolParam(value = "GraphQL variable values, or an empty map when none are needed.", required = false) Map<String, Object> variables) {
        return executeDocument(query, variables, false);
    }

    /**
     * Executes one Mutation operation when commands are explicitly supported.
     * A subclass must opt in through {@link #supportsMutations()} and expose its
     * own annotated tool method. Once native operation execution is reached, the
     * call must belong to this toolkit's executing mutate tool and satisfy the
     * current toolkit permission checks; a direct Java invocation has no such
     * execution context.
     *
     * <p>Top-level fields follow GraphQL's serial mutation semantics, without a
     * shared transaction, automatic rollback or retry. A later failure does not
     * undo effects already performed. Callers must inspect both data and errors
     * and determine the resulting state before retrying.</p>
     *
     * @param document GraphQL Mutation document with exactly one operation
     * @param variables variable map, or null for no variables
     * @return non-null response, including MutationUnsupported when the subclass has
     *     not opted in
     */
    public GraphQLExecutionInfo mutate(String document, Map<String, Object> variables) {
        if (!supportsMutations()) {
            return failure("This toolkit does not support GraphQL commands.", "MutationUnsupported");
        }
        return executeDocument(document, variables, true);
    }

    /**
     * Declares whether the concrete toolkit supports a command channel.
     * Overriding this hook does not by itself register an annotated tool method
     * or bypass the current tool authorization and preflight checks.
     *
     * @return false by default; true only for an explicitly enabled command toolkit
     */
    protected boolean supportsMutations() {
        return false;
    }

    /**
     * Reports the command opt-in to the existing Java tool protocol.
     * The protocol uses this flag for GraphQL-specific argument conversion and
     * invocation checks; it does not enable commands on ordinary Java toolkits.
     *
     * @return the value supplied by supportsMutations
     */
    public final boolean isCommandToolkit() {
        return supportsMutations();
    }

    /**
     * Validates selected fields after native validation and argument coercion.
     * The engine traverses fragments, aliases and active directives before calling
     * this hook, for both query and mutation operations. The default implementation
     * does nothing. Override it for bounded domain checks before resolver effects;
     * do not execute commands or make irreversible changes in this hook.
     *
     * <p>Completing this hook is not a transaction guarantee: the source can change
     * before resolvers run, and failures may still occur after earlier effects.</p>
     *
     * @param fields unmodifiable list of selected field descriptions; nested argument
     *     values are not deeply immutable
     * @param request execution-local cache and cooperative budget
     * @param mutation true for a Mutation operation, false for a Query
     * @throws SafeGraphQLException to reject the operation with an authored public
     *     error
     */
    protected void validateSelection(List<GraphQLFieldInfo> fields, GraphQLRequestContext request, boolean mutation) {
    }

    /**
     * Executes a validated document with a fresh request cache and budget.
     * Runtime failures at this boundary become a generic response; no automatic
     * retry or rollback is attempted.
     *
     * @param query untrusted operation document
     * @param variables variable values, or null for an empty variable map
     * @param mutation true for the command channel, false for the query channel
     * @return selected data and public errors, or an operation-level failure
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
     * Supplies SDL for the next engine initialization or rebind.
     * The schema must define Query and all types referenced by the runtime wiring.
     * A command-enabled subclass must also supply its Mutation contract. Describe
     * public information types without exposing live domain handles.
     *
     * @return non-null SDL accepted by the native schema parser
     */
    protected abstract String getSchemaSdl();

    /**
     * Supplies native field resolvers and scalar implementations for the SDL.
     * Constructing wiring must not itself execute domain commands. Resolvers own
     * selection-driven work, domain resource cleanup and shared-state safety.
     *
     * @return non-null wiring matching the schema returned by getSchemaSdl
     */
    protected abstract RuntimeWiring buildRuntimeWiring();

    /**
     * Configures the nesting-depth ceiling when the engine is built.
     * Changes to the returned value take effect on initialization or rebind.
     *
     * @return positive maximum depth; 12 by default
     */
    protected int getMaxQueryDepth() {
        return 12;
    }

    /**
     * Configures the estimated field-cost ceiling when the engine is built.
     * The estimate includes selected fields and list cardinality; actual domain
     * work remains bounded separately by request and resolver budgets.
     *
     * @return positive rejection threshold; 10,000 by default
     */
    protected int getMaxQueryComplexity() {
        return 10_000;
    }

    /**
     * Bounds document length before native parsing.
     * This limit applies to the document string, not to the separate variable map.
     *
     * @return maximum Java string length of a document; 20,000 by default
     */
    protected int getMaxQueryCharacters() {
        return 20_000;
    }

    /**
     * Bounds actual metadata operations reserved within each request.
     * Resolvers must consume the request budget for new source work; cached values
     * do not inherently consume another operation.
     *
     * @return maximum metadata reservations per request; 250 by default
     */
    protected int getMaxMetadataOperations() {
        return 250;
    }

    /**
     * Supplies a cooperative duration for each execution's request context.
     * Checks do not forcibly interrupt a blocked driver, supplier or future wait;
     * domain code must also enforce any available source-specific timeouts.
     *
     * @return positive duration in milliseconds; 10,000 by default
     */
    protected long getMaxDurationMillis() {
        return 10_000;
    }

    /**
     * Builds and publishes a new engine from SDL, wiring and limit hooks.
     * The engine reference is assigned only after successful construction. Native
     * schema errors propagate to the lifecycle caller; this method does not
     * serialize concurrent lifecycle changes with subclass state.
     *
     * @throws RuntimeException if parsing, schema generation or engine setup fails
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
     * Checks current command context and validates the native field selection.
     * Arguments have been coerced and active directives resolved by graphql-java.
     * The synthetic __typename field is omitted from the domain field list because
     * it has no ordinary fields container and performs no domain work.
     *
     * @param execution native context for the operation about to execute
     * @throws AbortExecutionException if public permission, selection or budget checks
     *     reject the operation
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
     * Validates executable operation definitions rather than matching a prefix.
     * Exactly one operation must use the requested channel; additional fragments
     * are permitted. Schema and variable-type validation remain engine work.
     *
     * @param query untrusted document, including null
     * @param mutation true to require Mutation, false to require Query
     * @return public validation message, or null when document structure is accepted
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
     * Estimates selected-field fan-out without invoking domain resolvers.
     * A limit argument multiplies child cost; an unbounded list uses the default
     * maximum page limit unless a parent limit already accounts for its items.
     * Subclasses may specialize known domain cardinalities, while retaining
     * conservative estimates and independent limits on actual work.
     *
     * @param environment coerced arguments and native field definition
     * @param childComplexity estimated combined child cost
     * @param maxComplexity configured rejection threshold
     * @return estimated field cost, capped just above the configured threshold
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
     * Copies selected response entries into an ordered, string-keyed map.
     * Nested maps and lists are normalized recursively; the original map is not
     * retained in the returned graph.
     *
     * @param source non-null engine-selected response entries
     * @return new ordered map of supported public values
     * @throws SafeGraphQLException if a key is not a string or a value is unsupported
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
     * Retains supported scalar values and copies nested maps and lists.
     * Numbers are retained as supplied; this method does not impose a numeric range.
     *
     * @param value selected scalar, map, list or null
     * @return supported scalar or newly normalized container, including null
     * @throws SafeGraphQLException if value has an unsupported type
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
