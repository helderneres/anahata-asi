/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.context.ContextManager;

/**
 * A specialized panel for monitoring the Context Window Garbage Collection (CwGC) 
 * status, showing token recycling metrics and pruned content overhead with 
 * a high-fidelity donut chart.
 * 
 * @author anahata
 */
public class CwGcPanel extends JPanel {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();

    private final AgiPanel agiPanel;
    private Agi agi;
    private final Timer refreshTimer;

    private JLabel prunedTokensLabel;
    private JLabel prunedMetadataTokensLabel;
    private JLabel activeTokensLabel;
    private JLabel totalContextTokensLabel;
    
    private MetabolicDonutChart donutChart;

    public CwGcPanel(@NonNull AgiPanel agiPanel) {
        setLayout(new BorderLayout());
        this.agiPanel = agiPanel;
        this.agi = agiPanel.getAgi();
        
        initComponents();
        
        this.refreshTimer = new Timer(2000, e -> refresh());
    }

    private void initComponents() {
        JPanel content = new JPanel(new MigLayout("insets 20, fill, gap 20", "[300!][grow]", "[grow]"));
        content.setBorder(BorderFactory.createTitledBorder("CwGC Metabolic Status"));

        // --- Left side: Metrics Breakdown ---
        JPanel metricsPanel = new JPanel(new MigLayout("insets 0, fillx, gap 5", "[right][left, grow]", "[]15[]15[]15[]20[]"));
        
        Font valueFont = new JLabel().getFont().deriveFont(Font.BOLD, 14f);

        metricsPanel.add(new JLabel("Active Content:"), "gapbottom 5");
        activeTokensLabel = new JLabel("0");
        activeTokensLabel.setFont(valueFont);
        activeTokensLabel.setForeground(new Color(46, 204, 113)); // Green
        metricsPanel.add(activeTokensLabel, "wrap");

        metricsPanel.add(new JLabel("Metadata Overhead:"), "gapbottom 5");
        prunedMetadataTokensLabel = new JLabel("0");
        prunedMetadataTokensLabel.setFont(valueFont);
        prunedMetadataTokensLabel.setForeground(new Color(243, 156, 18)); // Orange
        metricsPanel.add(prunedMetadataTokensLabel, "wrap");

        metricsPanel.add(new JLabel("Pruned (Recycled):"), "gapbottom 5");
        prunedTokensLabel = new JLabel("0");
        prunedTokensLabel.setFont(valueFont);
        prunedTokensLabel.setForeground(new Color(149, 165, 166)); // Grey
        metricsPanel.add(prunedTokensLabel, "wrap");
        
        metricsPanel.add(new JLabel("Total Window Load:"), "gapbottom 5");
        totalContextTokensLabel = new JLabel("0");
        totalContextTokensLabel.setFont(valueFont);
        metricsPanel.add(totalContextTokensLabel, "wrap");

        metricsPanel.add(new JLabel("<html><br><b>Indefinite History</b><br>"
                + "History remains physically intact. Only the <b>Prompt Load</b> "
                + "is sent to the model. CwGC ensures your prompt stays under "
                + "the threshold by recycling content into hints.</html>"), "span 2, growx");

        content.add(metricsPanel, "top");

        // --- Right side: Visualization ---
        donutChart = new MetabolicDonutChart();
        content.add(donutChart, "grow");

        add(content, BorderLayout.CENTER);
        
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel info = new JLabel("<html><b>Metabolism Summary:</b> The <font color='#2ecc71'><b>Active Content</b></font> is what the AI is focused on. "
                + "The <font color='#f39c12'><b>Metadata Overhead</b></font> allows the AI to remember the 'flavor' of recycled turns. "
                + "The <font color='#95a5a6'><b>Pruned Content</b></font> is weightless history stored on disk.</html>");
        infoPanel.add(info, BorderLayout.NORTH);
        add(infoPanel, BorderLayout.SOUTH);
    }

    public void reload() {
        this.agi = agiPanel.getAgi();
        refresh();
    }

    public void refresh() {
        ContextManager cm = agi.getContextManager();
        int recycled = cm.getAllEffectivelyPrunedPartsTokenCount();
        int metadata = cm.getAllEffectivelyPrunedMetadataTokenCount();
        int total = agi.getLastTotalTokenCount();
        int active = total - metadata;
        int threshold = agi.getConfig().getTokenThreshold();

        activeTokensLabel.setText(NUMBER_FORMAT.format(active) + " tokens");
        prunedMetadataTokensLabel.setText(NUMBER_FORMAT.format(metadata) + " tokens");
        prunedTokensLabel.setText(NUMBER_FORMAT.format(recycled) + " tokens");
        totalContextTokensLabel.setText(NUMBER_FORMAT.format(total) + " / " + NUMBER_FORMAT.format(threshold));
        
        donutChart.update(active, metadata, recycled, threshold);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    /**
     * A high-fidelity donut chart visualizing token distribution.
     */
    private static class MetabolicDonutChart extends JComponent {
        private double activeAng = 0;
        private double metadataAng = 0;
        private double recycledAng = 0;
        private double totalUsagePct = 0;

        public void update(int active, int metadata, int recycled, int threshold) {
            double total = active + metadata + recycled;
            if (total == 0) total = 1;

            this.activeAng = (active / total) * 360.0;
            this.metadataAng = (metadata / total) * 360.0;
            this.recycledAng = (recycled / total) * 360.0;
            
            this.totalUsagePct = Math.min(1.0, (double)(active + metadata) / threshold);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 40;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            int stroke = 40;

            // Background Ring
            g2d.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(236, 240, 241));
            g2d.drawOval(x + stroke/2, y + stroke/2, size - stroke, size - stroke);

            double currentAngle = 90;

            // 1. Active Content (Green)
            if (activeAng > 0) {
                g2d.setColor(new Color(46, 204, 113));
                g2d.draw(new Arc2D.Double(x + stroke/2, y + stroke/2, size - stroke, size - stroke, currentAngle, -activeAng, Arc2D.OPEN));
                currentAngle -= activeAng;
            }

            // 2. Metadata Overhead (Orange)
            if (metadataAng > 0) {
                g2d.setColor(new Color(243, 156, 18));
                g2d.draw(new Arc2D.Double(x + stroke/2, y + stroke/2, size - stroke, size - stroke, currentAngle, -metadataAng, Arc2D.OPEN));
                currentAngle -= metadataAng;
            }

            // 3. Pruned Content (Grey - Inner faint ring)
            if (recycledAng > 0) {
                g2d.setStroke(new BasicStroke(15, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g2d.setColor(new Color(189, 195, 199));
                g2d.draw(new Arc2D.Double(x + stroke + 10, y + stroke + 10, size - (stroke*2) - 20, size - (stroke*2) - 20, 90, 360, Arc2D.OPEN));
            }

            // Center Text: Threshold Usage
            g2d.setFont(getFont().deriveFont(Font.BOLD, 24f));
            String usageText = (int)(totalUsagePct * 100) + "%";
            int tw = g2d.getFontMetrics().stringWidth(usageText);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawString(usageText, getWidth()/2 - tw/2, getHeight()/2 + 8);
            
            g2d.setFont(getFont().deriveFont(12f));
            g2d.setColor(Color.GRAY);
            String subText = "PROMPT LOAD";
            int stw = g2d.getFontMetrics().stringWidth(subText);
            g2d.drawString(subText, getWidth()/2 - stw/2, getHeight()/2 + 25);

            g2d.dispose();
        }
    }
}
