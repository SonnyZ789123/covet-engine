package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.coverage.model.ClassDTO;
import com.kuleuven.coverage.model.CoverageReportDTO;

import java.util.HashMap;
import java.util.Map;

public class CoverageReport {

    private final CoverageReportDTO coverageReport;
    private final Map<String, ClassCoverage> classCoverageMap = new HashMap<>();

    public CoverageReport(CoverageReportDTO coverageReport) {
        this.coverageReport = coverageReport;

        for (ClassDTO classCoverage : coverageReport.classes) {
            String className = classCoverage.name;
            ClassCoverage cc = new ClassCoverage(className, classCoverage);
            classCoverageMap.put(className, cc);
        }
    }

    public ClassCoverage getClassCoverage(String className) {
        return classCoverageMap.get(className);
    }
}
