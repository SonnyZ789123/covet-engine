package <packageName>;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <className> {

<if(!methodProps.isStaticMethod)>
    private <methodProps.methodClassName> obj;
<endif>

    @BeforeEach
    void setUp() {
        // Initialize tests

        <if(!methodProps.isStaticMethod)>
        // Complete the initialization of obj as needed
        obj = new <methodProps.methodClassName>();
        <endif>
    }

<tests:{t |
    @Test
    <if(t.executionPath.coveredBlocksAnnotations)><t.executionPath.coveredBlocksAnnotations><endif>
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

