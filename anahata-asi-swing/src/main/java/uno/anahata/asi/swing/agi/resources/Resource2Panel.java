/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi.resources;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uno.anahata.asi.context.ContextPosition;
import uno.anahata.asi.model.core.RagMessage;
import uno.anahata.asi.model.resource.RefreshPolicy;
import uno.anahata.asi.resource.v2.Resource;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.message.RagMessageViewer;
import uno.anahata.asi.swing.icons.RestartIcon;
import uno.anahata.asi.swing.internal.EdtPropertyChangeListener;
import uno.anahata.asi.swing.internal.SwingTask;

/**
 * A specialized panel for managing and viewing a V2 {@link Resource}.
 * <p>
 * This panel acts as a high-level orchestrator. It displays resource metadata
 * and context strategy controls, while delegating content rendering and 
 * host-specific actions to the active {@link ResourceUI} strategy.
 * </p>
 * <p>
 * <b>Edit/Save Nexus:</b> Includes a state-aware toggle to transition from 
 * a viewport preview to a full-file high-fidelity editor.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
public class Resource2Panel extends JPanel {

    /** The parent agi panel. */
    private final AgiPanel agiPanel;
    /** The resource currently being managed. */
    private Resource currentResource;

    private final JLabel nameLabel;
    private final JTextField uriField;
    private final JCheckBox providingBox;
    private final JComboBox<ContextPosition> positionCombo;
    private final JComboBox<RefreshPolicy> policyCombo;

    private final JTabbedPane mainTabs;
    
    /** Container for the content component provided by the strategy. */
    private final JPanel viewerContainer;
    /** Container for the host-specific action buttons. */
    private final JPanel actionPanel;
    
    /** The primary content component returned by the strategy. */
    private JComponent activeViewer;
    /** The active strategy being used for the current resource. */
    private ResourceUI activeStrategy;

    /** Button to toggle between View and Edit modes. */
    private final JButton editBtn;
    /** Tracks the current editing state. */
    private boolean editing = false;

    /** Reactive listener for resource state changes. */
    private EdtPropertyChangeListener resourceListener;

    /**
     * Constructs a new Resource2Panel.
     * @param agiPanel The parent AgiPanel.
     */
    public Resource2Panel(AgiPanel agiPanel) {
        this.agiPanel = agiPanel;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // 1. Header: Properties and Metadata
        JPanel headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBorder(BorderFactory.createTitledBorder("Universal Resource Properties"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 5, 2, 5);

        nameLabel = new JLabel("Resource");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);
        
        editBtn = new JButton("📝 EDIT");
        editBtn.addActionListener(e -> toggleEditMode());
        actionPanel.add(editBtn);
        
        headerPanel.add(actionPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        headerPanel.add(new JLabel("URI:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        uriField = new JTextField();
        uriField.setEditable(false);
        uriField.setBorder(null);
        uriField.setOpaque(false);
        headerPanel.add(uriField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.NONE;
        providingBox = new JCheckBox("Effectively Providing Context");
        providingBox.addActionListener(e -> {
            if (currentResource != null) {
                currentResource.setProviding(providingBox.isSelected());
            }
        });
        headerPanel.add(providingBox, gbc);

        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        comboPanel.setOpaque(false);
        
        comboPanel.add(new JLabel("Position:"));
        positionCombo = new JComboBox<>(ContextPosition.values());
        positionCombo.addActionListener(e -> {
            if (currentResource != null) {
                currentResource.setContextPosition((ContextPosition) positionCombo.getSelectedItem());
            }
        });
        comboPanel.add(positionCombo);
        
        comboPanel.add(new JLabel("Refresh:"));
        policyCombo = new JComboBox<>(RefreshPolicy.values());
        policyCombo.addActionListener(e -> {
            if (currentResource != null) {
                currentResource.setRefreshPolicy((RefreshPolicy) policyCombo.getSelectedItem());
            }
        });
        comboPanel.add(policyCombo);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerPanel.add(comboPanel, gbc);

        add(headerPanel, BorderLayout.NORTH);

        // 2. Main Viewport Tabs
        mainTabs = new JTabbedPane();
        viewerContainer = new JPanel(new BorderLayout());
        
        mainTabs.addTab("Capability View", viewerContainer);
        
        add(mainTabs, BorderLayout.CENTER);
    }

    /**
     * Toggles between read-only viewport view and high-fidelity editor.
     */
    private void toggleEditMode() {
        if (currentResource == null || activeStrategy == null || activeViewer == null) {
            return;
        }

        if (editing) {
            // PERFORM SAVE
            String newContent = activeStrategy.getEditorContent(activeViewer);
            if (newContent != null) {
                saveContent(newContent);
            } else {
                setEditing(false);
            }
        } else {
            // ENTER EDIT MODE
            setEditing(true);
        }
    }

    private void setEditing(boolean editing) {
        this.editing = editing;
        activeStrategy.setEditing(activeViewer, editing);
        
        if (editing) {
            editBtn.setText("💾 SAVE");
            editBtn.setIcon(new RestartIcon(16)); // Representing 'Persist/Commit'
        } else {
            editBtn.setText("📝 EDIT");
            editBtn.setIcon(null);
        }
    }

    /**
     * Atomically saves the content back to the resource via the agnostic write API.
     */
    private void saveContent(String content) {
        new SwingTask<>(this, "Saving Resource", () -> {
            // THE TECHNICAL PURE PATH: Write straight to the resource orchestrator.
            // This bypasses the toolkit middle-man and redundant DTO assembly.
            currentResource.write(content);
            return null;
        }, done -> {
            setEditing(false);
            syncUiWithResource();
        }).execute();
    }

    /**
     * Sets the resource to manage and initializes the dynamic viewer.
     * @param res The V2 resource instance.
     */
    public void setResource(Resource res) {
        if (resourceListener != null) {
            resourceListener.unbind();
        }
        
        this.currentResource = res;
        this.editing = false;
        editBtn.setText("📝 EDIT");
        editBtn.setIcon(null);
        
        this.resourceListener = new EdtPropertyChangeListener(this, res, null, evt -> syncUiWithResource());
        
        // 1. Resolve strategy and populate containers
        viewerContainer.removeAll();
        actionPanel.removeAll();
        actionPanel.add(editBtn);
        
        this.activeStrategy = ResourceUiRegistry.getInstance().getResourceUI();
        if (res != null && activeStrategy != null) {
            this.activeViewer = activeStrategy.createContent(res, agiPanel);
            viewerContainer.add(activeViewer, BorderLayout.CENTER);
            activeStrategy.populateActions(actionPanel, res, agiPanel);
            
            editBtn.setVisible(activeStrategy.canEdit(res) && res.isWritable());
        } else if (res != null) {
            viewerContainer.add(new JLabel("No ResourceUI strategy registered for this host environment."), BorderLayout.CENTER);
            editBtn.setVisible(false);
        }
        
        syncUiWithResource();
    }

    /**
     * Synchronizes the common metadata controls with the resource state.
     */
    private void syncUiWithResource() {
        if (currentResource == null) {
            return;
        }
        
        nameLabel.setText(currentResource.getName());
        uriField.setText(currentResource.getHandle().getUri().toString());
        providingBox.setSelected(currentResource.isProviding());
        positionCombo.setSelectedItem(currentResource.getContextPosition());
        policyCombo.setSelectedItem(currentResource.getRefreshPolicy());

        updateModelPerspectiveTab();
        
        revalidate();
        repaint();
    }

    /**
     * Generates and updates the "Model Perspective" tab, showing the definitive
     * RAG part that will be injected into the prompt.
     */
    private void updateModelPerspectiveTab() {
        // Find existing tab or create new
        int idx = -1;
        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            if (mainTabs.getTitleAt(i).equals("Model Perspective (RAG)")) {
                idx = i;
                break;
            }
        }

        RagMessage rawMsg = new RagMessage(agiPanel.getAgi());
        try {
            // Mirror the RAG assembly logic: Header + Content
            rawMsg.addTextPart(currentResource.getHeader());
            if (currentResource.getContextPosition() == ContextPosition.SYSTEM_INSTRUCTIONS) {
                currentResource.getSystemInstructions().forEach(rawMsg::addTextPart);
            } else {
                currentResource.populateMessage(rawMsg);
            }
        } catch (Exception e) {
            rawMsg.addTextPart("**Error generating perspective:**\n" + ExceptionUtils.getStackTrace(e));
        }

        RagMessageViewer viewer = new RagMessageViewer(agiPanel, rawMsg, false, false);
        viewer.render();

        if (idx == -1) {
            mainTabs.addTab("Model Perspective (RAG)", viewer);
        } else {
            mainTabs.setComponentAt(idx, viewer);
        }
    }
}
