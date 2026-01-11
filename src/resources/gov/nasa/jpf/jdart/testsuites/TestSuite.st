package <packageName>;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <className> {

<tests:{t |
    @Test
    public void test<i0>() {
<t.methods:{m |
<if(!m.staticMethod)>
        <m.className> obj = new <m.className>();
<endif>

<if(!m.throwsExceptionTest)>
    <if(m.staticMethod)>
        <m.returnType> result = <m.call>;
    <else>
        <m.returnType> result = obj.<m.call>;
    <endif>

        // perform assertions
<else>
        assertThrows(<m.exceptionClass>.class, () -> {
            <if(m.staticMethod)>
            <m.call>;
            <else>
            obj.<m.call>;
            <endif>
        \});
<endif>
<m.assertions:{a |
        <a>;
}>}>
    \}

}>\}

