/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.agi.context.gc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.JComponent;
import uno.anahata.asi.agi.context.ContextWindowGarbageCollector;

/**
 * A high-fidelity, reactive donut chart that visualizes the categorized token distribution
 * of an AGI session's prompt.
 * <p>
 * This component renders segments representing different token types (System, Tools, Metadata, 
 * History, and RAG) and provides a central capacity indicator to help users monitor 
 * the "metabolic load" of their context window relative to the model's threshold.
 * </p>
 * 
 * @author anahata
 */
public class MetabolicDonutChart extends JComponent {

    /** The latest token metabolism statistics. */
    private ContextWindowGarbageCollector.Stats stats;
    /** The current token capacity limit of the session. */
    private int threshold;

    /**
     * Updates the chart data and triggers a repaint of the visualization.
     * 
     * @param stats The latest token metabolism statistics.
     * @param threshold The current token capacity limit of the session.
     */
    public void update(ContextWindowGarbageCollector.Stats stats, int threshold) {
        this.stats = stats;
        this.threshold = threshold;
        repaint();
    }

    /** 
     * {@inheritDoc} 
     * <p>Performs a high-fidelity rendering of the metabolic donut chart. 
     * It draws segments for System, Tools, Metadata, History, and RAG tokens 
     * relative to the total prompt load, while providing a capacity 
     * percentage in the center of the ring.</p> 
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (stats == null) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.min(getWidth(), getHeight()) - 60;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        int stroke = 50;
        // Background Ring
        g2d.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(236, 240, 241));
        g2d.drawOval(x + stroke / 2, y + stroke / 2, size - stroke, size - stroke);
        double total = stats.getTotalPromptLoad();
        if (total == 0) {
            total = 1;
        }
        double currentAngle = 90;
        // 1. System (Blue)
        currentAngle = drawArc(g2d, x, y, size, stroke, currentAngle, stats.getSystemInstructionsTokens() / total, new Color(52, 152, 219));
        // 2. Tools (Purple)
        currentAngle = drawArc(g2d, x, y, size, stroke, currentAngle, stats.getToolDeclarationsTokens() / total, new Color(155, 89, 182));
        // 3. Metadata (Orange)
        currentAngle = drawArc(g2d, x, y, size, stroke, currentAngle, stats.getMetadataTokens() / total, new Color(243, 156, 18));
        // 4. History (Green)
        currentAngle = drawArc(g2d, x, y, size, stroke, currentAngle, stats.getActiveHistoryTokens() / total, new Color(46, 204, 113));
        // 5. RAG (Turquoise)
        currentAngle = drawArc(g2d, x, y, size, stroke, currentAngle, stats.getRagTokens() / total, new Color(26, 188, 156));
        // Inner Ring: Pruned (Grey)
        if (stats.getPrunedHistoryTokens() > 0) {
            g2d.setStroke(new BasicStroke(15, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(189, 195, 199));
            g2d.drawOval(x + stroke + 15, y + stroke + 15, size - (stroke * 2) - 30, size - (stroke * 2) - 30);
        }
        // Center Text
        double usagePct = Math.min(1.0, total / threshold);
        g2d.setFont(getFont().deriveFont(Font.BOLD, 28f));
        String usageText = (int) (usagePct * 100) + "%";
        int tw = g2d.getFontMetrics().stringWidth(usageText);
        g2d.setColor(usagePct > 0.9 ? new Color(192, 57, 43) : Color.DARK_GRAY);
        g2d.drawString(usageText, getWidth() / 2 - tw / 2, getHeight() / 2 + 10);
        g2d.setFont(getFont().deriveFont(12f));
        g2d.setColor(Color.GRAY);
        String subText = "CAPACITY";
        int stw = g2d.getFontMetrics().stringWidth(subText);
        g2d.drawString(subText, getWidth() / 2 - stw / 2, getHeight() / 2 + 30);
        g2d.dispose();
    }

    /**
     * Orchestrates the drawing of a single categorized segment of the donut chart.
     * 
     * @param g2d The graphics context.
     * @param x The x coordinate of the bounding box.
     * @param y The y coordinate of the bounding box.
     * @param size The diameter of the chart.
     * @param stroke The width of the ring.
     * @param startAngle The starting angle for this segment.
     * @param pct The percentage of the total load this segment represents.
     * @param color The color associated with the token category.
     * @return The ending angle of this segment, used as the starting angle for the next one.
     */
    private double drawArc(Graphics2D g2d, int x, int y, int size, int stroke, double startAngle, double pct, Color color) {
        if (pct <= 0) {
            return startAngle;
        }
        double angle = pct * 360.0;
        g2d.setColor(color);
        g2d.draw(new Arc2D.Double(x + stroke / 2, y + stroke / 2, size - stroke, size - stroke, startAngle, -angle, Arc2D.OPEN));
        return startAngle - angle;
    }
    
}
