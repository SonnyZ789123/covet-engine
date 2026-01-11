package <packageName>;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <className> {

<tests:{t |
    @Test
    public void test<i0>() {
<t.methods:{m |
<if(m.checks.instanceMeth)>
        <m.checks.className> obj = new <m.checks.className>();
<endif>

<if(m.checks.exception)>
        assertThrows(<m.checks.exceptionClass>.class, () -> {
        <if(m.checks.instanceMeth)>
            obj.<m.call>;
        <else>
            <m.call>;
        <endif>
        \});
<else>
    <if(m.checks.instanceMeth)>
        <m.returnType> result = obj.<m.call>;
    <else>
        <m.returnType> result = <m.call>;
    <endif>
<endif>


<if(m.checks.checks)>
        // perform checks
<m.checks.checkCalls:{a |
        <a>;
}><endif>
}>
    \}

}>\}

