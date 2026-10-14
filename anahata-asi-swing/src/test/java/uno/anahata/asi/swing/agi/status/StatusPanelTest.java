/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. ForÃƒÂ§a BarÃƒÂ§a! */
package uno.anahata.asi.swing.agi.status;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.ResponseUsageMetadata;
import uno.anahata.asi.agi.provider.Response;
import uno.anahata.asi.agi.status.AgiStatus;
import uno.anahata.asi.agi.status.RawApiJson;
import uno.anahata.asi.agi.status.StatusManager;
import uno.anahata.asi.agi.tool.ToolManager;
import uno.anahata.asi.swing.agi.SwingAgiConfig;
import uno.anahata.asi.swing.components.CodeHyperlink;

/** Exercises status refresh on the EDT without constructing a session or window. */
class StatusPanelTest {
    @Test
    void streamingDiagnosticsRefreshBeforeResponseAndKeepFinalUsage() throws Exception {
        Agi agi = mock(Agi.class);
        StatusManager status = mock(StatusManager.class);
        ToolManager tools = mock(ToolManager.class);
        SwingAgiConfig config = mock(SwingAgiConfig.class);
        when(agi.getStatusManager()).thenReturn(status);
        when(agi.getToolManager()).thenReturn(tools);
        when(status.getCurrentStatus()).thenReturn(AgiStatus.API_CALL_IN_PROGRESS);
        when(status.getApiErrors()).thenReturn(List.of());
        when(tools.getExecutingCalls()).thenReturn(List.of());
        when(agi.getLastResponse()).thenReturn(Optional.empty());
        when(agi.getLastRawApiJson()).thenReturn(Optional.of(new RawApiJson("{}", "[]", "")));

        // Only refresh is real: session/window/audio constructors and paint callbacks are excluded.
        StatusPanel panel = mock(StatusPanel.class);
        doCallRealMethod().when(panel).refresh();
        set(panel, "agi", agi);
        set(panel, "agiConfig", config);
        set(panel, "statusIndicator", mock(field("statusIndicator").getType()));
        set(panel, "statusLabel", new JLabel());
        set(panel, "executingToolsLabel", new JLabel());
        set(panel, "contextUsageBar", mock(ContextUsageBar.class));
        set(panel, "apiErrorsPanel", new JPanel());
        set(panel, "apiErrorsScrollPane", new JScrollPane());
        JLabel tokens = new JLabel();
        JLabel blocked = new JLabel();
        set(panel, "tokenDetailsLabel", tokens);
        set(panel, "blockReasonLabel", blocked);
        CodeHyperlink request = link();
        CodeHyperlink history = link();
        CodeHyperlink response = link();
        set(panel, "rawJsonRequestConfigLink", request);
        set(panel, "historyJsonLink", history);
        set(panel, "rawJsonResponseLink", response);

        SwingUtilities.invokeAndWait(panel::refresh);
        assertTrue(request.isVisible());
        assertTrue(history.isVisible());
        assertFalse(response.isVisible());
        assertFalse(tokens.isVisible());
        assertFalse(blocked.isVisible());

        when(agi.getLastRawApiJson()).thenReturn(Optional.of(new RawApiJson("{}", "[]", "[{}]")));
        SwingUtilities.invokeAndWait(panel::refresh);
        assertTrue(response.isVisible());
        assertFalse(tokens.isVisible());

        Response<?> completed = mock(Response.class);
        when(completed.getPromptFeedback()).thenReturn(Optional.of("test policy"));
        when(completed.getUsageMetadata()).thenReturn(ResponseUsageMetadata.builder()
                .promptTokenCount(11).candidatesTokenCount(7).totalTokenCount(18).build());
        doReturn(Optional.of(completed)).when(agi).getLastResponse();
        SwingUtilities.invokeAndWait(panel::refresh);
        assertTrue(tokens.isVisible());
        assertTrue(tokens.getText().contains("Prompt: 11"));
        assertTrue(tokens.getText().contains("Candidates: 7"));
        assertTrue(blocked.isVisible());
        assertEquals("Prompt Blocked: test policy", blocked.getText());
    }
    private static CodeHyperlink link() {
        return new CodeHyperlink("test", () -> "test", () -> "{}", "json");
    }
    private static Field field(String name) throws Exception {
        Field field = StatusPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void set(StatusPanel panel, String name, Object value) throws Exception {
        field(name).set(panel, value);
    }
}
