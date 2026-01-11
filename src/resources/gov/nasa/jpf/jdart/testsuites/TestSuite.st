package <packageName>;

<if(!isStaticMethod)>import org.junit.jupiter.api.BeforeEach;<endif>
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <methodProps.className> {

<if(!methodProps.isStaticMethod)>
    private <methodProps.methodClassName> obj;

    @BeforeEach
    void setUp() {
        // Complete the initialization of obj as needed
        obj = new <methodProps.methodClassName>();
    }
<endif>

<tests:{t |
    @Test
    public void test<i0>() {
<if(!t.executionPath.throwsExceptionTest)>
    <if(methodProps.staticMethod)>
        <methodProps.returnType> result = <t.executionPath.call>;
    <else>
        <methodProps.returnType> result = obj.<t.executionPath.call>;
    <endif>

        // perform assertions
<else>
        assertThrows(<t.executionPath.exceptionClass>.class, () -> {
            <if(methodProps.staticMethod)>
            <t.executionPath.call>;
            <else>
            obj.<t.executionPath.call>;
            <endif>
        \});
<endif>
<t.executionPath.assertions:{a |
        <a>;
}>
    \}

}>\}

