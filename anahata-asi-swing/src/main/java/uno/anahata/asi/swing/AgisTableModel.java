/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import lombok.NonNull;
import uno.anahata.asi.AsiContainer;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.status.AgiStatus;

/**
 * A reusable table model for displaying active AI agi sessions.
 * This model tracks the {@link AsiContainer} and provides real-time updates
 * on session status, message count, and context usage.
 * 
 * @author gemini-3-flash-preview
 */
public class AgisTableModel extends AbstractTableModel {

    /** The list of active agi sessions being tracked. */
    private final List<Agi> sessions = new ArrayList<>();
    /** The localized column names for the table. */
    private final String[] columnNames = {"Nickname", "ID", "Status", "Msgs", "Context %"};
    /** The container configuration providing session data. */
    private final AsiContainer asiConfig;
    /** The listener for changes in the container's session list. */
    private final PropertyChangeListener asiListener = this::handleAsiChange;

    /** The column index for the session name. */
    public static final int SESSION_COL = 0;
    /** The column index for the session ID. */
    public static final int ID_COL = 1;
    /** The column index for the session status. */
    public static final int STATUS_COL = 2;
    /** The column index for the message count. */
    public static final int MESSAGES_COL = 3;
    /** The column index for context window usage. */
    public static final int CONTEXT_COL = 4;

    /** 
     * Constructs a new model and registers a listener on the provided container.
     * 
     * @param asiConfig The container to track.
     */
    public AgisTableModel(@NonNull AsiContainer asiConfig) {
        this.asiConfig = asiConfig;
        refresh();
        asiConfig.addPropertyChangeListener(asiListener);
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the number of active sessions in the tracked container.</p> 
     */
    @Override
    public int getRowCount() {
        return sessions.size();
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the number of columns defined in the columnNames array.</p> 
     */
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the localized name for the specified column.</p> 
     */
    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the appropriate class for each column to enable specialized renderers (e.g., status colors).</p> 
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case MESSAGES_COL:
                return Integer.class;
            case CONTEXT_COL:
                return Double.class;
            case STATUS_COL:
                return AgiStatus.class;
            default:
                return String.class;
        }
    }

    /** 
     * {@inheritDoc} 
     * <p>Extracts session data based on the column index (nickname, id, status, messages, usage).</p> 
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= sessions.size()) {
            return null;
        }
        Agi agi = sessions.get(rowIndex);

        switch (columnIndex) {
            case SESSION_COL:
                return agi.getDisplayName();
            case ID_COL:
                return agi.getShortId();
            case STATUS_COL:
                return agi.getStatusManager().getCurrentStatus();
            case MESSAGES_COL:
                return agi.getContextManager().getHistory().size();
            case CONTEXT_COL:
                return agi.getContextWindowUsage();
            default:
                return null;
        }
    }

    /**
     * Refreshes the table model by synchronizing with the {@link AsiContainer}.
     */
    public final void refresh() {
        List<Agi> activeAgis = asiConfig.getActiveAgis();
        
        // Identify removed sessions
        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (!activeAgis.contains(sessions.get(i))) {
                sessions.remove(i);
                fireTableRowsDeleted(i, i);
            }
        }

        // Identify added sessions
        for (int i = 0; i < activeAgis.size(); i++) {
            Agi agi = activeAgis.get(i);
            if (!sessions.contains(agi)) {
                sessions.add(i, agi);
                fireTableRowsInserted(i, i);
            }
        }

        // Identify updated rows
        if (!sessions.isEmpty()) {
            fireTableRowsUpdated(0, sessions.size() - 1);
        }
    }

    /** 
     * Retrieves the agi session at the specified row index.
     * 
     * @param row The model row index.
     * @return The session, or null if the index is out of bounds.
     */
    public Agi getAgiAt(int row) {
        if (row >= 0 && row < sessions.size()) {
            return sessions.get(row);
        }
        return null;
    }

    /** 
     * Handles property change events from the ASI container to trigger a refresh.
     * 
     * @param evt The property change event.
     */
    private void handleAsiChange(PropertyChangeEvent evt) {
        if ("activeAgis".equals(evt.getPropertyName())) {
            refresh();
        }
    }
    
    /** 
     * Unregisters the listener from the container and cleans up the model.
     */
    public void dispose() {
        asiConfig.removePropertyChangeListener(asiListener);
    }
}
