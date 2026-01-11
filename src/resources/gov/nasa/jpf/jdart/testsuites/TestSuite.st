package <packageName>;

<if(!isStaticMethod)>import org.junit.jupiter.api.BeforeEach;<endif>
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <className> {

<if(!isStaticMethod)>
    private <methodClassName> obj;

    @BeforeEach
    void setUp() {
        // Complete the initialization of obj as needed
        obj = new <methodClassName>();
    }
<endif>

<tests:{t |
    @Test
    public void test<i0>() {
<if(!t.executionPath.throwsExceptionTest)>
    <if(t.executionPath.staticMethod)>
        <t.executionPath.returnType> result = <t.executionPath.call>;
    <else>
        <t.executionPath.returnType> result = obj.<t.executionPath.call>;
    <endif>

        // perform assertions
<else>
        assertThrows(<t.executionPath.exceptionClass>.class, () -> {
            <if(t.executionPath.staticMethod)>
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

