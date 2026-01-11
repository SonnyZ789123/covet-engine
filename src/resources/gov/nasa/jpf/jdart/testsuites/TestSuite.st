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
<endif>

        // perform checks
<m.assertions:{a |
        <a>;
}>}>
    \}

}>\}

