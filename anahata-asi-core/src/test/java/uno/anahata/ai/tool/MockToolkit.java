/* Licensed under the Anahata Software License, Version 108 - https://github.com/anahata-os/anahata-ai/blob/main/LICENSE */
package uno.anahata.ai.tool;

import uno.anahata.asi.agi.tool.AnahataToolkit;
import java.util.List;
import uno.anahata.ai.model.tool.MockComplexObject;
import uno.anahata.ai.model.tool.Tree;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolResponse;
import uno.anahata.asi.agi.tool.AgiToolkit;
import uno.anahata.asi.agi.tool.AgiToolParam;
import uno.anahata.asi.agi.tool.AgiTool;

@AgiToolkit(value = "A mock toolkit for testing schema generation and context injection.", maxDepth = 10)
public class MockToolkit extends AnahataToolkit {

    @AgiTool("Returns a greeting for the given name.")
    public String sayHello(@AgiToolParam("The name to greet.") String name) {
        return "Hello, " + name;
    }   

    @AgiTool("A method that returns a recursive Tree object.")
    public Tree getTree() {
        return new Tree();
    }

    @AgiTool("A method with no return value.")
    public void doNothing() {
        // This method does nothing and returns void.
    }
    
    @AgiTool("Returns a List<String>")
    public List<String> getStringList() {
        return null;
        // This method does nothing and returns null.
    }
    
    @AgiTool("A method that returns a complex object with various field types.")
    public MockComplexObject getComplexObject() {
        return new MockComplexObject();
    }
    
    @AgiTool(value = "A tool specifically for testing the JavaTool context.", requiresApproval = false)
    public String testContext(@AgiToolParam("A message to add to the logs.") String logMessage) {
        log("This is a log message from inside the tool: " + logMessage);
        return "Context test completed successfully.";
    }

    @AgiTool(value = "A tool for testing context access.", requiresApproval = false)
    public String testContextAccess() {
        if (JavaMethodToolResponse.getCurrent() != null) {
            return "Success";
        }
        return "Failure";
    }
}
