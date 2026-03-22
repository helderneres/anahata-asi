/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.internal;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A utility {@link DocumentListener} that fires a single callback for any type of
 * document change (insert or remove). 
 * <p>
 * This class is a key component of the <b>Reactive UI</b> strategy, simplifying 
 * the binding between {@code JTextField}s and domain model properties by 
 * consolidating granular document events into a single "dirty" notification.
 * </p>
 *
 * @author anahata
 */
public class AnyChangeDocumentListener implements DocumentListener {

    /** The reactive callback triggered on every document modification. */
    private final Runnable onChange;

    /**
     * Creates a new listener.
     *
     * @param onChange The {@link Runnable} to execute when the document changes.
     */
    public AnyChangeDocumentListener(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 
     * {@inheritDoc} 
     * <p>Triggers the unified change callback upon text insertion.</p> 
     */
    @Override
    public void insertUpdate(DocumentEvent e) {
        onChange.run();
    }

    /** 
     * {@inheritDoc} 
     * <p>Triggers the unified change callback upon text removal.</p> 
     */
    @Override
    public void removeUpdate(DocumentEvent e) {
        onChange.run();
    }

    /** 
     * {@inheritDoc} 
     * <p>
     * Triggers the unified change callback for attribute changes. Note that 
     * plain text components rarely fire this, but it is handled for architectural 
     * completeness.
     * </p> 
     */
    @Override
    public void changedUpdate(DocumentEvent e) {
        // Plain text components do not fire this event, but we call it for completeness.
        onChange.run();
    }
}
