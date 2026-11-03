/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.agi.message.part.tool;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import uno.anahata.asi.agi.tool.ToolExecutionStatus;
import uno.anahata.asi.agi.tool.ToolPermission;
import uno.anahata.asi.agi.tool.spi.AbstractTool;
import uno.anahata.asi.agi.tool.spi.AbstractToolCall;
import uno.anahata.asi.agi.tool.spi.AbstractToolResponse;
import uno.anahata.asi.swing.agi.SwingAgiConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises existing action controls on the EDT without a session or IDE
 * window.
 */
class ToolCallPanelTest {

    @Test
    void blockedResponseHasNoRetryResetOrStatusOverrideAndOrdinaryRetryReturns() throws Exception {
        ToolCallPanel panel = mock(ToolCallPanel.class);
        AbstractToolCall<?, ?> call = mock(AbstractToolCall.class);
        AbstractTool<?, ?> tool = mock(AbstractTool.class);
        AbstractToolResponse<?> response = mock(AbstractToolResponse.class);
        doReturn(tool).when(call).getTool();
        when(tool.getPermission()).thenReturn(ToolPermission.PROMPT);
        when(response.getStatus()).thenReturn(ToolExecutionStatus.FAILED);
        when(response.getExecutionBlockReason()).thenReturn("Create a new corrected call.");
        JButton run = new JButton();
        JButton clear = new JButton();
        JComboBox<ToolExecutionStatus> status = new JComboBox<>(ToolExecutionStatus.values());
        set(panel, "agiConfig", mock(SwingAgiConfig.class));
        set(panel, "permissionCombo", new JComboBox<>(ToolPermission.values()));
        set(panel, "statusCombo", status);
        set(panel, "feedbackField", new JTextField());
        set(panel, "runButton", run);
        set(panel, "revertButton", clear);
        set(panel, "declineButton", new JButton());
        set(panel, "toolProgressBar", new JProgressBar());
        Method update = ToolCallPanel.class.getDeclaredMethod("updateControls", AbstractToolCall.class, AbstractToolResponse.class);
        update.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                update.invoke(panel, call, response);
                assertEquals("New call required", run.getText());
                assertFalse(run.isEnabled());
                assertEquals(0, run.getActionListeners().length);
                assertFalse(clear.isVisible());
                assertFalse(clear.isEnabled());
                assertFalse(status.isEnabled());
                assertEquals("Create a new corrected call.", run.getToolTipText());
                when(response.getExecutionBlockReason()).thenReturn(null);
                update.invoke(panel, call, response);
                assertEquals("Retry", run.getText());
                assertTrue(run.isEnabled());
                assertEquals(1, run.getActionListeners().length);
                assertTrue(clear.isEnabled());
                assertTrue(clear.isVisible());
                assertTrue(status.isEnabled());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    private static void set(ToolCallPanel panel, String name, Object value) throws Exception {
        for (Class<?> type = ToolCallPanel.class; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(panel, value);
                return;
            } catch (NoSuchFieldException absent) {
                // Continue to the inherited panel configuration.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
