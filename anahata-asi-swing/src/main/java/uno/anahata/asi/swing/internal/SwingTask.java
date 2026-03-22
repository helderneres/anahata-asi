/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.internal;

import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.swing.components.ExceptionDialog;

/**
 * A robust, specialized {@link SwingWorker} implementation for executing 
 * background tasks with integrated lifecycle management and UI feedback.
 * 
 * <p>SwingTask provides a clean, functional API for handling the transition 
 * between the background execution thread and the Event Dispatch Thread (EDT), 
 * with built-in support for success/error callbacks and automatic error 
 * dialog presentation.</p>
 * 
 * @param <T> The result type produced by the background task.
 * @author anahata
 */
@Slf4j
@Getter
@Setter
public class SwingTask<T> extends SwingWorker<T, Void> {
    
    /** The parent component used as a reference for positioning modal dialogs. */
    private Component owner;
    /** A human-readable identifier for the task, displayed in progress and error reports. */
    private String taskName;
    /** The functional core of the task, to be executed on a background worker thread. */
    private Callable<T> backgroundTask;
    /** The EDT callback for handling successful results. */
    private Consumer<T> onDone;
    /** The EDT callback for handling execution failures. */
    private Consumer<Exception> onError;    
    /** Flag to enable/disable the automatic presentation of a modal ExceptionDialog. */
    private boolean showError;

    /**
     * Constructs a fully configured SwingTask.
     * 
     * @param owner The parent component.
     * @param taskName The task name.
     * @param backgroundTask The background logic.
     * @param onDone Success callback.
     * @param onError Error callback.
     * @param showError Whether to show dialog on error.
     */
    public SwingTask(Component owner, String taskName, Callable<T> backgroundTask, Consumer<T> onDone, Consumer<Exception> onError, boolean showError) {
        this.owner = owner;
        this.taskName = taskName;
        this.backgroundTask = backgroundTask;
        this.onDone = onDone;
        this.onError = onError;
        this.showError = showError;
    }

    /**
     * Constructs a SwingTask with default error dialog visibility enabled.
     * 
     * @param owner The parent component.
     * @param taskName The task name.
     * @param backgroundTask The background logic.
     * @param onDone Success callback.
     * @param onError Error callback.
     */
    public SwingTask(Component owner, String taskName, Callable<T> backgroundTask, Consumer<T> onDone, Consumer<Exception> onError) {
        this(owner, taskName, backgroundTask, onDone, onError, true);
    }

    /**
     * Constructs a SwingTask with only a success callback.
     * 
     * @param owner The parent component.
     * @param taskName The task name.
     * @param backgroundTask The background logic.
     * @param onDone Success callback.
     */
    public SwingTask(Component owner, String taskName, Callable<T> backgroundTask, Consumer<T> onDone) {
        this(owner, taskName, backgroundTask, onDone, null, true);
    }

    /**
     * Constructs a SwingTask with no explicit callbacks.
     * 
     * @param owner The parent component.
     * @param taskName The task name.
     * @param backgroundTask The background logic.
     */
    public SwingTask(Component owner, String taskName, Callable<T> backgroundTask) {
        this(owner, taskName, backgroundTask, null, null, true);
    }

    /** 
     * {@inheritDoc} 
     * <p>Delegates execution to the provided {@code backgroundTask} callable.</p> 
     */
    @Override
    protected T doInBackground() throws Exception {
        return backgroundTask.call();
    }

    /** 
     * {@inheritDoc} 
     * <p>
     * Performs clean-up and result propagation on the EDT. This method 
     * centralizes the logic for unwrapping {@code ExecutionException}s and 
     * triggering the appropriate success or error callbacks.
     * </p> 
     */
    @Override
    protected void done() {
        try {
            T result = get();
            if (onDone != null) {
                onDone.accept(result);
            }
        } catch (InterruptedException | ExecutionException e) {
            if (showError) {
                ExceptionDialog.show(owner, taskName, "An error occurred during background task " + taskName, e);
            }
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
}
