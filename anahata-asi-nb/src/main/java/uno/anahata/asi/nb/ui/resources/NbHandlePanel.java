/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.resources;

import java.awt.Color;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import lombok.extern.slf4j.Slf4j;
import org.openide.filesystems.FileObject;
import uno.anahata.asi.nb.resources.handle.NbHandle;
import uno.anahata.asi.swing.agi.resources.handle.AbstractHandlePanel;

/**
 * A specialized metadata panel for the {@link NbHandle}.
 * <p>
 * This panel exposes NetBeans VFS attributes, such as FileObject validity 
 * and archive status (e.g., JAR entries).
 * </p>
 * 
 * @author anahata
 */
@Slf4j
public class NbHandlePanel extends AbstractHandlePanel<NbHandle> {

    private final JLabel validityLabel = new JLabel();
    private final JCheckBox archiveBox = new JCheckBox("Archive Entry");

    /**
     * Constructs a new NbHandlePanel.
     */
    public NbHandlePanel() {
        archiveBox.setEnabled(false);
        archiveBox.setOpaque(false);
        
        addProperty("IDE Validity:", validityLabel);
        addProperty("Storage:", archiveBox);
    }

    /** 
     * {@inheritDoc} 
     * <p>Implementation details: Synchronizes labels with the live FileObject state 
     * within the NetBeans IDE.</p>
     */
    @Override
    public void refresh() {
        super.refresh();
        if (handle == null) {
            return;
        }
        
        FileObject fo = handle.getFileObject();
        if (fo != null) {
            validityLabel.setText(fo.isValid() ? "VALID" : "INVALID");
            validityLabel.setForeground(fo.isValid() ? new Color(0, 150, 0) : Color.RED);
            
            try {
                archiveBox.setSelected(fo.getFileSystem().isReadOnly());
            } catch (Exception e) {
                archiveBox.setSelected(false);
            }
        } else {
            validityLabel.setText("UNRESOLVED");
            validityLabel.setForeground(Color.GRAY);
        }
    }
}
