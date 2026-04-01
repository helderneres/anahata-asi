/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.AbstractTreeTableModel;
import uno.anahata.asi.AsiContainerPreferences;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.agi.provider.AbstractAgiProvider;
import uno.anahata.asi.agi.provider.AbstractModel;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.ToolPermission;
import uno.anahata.asi.swing.agi.SwingAgiConfig;
import uno.anahata.asi.swing.agi.config.SessionConfigPanel;
import uno.anahata.asi.swing.agi.message.part.tool.ToolPermissionRenderer;
import uno.anahata.asi.swing.icons.DoubleToolIconRefined;
import uno.anahata.asi.swing.icons.IconUtils;
import uno.anahata.asi.swing.icons.OkIcon;
import uno.anahata.asi.swing.icons.ToolIcon;

/**
 * A centralized, multi-tabbed Command Center for managing the ASI container.
 * It governs global defaults, DNA templates, and per-provider API key pools.
 * 
 * @author anahata
 */
@Slf4j
public class AsiContainerPreferencesPanel extends JPanel {

    private final AbstractSwingAsiContainer container;
    private final AsiContainerPreferences prefs;
    
    private JComboBox<Class<? extends AbstractAgiProvider>> providerDropdown;
    private JComboBox<String> modelDropdown;

    /**
     * Constructs a new preferences Command Center.
     * 
     * @param container The ASI container instance.
     */
    public AsiContainerPreferencesPanel(AbstractSwingAsiContainer container) {
        this.container = container;
        this.prefs = container.getPreferences();
        
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(950, 750));

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("General Defaults", createGeneralTab());
        mainTabs.addTab("DNA Templates", createTemplatesTab());
        mainTabs.addTab("Tool Permissions", createPermissionsTab());
        mainTabs.addTab("API Key Pools", createApiKeysTab());

        add(mainTabs, BorderLayout.CENTER);
        
        // Footer: Save Button
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save & Apply Global Config", new OkIcon(16));
        saveBtn.addActionListener(e -> {
            container.savePreferences();
            log.info("Global preferences persisted to disk.");
        });
        footer.add(saveBtn);
        add(footer, BorderLayout.SOUTH);
        
        // Initialize model discovery
        refreshModelDropdown();
    }

    private JPanel createGeneralTab() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 20", "[right]15[grow,fill]"));
        
        JLabel title = new JLabel("Global 'Starting XI' Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, "span, wrap, gapbottom 20");

        // 1. Default Provider
        panel.add(new JLabel("Default AI Provider:"));
        providerDropdown = new JComboBox<>();
        AgiConfig template = prefs.getAgiTemplate();
        DefaultComboBoxModel<Class<? extends AbstractAgiProvider>> providerModel = new DefaultComboBoxModel<>();
        template.getProviderClasses().forEach(providerModel::addElement);
        providerDropdown.setModel(providerModel);
        
        providerDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Class<?> c) {
                    AbstractAgiProvider p = container.getProvider((Class<? extends AbstractAgiProvider>) c);
                    if (p != null) {
                        setText(p.getProviderId());
                    } else {
                        setText(c.getSimpleName());
                    }
                }
                return this;
            }
        });
        
        providerDropdown.setSelectedItem(template.getSelectedProviderClass());
        providerDropdown.addActionListener(e -> {
            Class<? extends AbstractAgiProvider> selected = (Class<? extends AbstractAgiProvider>) providerDropdown.getSelectedItem();
            template.setSelectedProviderClass(selected);
            refreshModelDropdown();
        });
        panel.add(providerDropdown, "wrap");

        // 2. Default Model
        panel.add(new JLabel("Default AI Model:"));
        modelDropdown = new JComboBox<>();
        modelDropdown.addActionListener(e -> {
            String selected = (String) modelDropdown.getSelectedItem();
            if (selected != null) {
                template.setSelectedModelId(selected);
            }
        });
        panel.add(modelDropdown, "wrap");
        
        panel.add(new JLabel("<html><font color='#707070'><i>These settings define which model is selected by default when you create a brand-new session.</i></font></html>"), "gapleft 20, span, wrap");

        return panel;
    }

    private void refreshModelDropdown() {
        AgiConfig template = prefs.getAgiTemplate();
        Class<? extends AbstractAgiProvider> providerClass = template.getSelectedProviderClass();
        if (providerClass == null) return;

        modelDropdown.setEnabled(false);
        modelDropdown.setToolTipText("Discovering models...");

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                AbstractAgiProvider provider = container.getProvider(providerClass);
                if (provider == null) return new ArrayList<>();
                return provider.getModels().stream()
                        .map(AbstractModel::getModelId)
                        .toList();
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    models.forEach(model::addElement);
                    modelDropdown.setModel(model);
                    
                    if (template.getSelectedModelId() != null) {
                        modelDropdown.setSelectedItem(template.getSelectedModelId());
                    }
                    
                    // Fallback: If previous selection is invalid for the new provider, pick the first one.
                    if (modelDropdown.getSelectedIndex() == -1 && model.getSize() > 0) {
                        modelDropdown.setSelectedIndex(0);
                    }
                    
                    modelDropdown.setEnabled(true);
                    modelDropdown.setToolTipText(null);
                } catch (Exception e) {
                    log.error("Failed to discover models for preferences", e);
                    modelDropdown.setModel(new DefaultComboBoxModel<>(new String[]{"Discovery Failed"}));
                }
            }
        }.execute();
    }

    private JPanel createTemplatesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // DNA Templates use the SessionConfigPanel aggregator bound to the global templates
        SessionConfigPanel templatesPanel = new SessionConfigPanel(
                prefs.getAgiTemplate(), 
                prefs.getRequestTemplate(), 
                null // No live Agi session
        );
        
        panel.add(templatesPanel, BorderLayout.CENTER);
        
        JLabel header = new JLabel("<html><div style='padding: 10px;'><b>Session DNA Templates:</b> These settings are inherited by every new session born in this container. Changes made here do not affect existing sessions.</div></html>");
        panel.add(header, BorderLayout.NORTH);
        
        return panel;
    }

    private JPanel createPermissionsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        
        PermissionsTreeTableModel model = new PermissionsTreeTableModel(prefs);
        JXTreeTable treeTable = new JXTreeTable(model);
        treeTable.setRowHeight(25);
        treeTable.setRootVisible(false);
        treeTable.setShowsRootHandles(true);
        
        // 1. Tool / Toolkit Icon Renderer
        treeTable.setTreeCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof PermissionsTreeTableModel.ToolkitNode tk) {
                    setIcon(IconUtils.getIcon("java.png", 16));
                    setText(tk.name);
                } else if (value instanceof PermissionsTreeTableModel.ToolNode tool) {
                    setIcon(new ToolIcon(16));
                    setText(tool.name);
                }
                return this;
            }
        });

        // 2. Permission Column Renderer (Consistent Colors)
        treeTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof ToolPermission tp) {
                    setText(tp.getDisplayValue());
                    setForeground(SwingAgiConfig.getColor(tp));
                }
                return c;
            }
        });

        // 3. Permission Column Editor
        JComboBox<ToolPermission> permissionCombo = new JComboBox<>(ToolPermission.values());
        permissionCombo.setRenderer(new ToolPermissionRenderer());
        permissionCombo.addActionListener(e -> {
            ToolPermission tp = (ToolPermission) permissionCombo.getSelectedItem();
            if (tp != null) {
                permissionCombo.setForeground(SwingAgiConfig.getColor(tp));
            }
        });
        
        treeTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(permissionCombo) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (value instanceof ToolPermission tp) {
                    c.setForeground(SwingAgiConfig.getColor(tp));
                }
                return c;
            }
        });

        // Expand all toolkits by default
        for (int i = 0; i < treeTable.getRowCount(); i++) {
            treeTable.expandRow(i);
        }

        panel.add(new JScrollPane(treeTable), BorderLayout.CENTER);
        panel.add(new JLabel("<html><div style='padding: 10px;'><b>Global Tool Rules of Engagement:</b> Set the default safety level for every tool grouped by toolkit. Changes apply to all sessions.</div></html>"), BorderLayout.NORTH);

        return panel;
    }

    private JPanel createApiKeysTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane providerTabs = new JTabbedPane(JTabbedPane.LEFT);

        AgiConfig template = prefs.getAgiTemplate();
        List<Class<? extends AbstractAgiProvider>> providerClasses = template.getProviderClasses();

        for (Class<? extends AbstractAgiProvider> providerClass : providerClasses) {
            AbstractAgiProvider provider = container.getProvider(providerClass);
            if (provider != null) {
                ProviderKeysPanel keysPanel = new ProviderKeysPanel(provider);
                providerTabs.addTab(provider.getProviderId(), keysPanel);
            }
        }

        panel.add(providerTabs, BorderLayout.CENTER);
        return panel;
    }

    /**
     * TreeTableModel implementation for the hierarchical tool permissions view.
     */
    private static class PermissionsTreeTableModel extends AbstractTreeTableModel {
        private final List<ToolkitNode> toolkitNodes = new ArrayList<>();
        private final AsiContainerPreferences preferences;

        public PermissionsTreeTableModel(AsiContainerPreferences prefs) {
            super(new Object()); // dummy root
            this.preferences = prefs;
            AgiConfig template = prefs.getAgiTemplate();
            Map<String, ToolPermission> currentPermissions = prefs.getToolPermissions();

            for (Class<?> toolkitClass : template.getToolClasses()) {
                ToolkitNode tkNode = new ToolkitNode(toolkitClass.getSimpleName());
                toolkitNodes.add(tkNode);
                for (Method m : toolkitClass.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(AgiTool.class)) {
                        String toolName = tkNode.name + "." + m.getName();
                        ToolPermission p = currentPermissions.getOrDefault(toolName, m.getAnnotation(AgiTool.class).permission());
                        tkNode.tools.add(new ToolNode(m.getName(), p, tkNode));
                    }
                }
            }
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Toolkit / Tool" : "Global Permission";
        }

        @Override
        public Object getValueAt(Object node, int column) {
            if (node instanceof ToolkitNode tk) {
                return column == 0 ? tk.name : null;
            } else if (node instanceof ToolNode tool) {
                return column == 0 ? tool.name : tool.permission;
            }
            return null;
        }

        @Override
        public boolean isCellEditable(Object node, int column) {
            return node instanceof ToolNode && column == 1;
        }

        @Override
        public void setValueAt(Object value, Object node, int column) {
            if (node instanceof ToolNode tool && column == 1 && value instanceof ToolPermission p) {
                tool.permission = p;
                preferences.getToolPermissions().put(tool.parent.name + "." + tool.name, p);
            }
        }

        @Override
        public Object getChild(Object parent, int index) {
            if (parent == getRoot()) {
                return toolkitNodes.get(index);
            }
            if (parent instanceof ToolkitNode tk) {
                return tk.tools.get(index);
            }
            return null;
        }

        @Override
        public int getChildCount(Object parent) {
            if (parent == getRoot()) {
                return toolkitNodes.size();
            }
            if (parent instanceof ToolkitNode tk) {
                return tk.tools.size();
            }
            return 0;
        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            if (parent == getRoot()) {
                return toolkitNodes.indexOf(child);
            }
            if (parent instanceof ToolkitNode tk) {
                return tk.tools.indexOf(child);
            }
            return -1;
        }

        private static class ToolkitNode {
            String name;
            List<ToolNode> tools = new ArrayList<>();
            ToolkitNode(String name) { this.name = name; }
        }

        private static class ToolNode {
            String name;
            ToolPermission permission;
            ToolkitNode parent;
            ToolNode(String name, ToolPermission p, ToolkitNode parent) {
                this.name = name; this.permission = p; this.parent = parent;
            }
        }
    }
}
