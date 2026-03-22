/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi.input;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.message.InputUserMessage;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.message.AbstractMessagePanel;

/**
 * A panel that provides a live, read-only preview of the user's input message,
 * including markdown rendering of the text part and a display of any attached
 * blob parts.
 * <p>
 * This component also provides high-fidelity scrolling methods to ensure that 
 * new attachments (like screenshots) are immediately visible to the user.
 * </p>
 *
 * @author anahata
 */
@Slf4j
public class InputUserMessagePanel extends AbstractMessagePanel<InputUserMessage> implements Scrollable {

    /**
     * Constructs a new InputMessagePanel.
     *
     * @param agiPanel The parent agi panel.
     * @param message The mutable message instance.
     */
    public InputUserMessagePanel(@NonNull AgiPanel agiPanel, @NonNull InputUserMessage message) {
        super(agiPanel, message);
    }

    /**
     * Surgically scrolls the containing scroll pane to the absolute bottom.
     * Uses a double-dispatch trick to ensure layout has finished before 
     * capturing the maximum scroll position.
     */
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            // First pass triggers the render reaction.
            // Second pass captures the finalized layout height.
            SwingUtilities.invokeLater(() -> {
                Container parent = getParent();
                if (parent instanceof JViewport viewport && viewport.getParent() instanceof JScrollPane scrollPane) {
                    JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                    verticalBar.setValue(verticalBar.getMaximum());
                }
            });
        });
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the theme-specific starting gradient color for user message headers.</p> 
     */
    @Override
    protected Color getHeaderStartColor() {
        return agiConfig.getTheme().getUserHeaderBg();
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the theme-specific ending gradient color for user message headers, 
     * typically blending into the content background.</p> 
     */
    @Override
    protected Color getHeaderEndColor() {
        return agiConfig.getTheme().getUserContentBg();
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns the theme-specific text color for user message headers.</p> 
     */
    @Override
    protected Color getHeaderForegroundColor() {
        return agiConfig.getTheme().getUserHeaderFg();
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns a specialized border for user messages, often rounded to 
     * distinguish them from model responses.</p> 
     */
    @Override
    protected Border getMessageBorder() {
        return BorderFactory.createLineBorder(agiConfig.getTheme().getUserBorder(), 2, true);
    }

    // --- Scrollable Implementation ---

    /** 
     * {@inheritDoc} 
     * <p>Delegates the preferred size calculation to the standard Swing component logic.</p> 
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /** 
     * {@inheritDoc} 
     * <p>Provides a standardized unit increment for smooth vertical scrolling.</p> 
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    /** 
     * {@inheritDoc} 
     * <p>Provides block increments equal to the visible viewport height for efficient paging.</p> 
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
    }

    /** 
     * {@inheritDoc} 
     * <p>Always returns true to ensure the preview panel wraps text and 
     * fits within the horizontal bounds of the scroll pane.</p> 
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /** 
     * {@inheritDoc} 
     * <p>Returns true only if the preferred height is smaller than the viewport, 
     * allowing the panel to stretch and fill the space.</p> 
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (getParent() instanceof JViewport viewport) {
            return viewport.getHeight() > getPreferredSize().height;
        }
        return false;
    }
}
