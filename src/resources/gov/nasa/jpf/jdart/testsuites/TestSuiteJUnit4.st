package <packageName>;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class <className> {

<if(!methodProps.isStaticMethod)>
    private <methodProps.methodClassName> obj;
<endif>

    @Before
    public void setUp() {
        // Initialize tests

        <if(!methodProps.isStaticMethod)>
        // Complete the initialization of obj as needed
        obj = new <methodProps.methodClassName>();
        <endif>
    }

<tests:{t |
    @Test
    public void test<i0>() {
<if(!t.executionPath.throwsExceptionTest)>
    <if(methodProps.isStaticMethod)>
        <methodProps.returnType> result = <t.executionPath.call>;

    <else>
        <methodProps.returnType> result = obj.<t.executionPath.call>;
    <endif>

        // perform assertions
<else>
        assertThrows(<t.executionPath.exceptionClass>.class, () -> {
            <if(methodProps.isStaticMethod)>
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

