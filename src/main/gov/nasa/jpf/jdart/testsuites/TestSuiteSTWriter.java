/*
 * Copyright (C) 2025-2026 Yoran Mertens
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package gov.nasa.jpf.jdart.testsuites;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.util.JPFLogger;
import org.stringtemplate.v4.ST;

import java.io.*;
import java.lang.reflect.Modifier;

public class TestSuiteSTWriter {
    private static class MethodProps {
        public String methodClassName;
        public boolean isStaticMethod;
        public String returnType;

        public MethodProps(String methodClassName, boolean isStaticMethod, String returnType) {
            this.methodClassName = methodClassName;
            this.isStaticMethod = isStaticMethod;
            this.returnType = returnType;
        }
    }

    private static final JPFLogger logger = JPF.getLogger("jdart.testsuites");

    private final TestSuite testSuite;
    private final File outBaseDir;

    // Shared test suite fields for file string template rendering
    private final String packageName;
    private final MethodProps methodProps;

    public TestSuiteSTWriter(TestSuite testSuite, String outBaseDir) {
        this.testSuite = testSuite;
        this.outBaseDir = new File(outBaseDir);

        this.packageName = testSuite.getPackageName();
        this.methodProps = new MethodProps(
                testSuite.getMethodUT().getDeclaringClass().getName(),
                Modifier.isStatic(testSuite.getMethodUT().getModifiers()),
                testSuite.getMethodUT().getReturnType().getName()
        );
    }

    public void write(String templatePath) {
        try {
            if (!testSuite.iterator().hasNext()) {
                logger.info("[TestSuiteGenerator] No tests to write");
                return;
            }

            for (TestSubSuite testSubSuite : testSuite) {
                writeTestSuiteFile(testSubSuite,
                        TestSuiteSTWriter.class.getResourceAsStream(templatePath));
            }
            logger.info("[TestSuiteGenerator] Test suite generated in " + outBaseDir.getAbsolutePath());
        } catch (IOException e) {
            logger.severe("Failed to write test suite files to " + outBaseDir.getAbsolutePath() + ": ", e.getMessage());
        }
    }

    private void writeTestSuiteFile(TestSubSuite testSubSuite, InputStream tplIs) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tplIs))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }

        ST tpl = new ST(sb.toString());

        tpl.add("tests", testSubSuite.getTests());
        tpl.add("packageName", packageName);
        tpl.add("methodProps", methodProps);
        tpl.add("className", testSubSuite.getClassName());

        writeToFile(testSubSuite, tpl.render());
    }

    private void writeToFile(TestSubSuite testSubSuite, String content) throws IOException {
        String packagePath = packageName.replace('.', File.separatorChar);

        File outputDir = new File(outBaseDir, packagePath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String baseName = testSubSuite.getClassName();
        File outputFile;
        int counter = 0;

        do {
            String suffix = (counter == 0) ? "" : "(" + String.valueOf(counter) + ")";
            outputFile = new File(outputDir, baseName + suffix + ".java");
            counter++;
        } while (outputFile.exists());

        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(content);
            logger.info("[TestSuiteGenerator] Test suite file written: " + outputFile.getAbsolutePath());
        }
    }
}
