package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.coverage.model.ClassDTO;
import com.kuleuven.coverage.model.LineDTO;

import java.util.HashMap;
import java.util.Map;

public class ClassCoverage {

    private final String name;
    private final ClassDTO classCoverage;
    private final Map<Integer, CoverageType> lineCoverageMap = new HashMap<>();

    public ClassCoverage(String name, ClassDTO classCoverage) {
        this.name = name;
        this.classCoverage = classCoverage;
        initLineCoverageMap();
    }

    private void initLineCoverageMap() {
        classCoverage.methods.forEach(method -> {
            method.lines.forEach(line -> {
                CoverageType lineCoverageType = getLineCoverageType(line);
                lineCoverageMap.put(line.line, lineCoverageType);
            });
        });
    }

    private CoverageType getLineCoverageType(LineDTO line) {
        if (line.hits == 0) {
            return CoverageType.NONE;
        }
        if (line.branches.covered != line.branches.total) {
            return CoverageType.PARTIAL;
        }
        return CoverageType.FULL;
    }

    public CoverageType getLineCoverageType(int line) {
        return lineCoverageMap.get(line);
    }
}
