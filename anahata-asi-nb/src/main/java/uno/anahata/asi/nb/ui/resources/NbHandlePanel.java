/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.resources;

import java.awt.Color;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import lombok.extern.slf4j.Slf4j;
import org.openide.filesystems.FileObject;
import uno.anahata.asi.nb.resources.handle.NbHandle;
import uno.anahata.asi.swing.agi.resources.handle.AbstractHandlePanel;

/**
 * A specialized metadata panel for the NbHandle.
 */
@Slf4j
public class NbHandlePanel extends AbstractHandlePanel<NbHandle> {

    private final JLabel validityLabel = new JLabel();
    private final JCheckBox archiveBox = new JCheckBox("Archive Entry");
    private final JTextField uriField = createReadOnlyField();
    private final JTextField pathField = createReadOnlyField();

    public NbHandlePanel() {
        archiveBox.setEnabled(false);
        archiveBox.setOpaque(false);
        
        addProperty("URI:", uriField);
        addProperty("Path:", pathField);
        addProperty("IDE Validity:", validityLabel);
        addProperty("Storage:", archiveBox);
    }

    @Override
    public void refresh() {
        super.refresh();
        if (handle == null) return;
        
        uriField.setText(handle.getUri().toString());
        pathField.setText(handle.getPath() != null ? handle.getPath() : "N/A");
        
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
            validityLabel.setText("OFFLINE (Unresolved)");
            validityLabel.setForeground(Color.RED);
        }
    }
}