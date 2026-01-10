package gov.nasa.jpf.jdart.testsuites;

import org.stringtemplate.v4.ST;

import java.io.*;
import java.util.*;

public class TestSuiteFileST {
    private final TestSubSuite testSubSuite;
    private final File outDir;

    private final List<File> sourceFiles = new ArrayList<>();

    public TestSuiteFileST(TestSubSuite testSubSuite, File outDir) {
        this.testSubSuite = testSubSuite;
        this.outDir = outDir;
        outDir.mkdirs();
    }

    public void renderTestSuiteFile(String packageName, String className, Map<String,Object> attributes, InputStream tplIs) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tplIs))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        ST tpl = new ST(sb.toString());

        for (Map.Entry<String, Object> e : attributes.entrySet()) {
            tpl.add(e.getKey(), e.getValue());
        }
        tpl.add("package", packageName);
        tpl.add("class", className);

        String packagePath = packageName.replace('.', File.separatorChar);

        File outputDir = new File(outDir, packagePath);
        outputDir.mkdirs();

        File outputFile = new File(outputDir, className + ".java");

        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(tpl.render());
        }
    }

    private void writeToFile(File outputFile, String content) throws IOException {


        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(content);
        }
    }
}
