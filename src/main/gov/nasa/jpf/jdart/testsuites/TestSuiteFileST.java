package gov.nasa.jpf.jdart.testsuites;

import org.stringtemplate.v4.ST;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;

public class TestSuiteFileST {
    private final TestSubSuite testSubSuite;
    private final File outBaseDir;

    public TestSuiteFileST(TestSubSuite testSubSuite, String outBaseDir) {
        this.testSubSuite = testSubSuite;
        this.outBaseDir = new File(outBaseDir);
    }

    public void writeTestSuiteFile(InputStream tplIs) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tplIs))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        ST tpl = new ST(sb.toString());

        tpl.add("tests", testSubSuite.getTests());
        tpl.add("packageName", testSubSuite.getPackageName());
        tpl.add("className", testSubSuite.getClassName());
        tpl.add("methodClassName", testSubSuite.getMethodUT().getDeclaringClass().getName());
        tpl.add("isStaticMethod", Modifier.isStatic(testSubSuite.getMethodUT().getModifiers()));

        writeToFile(tpl.render());
    }

    private void writeToFile(String content) throws IOException {
        String packagePath = testSubSuite.getPackageName().replace('.', File.separatorChar);

        File outputDir = new File(outBaseDir, packagePath);
        outputDir.mkdirs();

        File outputFile = new File(outputDir, testSubSuite.getClassName() + ".java");

        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(content);
        }
    }
}
