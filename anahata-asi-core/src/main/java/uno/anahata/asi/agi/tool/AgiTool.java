/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an AI-callable tool and provides essential metadata.
 * This is the cornerstone of the V2 tool framework, defining the tool's
 * description, max depth policy, and default execution permission.
 *
 * @author anahata-gemini-pro-2.5
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgiTool {

    /**
     * A detailed description of what the tool does, including its purpose,
     * parameters, and expected output. This is critical for the model's
     * understanding and is used to generate the tool's schema.
     * 
     * @return The tool description.
     */
    String value();

    /**
     * The maximum depth policy for this tool's call/response pair in the
     * conversation history. This serves as a default hint that can be 
     * overridden by the model at runtime.
     * <p>
     * A value of -1 indicates that the value should be inherited from the 
     * {@code @AgiToolkit} annotation or the system default.
     * 
     * @return The maximum depth to keep this tool's result in context.
     */
    int maxDepth() default -1; // Inherit from toolkit

    /**
     * The default execution permission for this tool. 
     * This defines whether the tool requires explicit user confirmation 
     * before execution.
     * 
     * @return The default tool permission.
     */
    ToolPermission permission() default ToolPermission.PROMPT;
}
