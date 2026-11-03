/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.spi;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uno.anahata.asi.persistence.kryo.KryoUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests shared diagnostic accumulation without a session, provider or tool.
 */
class AbstractToolResponseTest {

    @Test
    void appendedErrorsRetainOrderNotifyAndSurvivePersistence() {
        TestResponse response = new TestResponse();
        List<Object> notifications = new ArrayList<>();
        response.getPropertyChangeSupport().addPropertyChangeListener("errors",
                event -> notifications.add(event.getNewValue()));
        response.addError("Initial cause");
        response.addError("Follow-up guidance");
        response.addError("Final detail");
        String expected = "Initial cause\nFollow-up guidance\nFinal detail";
        assertEquals(expected, response.getErrors());
        assertEquals(List.of("Initial cause", "Initial cause\nFollow-up guidance", expected), notifications);
        assertEquals(expected, KryoUtils.clone(response).getErrors());
        assertNull(response.getExecutionBlockReason());
    }

    /**
     * Minimal response with no session ownership or executable handler.
     */
    public static class TestResponse extends AbstractToolResponse<AbstractToolCall<?, ?>> {

        public TestResponse() {
            super(null);
        }

        @Override
        public void execute() {
        }

        @Override
        public void stop() {
        }
    }
}
