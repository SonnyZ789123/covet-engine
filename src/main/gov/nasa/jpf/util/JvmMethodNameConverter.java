package gov.nasa.jpf.util;

public final class JvmMethodNameConverter {

    private JvmMethodNameConverter() {
    }

    /**
     * Converts:
     * com/finscore/core/engine/RuleEngine.overrides(...)
     * ->
     * com.finscore.core.engine.RuleEngine.overrides(...)
     */
    public static String toDottedClassName(String jvmName) {
        int methodSeparator = jvmName.indexOf('.');
        if (methodSeparator < 0) {
            throw new IllegalArgumentException("Invalid JVM method name: " + jvmName);
        }

        String classPart = jvmName.substring(0, methodSeparator);
        String rest = jvmName.substring(methodSeparator);

        return classPart.replace('/', '.') + rest;
    }

    /**
     * Converts:
     * com.finscore.core.engine.RuleEngine.overrides(...)
     * ->
     * com/finscore/core/engine/RuleEngine.overrides(...)
     */
    public static String toInternalClassName(String dottedName) {
        int methodSeparator = dottedName.indexOf('.');
        if (methodSeparator < 0) {
            throw new IllegalArgumentException("Invalid dotted method name: " + dottedName);
        }

        String classPart = dottedName.substring(0, methodSeparator);
        String rest = dottedName.substring(methodSeparator);

        return classPart.replace('.', '/') + rest;
    }
}
