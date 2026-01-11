package <packageName>;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class <className> {

<tests:{t |
    @Test
    public void test<i0>() {
<t.methods:{m |
        // Check precondition
        assertTrue(<m.precondition>, "Precondition not met");

<if(m.check.instanceMeth)>
        <m.check.className> obj = new <m.check.className>();
<endif>

<if(m.checks.exception)>
        assertThrows(<m.check.exceptionClass>.class, () -> {
        <if(m.check.instanceMeth)>
            obj.<m.call>;
        <else>
            <m.call>;
        <endif>
        \});
<else>
    <if(m.check.instanceMeth)>
        obj.<m.call>;
    <else>
        <m.call>;
    <endif>
<endif>

        // perform checks
<if(m.check.checks)>
<m.check.checkCalls:{a |
        <a>;
}><endif>
}>
    \}

}>\}

