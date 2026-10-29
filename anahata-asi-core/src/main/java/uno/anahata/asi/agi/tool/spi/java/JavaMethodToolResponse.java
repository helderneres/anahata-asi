/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.spi.java;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.asi.agi.tool.spi.AbstractToolResponse;
import uno.anahata.asi.agi.tool.ToolExecutionStatus;
import uno.anahata.asi.agi.tool.AgiToolException;
import uno.anahata.asi.agi.tool.graphql.GraphQLToolkit;
import uno.anahata.asi.agi.tool.graphql.GraphQLArgumentBindingException;
import uno.anahata.asi.agi.tool.ToolPermission;

/**
 * A rich POJO that captures the complete and final outcome of a single tool
 * call. This class now follows a deferred execution model and manages the
 * thread-local context for Java tool execution.
 *
 * @author anahata-gemini-pro-2.5
 */
@Getter
@Slf4j
public class JavaMethodToolResponse extends AbstractToolResponse<JavaMethodToolCall> {

    /**
     * A thread-local storage for the currently executing Java tool response.
     * This allows tool logic to access its own context without explicit
     * parameter passing.
     */
    private static final ThreadLocal<JavaMethodToolResponse> current = new ThreadLocal<>();

    /**
     * Gets the tool response associated with the current thread.
     *
     * @return The active tool response, or {@code null} if none is active.
     */
    public static JavaMethodToolResponse getCurrent() {
        return current.get();
    }

    /**
     * Sets the tool response for the current thread.
     *
     * @param response The response to set, or {@code null} to clear the
     * context.
     */
    public static void setCurrent(JavaMethodToolResponse response) {
        if (response == null) {
            current.remove();
        } else {
            current.set(response);
        }
    }

    /**
     * Last caught invocation failure, retained only for local diagnostics. This
     * transient field is excluded from JSON; persisted outcomes are represented
     * by the response's result, status and public error text.
     */
    @JsonIgnore
    private transient Throwable exception;

    /**
     * Guard to ensure only one thread can execute this response at a time.
     */
    @JsonIgnore
    private transient AtomicBoolean executing = new AtomicBoolean(false);

    /**
     * Constructs a new JavaMethodToolResponse for a specific call.
     *
     * @param call The originating tool call.
     */
    public JavaMethodToolResponse(@NonNull JavaMethodToolCall call) {
        super(call);
        setStatus(ToolExecutionStatus.PENDING);
    }

    /**
     * Rebinds inherited state and recreates the transient execution guard. The
     * inherited hook marks a restored executing response as interrupted; it
     * does not resume that invocation. Call this hook while the response is
     * inactive, not during an invocation.
     */
    @Override
    public void rebind() {
        super.rebind();
        this.executing = new AtomicBoolean(false);
    }

    /**
     * Invokes the tool synchronously on the calling thread and records its
     * outcome. The caller must arrange background execution for potentially
     * blocking tools; this method does not schedule work on a worker or Swing
     * event thread.
     * <p>
     * The invocation establishes a thread-local response and an executing-call
     * registration, and clears both in the normal invocation cleanup. For
     * command GraphQL toolkits, it rejects a response already marked failed, a
     * disabled toolkit or a denied/undefined permission. Interactive approval
     * remains the responsibility of the existing call-dispatch flow; this
     * method is not a new approval mechanism.
     * </p>
     * <p>
     * GraphQL command toolkits use the captured argument edits and copy
     * variable maps before invocation, preserving explicit nulls. Callers must
     * not mutate argument structures concurrently. A returned GraphQL result
     * may contain field errors even when this response has status
     * {@link ToolExecutionStatus#EXECUTED}; inspect the result's data and
     * errors. Failures caught during invocation are recorded in this response
     * instead of being retried.
     * </p>
     *
     * @throws IllegalStateException if this response already has an invocation
     * in progress
     */
    @Override
    public void execute() {
        if (getStatus() == ToolExecutionStatus.FAILED
                && getCall().getTool().getToolkitInstance() instanceof GraphQLToolkit gql && gql.isCommandToolkit()) {
            addError("Issue a new GraphQL tool call after a protocol rejection; rejected arguments cannot be executed.");
            return;
        }
        if (!executing.compareAndSet(false, true)) {
            throw new IllegalStateException("Tool execution is already in progress for this call.");
        }

        long startTime = System.currentTimeMillis();
        addLog("Execution start time: " + LocalDateTime.now());
        setExpanded(true);
        setCurrent(this); // Establish the thread-local context
        setStatus(ToolExecutionStatus.EXECUTING);
        setThread(Thread.currentThread());
        getAgi().getToolManager().registerExecutingCall(getCall());

        // Capture a snapshot of the modified arguments at the time of execution
        getModifiedArgs().clear();
        getModifiedArgs().putAll(getCall().getModifiedArgs());

        try {
            JavaMethodTool tool = getCall().getTool();
            Object toolkitInstance = tool.getToolkitInstance();
            boolean graphqlCommands = toolkitInstance instanceof GraphQLToolkit gql
                    && gql.isCommandToolkit();
            if (graphqlCommands) {
                if (!tool.getToolkit().isEnabled() || tool.getPermission() == ToolPermission.DENY || tool.getPermission() == null) {
                    throw new AgiToolException("Tool execution is denied by the current toolkit permission.");
                }
            }

            var method = tool.getMethod();
            Parameter[] methodParameters = method.getParameters();
            Object[] argsToInvoke = new Object[methodParameters.length];
            Map<String, Object> effectiveArgs = graphqlCommands ? getExecutedArgs() : getCall().getEffectiveArgs();

            for (int i = 0; i < methodParameters.length; i++) {
                Parameter p = methodParameters[i];
                String paramName = p.getName();
                Object value = effectiveArgs.get(paramName);
                if (graphqlCommands && "variables".equals(paramName) && value != null) {
                    try {
                        GraphQLArgumentBindingException.requireObject(value);
                        value = ((GraphQLToolkit) toolkitInstance).copyVariables(value);
                    } catch (IllegalArgumentException invalidVariables) {
                        throw new AgiToolException(invalidVariables instanceof GraphQLArgumentBindingException
                                ? invalidVariables.getMessage()
                                : "Invalid GraphQL variables (phase ARGUMENT_BINDING; argument variables; expected object; effects NOT_STARTED).");
                    }
                    if (getModifiedArgs().containsKey(paramName)) {
                        getModifiedArgs().put(paramName, value); // Keeps the execution snapshot's explicit nulls in history.
                    }
                }
                argsToInvoke[i] = value;
            }

            Object result = method.invoke(toolkitInstance, argsToInvoke);

            setResult(result);
            setStatus(ToolExecutionStatus.EXECUTED);

        } catch (Throwable e) {
            boolean graphQLFailure = getCall().getTool().getToolkitInstance() instanceof GraphQLToolkit gql && gql.isCommandToolkit();
            if (!graphQLFailure) {
                log.error("Exception executing tool " + getCall(), e);
            }
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
            this.exception = cause;

            if (cause instanceof InterruptedException) {
                log.info("Tool execution interrupted: {}", getCall().getToolName());
                addError("Execution interrupted by user.");
                setStatus(ToolExecutionStatus.INTERRUPTED);
                Thread.currentThread().interrupt();
            } else {
                if (graphQLFailure) {
                    log.warn("GraphQL tool invocation failed: {} ({})", getCall().getToolName(), cause.getClass().getSimpleName());
                } else {
                    log.error("Tool execution failed for: {}", getCall().getToolName(), cause);
                }

                if (cause instanceof AgiToolException) {
                    addError(cause.getMessage());
                } else if (getCall().getTool().getToolkitInstance() instanceof GraphQLToolkit gql && gql.isCommandToolkit()) {
                    addError("GraphQL tool invocation failed. Verify the document and variables; inspect current permissions before issuing a new call.");
                } else {
                    addError(ExceptionUtils.getStackTrace(cause));
                }
                setStatus(ToolExecutionStatus.FAILED);
            }

        } finally {
            getAgi().getToolManager().unregisterExecutingCall(getCall());
            setCurrent(null); // Clear the context
            setThread(null);
            long duration = System.currentTimeMillis() - startTime;
            setExecutionTimeMillis(duration);
            addLog("Execution end time: " + LocalDateTime.now() + " (duration: " + duration + " ms)");
            executing.set(false);
        }
    }

    /**
     * Requests interruption of this response's current execution thread. This
     * does not wait for termination or forcibly abort a blocking driver call;
     * the invoked tool and its dependencies must cooperate with interruption.
     *
     * @throws IllegalStateException if no live execution thread is associated
     * with this response
     */
    @Override
    public void stop() {
        Thread t = getThread();
        if (t == null || !t.isAlive()) {
            throw new IllegalStateException("Tool is not currently executing.");
        }
        log.info("Stopping tool execution thread: {}", t.getName());
        t.interrupt();
    }
}
