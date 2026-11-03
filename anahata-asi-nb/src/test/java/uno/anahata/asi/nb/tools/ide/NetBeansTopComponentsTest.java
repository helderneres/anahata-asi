/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.ide;

import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Protects JDBC captions at the existing IDE context boundary, without IDE
 * startup.
 */
class NetBeansTopComponentsTest {

    @Test
    void reportsOmitWholeJdbcCaptionAndPreserveOrdinaryNodes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            WindowManager manager = mock(WindowManager.class);
            TopComponent.Registry registry = mock(TopComponent.Registry.class);
            TopComponent component = mock(TopComponent.class);
            Node connection = mock(Node.class);
            Node project = mock(Node.class);
            String sensitive = "JDBC:fixture://private-host/private-db [private-user]";
            when(manager.getRegistry()).thenReturn(registry);
            when(registry.getOpened()).thenReturn(Set.of(component));
            when(manager.findTopComponentID(component)).thenReturn("services");
            when(component.getName()).thenReturn("Services");
            when(component.getDisplayName()).thenReturn(sensitive);
            when(component.getToolTipText()).thenReturn(sensitive);
            when(component.getLookup()).thenReturn(Lookup.EMPTY);
            when(component.getActivatedNodes()).thenReturn(new Node[]{connection, project});
            when(connection.getDisplayName()).thenReturn(sensitive);
            when(project.getDisplayName()).thenReturn("anahata-project");
            try (MockedStatic<WindowManager> window = mockStatic(WindowManager.class)) {
                window.when(WindowManager::getDefault).thenReturn(manager);
                var info = NetBeansTopComponents.listTopComponents().getFirst();
                String report = NetBeansTopComponents.getMarkdownReport();
                assertTrue(report.contains("anahata-project"));
                assertTrue(report.contains("Database connection details omitted"));
                assertFalse(report.contains("private-host"));
                assertFalse(report.contains("private-user"));
                assertFalse(info.getDisplayName().contains("private-db"));
                assertFalse(info.getTooltip().contains("private-user"));
                assertFalse(info.getActivatedNodes().contains("private-user"));
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }
}
