/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi.config;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.provider.AbstractModel;
import uno.anahata.asi.agi.provider.RequestConfig;
import uno.anahata.asi.agi.provider.ServerTool;
import uno.anahata.asi.agi.provider.ThinkingLevel;
import uno.anahata.asi.swing.components.ScrollablePanel;
import uno.anahata.asi.swing.components.SliderSpinner;
import uno.anahata.asi.swing.internal.EdtPropertyChangeListener;

/**
 * A panel for editing the model-specific execution configuration (RequestConfig).
 * Manages parameters like temperature, thinking levels, modalities, and server tools.
 * 
 * @author anahata
 */
@Slf4j
public class RequestConfigPanel extends ScrollablePanel implements PropertyChangeListener {

    private final RequestConfig config;
    private final Agi agi;

    //== Parameter Components ==//
    private SliderSpinner temperatureControl;
    private SliderSpinner maxOutputTokensControl;
    private SliderSpinner topKControl;
    private SliderSpinner topPControl;
    private SliderSpinner candidateCountControl;
    private JComboBox<ThinkingLevel> thinkingLevelDropdown;
    private JCheckBox useNativeSchemasCheckbox;

    //== Modalities & Tools ==//
    private JPanel modalitiesPanel;
    private JPanel serverToolsPanel;

    //== Debug/Context Flags ==//
    private JCheckBox includePrunedCheckbox;
    private JCheckBox injectInbandMetadataCheckbox;

    /**
     * Constructs a new panel for the specified RequestConfig.
     * @param config The request configuration to edit.
     * @param agi The associated Agi session (for model discovery).
     */
    public RequestConfigPanel(RequestConfig config, Agi agi) {
        this.config = config;
        this.agi = agi;
        initComponents();
        loadConfig();
        
        if (agi != null) {
            agi.addPropertyChangeListener(this);
            new EdtPropertyChangeListener(this, agi.getConfig(), "hostedToolsEnabled", evt -> loadConfig());
        }
    }

    private void initComponents() {
        setLayout(new MigLayout("fillx, insets 10", "[grow,fill]", "[]10[]10[]"));

        // --- 1. MODEL PARAMETERS ---
        JPanel paramsPanel = createSectionPanel("Model Parameters");
        paramsPanel.setLayout(new MigLayout("fillx, insets 10", "[right]pref[10][grow,fill]"));

        paramsPanel.add(new JLabel("Thinking Level:"));
        thinkingLevelDropdown = new JComboBox<>(ThinkingLevel.values());
        thinkingLevelDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ThinkingLevel tl) setText(tl.getDisplayValue());
                return this;
            }
        });
        paramsPanel.add(thinkingLevelDropdown, "wrap");

        paramsPanel.add(new JLabel("Temperature:"));
        temperatureControl = new SliderSpinner(new SpinnerNumberModel(1.0, 0.0, 2.0, 0.1), 0, 200, 100.0);
        paramsPanel.add(temperatureControl, "wrap");

        paramsPanel.add(new JLabel("Max Output Tokens:"));
        maxOutputTokensControl = new SliderSpinner(new SpinnerNumberModel(2048, 1, 1000000, 1), 1, 1000000, 1.0);
        paramsPanel.add(maxOutputTokensControl, "wrap");

        paramsPanel.add(new JLabel("Top K:"));
        topKControl = new SliderSpinner(new SpinnerNumberModel(40, 1, 100, 1), 1, 100, 1.0);
        paramsPanel.add(topKControl, "wrap");

        paramsPanel.add(new JLabel("Top P:"));
        topPControl = new SliderSpinner(new SpinnerNumberModel(0.95, 0.0, 1.0, 0.05), 0, 100, 100.0);
        paramsPanel.add(topPControl, "wrap");

        paramsPanel.add(new JLabel("Max Candidates:"));
        candidateCountControl = new SliderSpinner(new SpinnerNumberModel(1, 1, 8, 1), 1, 8, 1.0);
        paramsPanel.add(candidateCountControl, "wrap");

        add(paramsPanel, "wrap");

        // --- 2. FEATURES & TOOLS ---
        JPanel featuresPanel = createSectionPanel("Features & Capabilities");
        featuresPanel.setLayout(new MigLayout("fillx, insets 10", "[right]pref[10][grow,fill]"));

        useNativeSchemasCheckbox = new JCheckBox("Use provider-native tool schemas");
        featuresPanel.add(new JLabel("Native Schemas:"));
        featuresPanel.add(useNativeSchemasCheckbox, "wrap");

        modalitiesPanel = new JPanel();
        modalitiesPanel.setLayout(new BoxLayout(modalitiesPanel, BoxLayout.Y_AXIS));
        modalitiesPanel.setOpaque(false);
        featuresPanel.add(new JLabel("Modalities:"), "top");
        featuresPanel.add(modalitiesPanel, "wrap");

        serverToolsPanel = new JPanel();
        serverToolsPanel.setLayout(new BoxLayout(serverToolsPanel, BoxLayout.Y_AXIS));
        serverToolsPanel.setOpaque(false);
        featuresPanel.add(new JLabel("Server Tools:"), "top");
        featuresPanel.add(serverToolsPanel, "wrap");

        add(featuresPanel, "wrap");

        // --- 3. CONTEXT & DEBUG ---
        JPanel debugPanel = createSectionPanel("Context & Debug Flags");
        debugPanel.setLayout(new MigLayout("fillx, insets 10", "[right]pref[10][grow,fill]"));

        includePrunedCheckbox = new JCheckBox("Include soft-pruned items in API payload");
        debugPanel.add(new JLabel("Include Pruned:"));
        debugPanel.add(includePrunedCheckbox, "wrap");

        injectInbandMetadataCheckbox = new JCheckBox("Inject IDs and depths into model prompt");
        debugPanel.add(new JLabel("In-band Metadata:"));
        debugPanel.add(injectInbandMetadataCheckbox, "wrap");

        add(debugPanel, "wrap");

        setupListeners();
    }

    private void setupListeners() {
        thinkingLevelDropdown.addActionListener(e -> config.setThinkingLevel((ThinkingLevel) thinkingLevelDropdown.getSelectedItem()));
        temperatureControl.addChangeListener(e -> config.setTemperature(((Number) temperatureControl.getValue()).floatValue()));
        maxOutputTokensControl.addChangeListener(e -> config.setMaxOutputTokens((Integer) maxOutputTokensControl.getValue()));
        topKControl.addChangeListener(e -> config.setTopK((Integer) topKControl.getValue()));
        topPControl.addChangeListener(e -> config.setTopP(((Number) topPControl.getValue()).floatValue()));
        candidateCountControl.addChangeListener(e -> config.setCandidateCount((Integer) candidateCountControl.getValue()));
        useNativeSchemasCheckbox.addActionListener(e -> config.setUseNativeSchemas(useNativeSchemasCheckbox.isSelected()));
        
        includePrunedCheckbox.addActionListener(e -> config.setIncludePruned(includePrunedCheckbox.isSelected()));
        injectInbandMetadataCheckbox.addActionListener(e -> config.setInjectInbandMetadata(injectInbandMetadataCheckbox.isSelected()));
    }

    private void loadConfig() {
        AbstractModel model = agi != null ? agi.getSelectedModel() : null;

        thinkingLevelDropdown.setSelectedItem(config.getThinkingLevel());
        useNativeSchemasCheckbox.setSelected(config.isUseNativeSchemas());
        includePrunedCheckbox.setSelected(config.isIncludePruned());
        injectInbandMetadataCheckbox.setSelected(config.isInjectInbandMetadata());

        // Dynamic limits and defaults from model
        float temp = config.getTemperature() != null ? config.getTemperature() : (model != null && model.getDefaultTemperature() != null ? model.getDefaultTemperature() : 1.0f);
        temperatureControl.setValue((double) temp);

        int maxTokens = config.getMaxOutputTokens() != null ? config.getMaxOutputTokens() : (model != null ? model.getMaxOutputTokens() : 2048);
        if (model != null) maxOutputTokensControl.getSlider().setMaximum(model.getMaxOutputTokens());
        maxOutputTokensControl.setValue(maxTokens);

        int topK = config.getTopK() != null ? config.getTopK() : (model != null && model.getDefaultTopK() != null ? model.getDefaultTopK() : 40);
        topKControl.setValue(topK);

        float topP = config.getTopP() != null ? config.getTopP() : (model != null && model.getDefaultTopP() != null ? model.getDefaultTopP() : 0.95f);
        topPControl.setValue((double) topP);

        candidateCountControl.setValue(config.getCandidateCount() != null ? config.getCandidateCount() : 1);

        if (model != null) {
            updateModalities(model);
            updateServerTools(model);
        }
    }

    private void updateModalities(AbstractModel model) {
        modalitiesPanel.removeAll();
        for (String modality : model.getSupportedResponseModalities()) {
            JCheckBox cb = new JCheckBox(modality);
            cb.setSelected(config.getResponseModalities().contains(modality));
            cb.setOpaque(false);
            cb.addActionListener(e -> {
                if (cb.isSelected()) config.getResponseModalities().add(modality);
                else config.getResponseModalities().remove(modality);
            });
            modalitiesPanel.add(cb);
        }
    }

    private void updateServerTools(AbstractModel model) {
        serverToolsPanel.removeAll();
        List<Object> enabledIds = config.getEnabledServerTools().stream().map(ServerTool::getId).collect(Collectors.toList());
        for (ServerTool tool : model.getAvailableServerTools()) {
            JCheckBox cb = new JCheckBox(tool.getDisplayName());
            cb.setToolTipText(tool.getDescription());
            cb.setSelected(enabledIds.contains(tool.getId()));
            cb.setOpaque(false);
            cb.addActionListener(e -> {
                if (cb.isSelected()) config.getEnabledServerTools().add(tool);
                else config.getEnabledServerTools().removeIf(st -> st.getId().equals(tool.getId()));
            });
            serverToolsPanel.add(cb);
        }
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 200, 200)),
                title, 0, 0, getFont().deriveFont(java.awt.Font.BOLD, 12f), new java.awt.Color(100, 100, 100)));
        return panel;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("selectedModel".equals(evt.getPropertyName())) loadConfig();
    }
}
