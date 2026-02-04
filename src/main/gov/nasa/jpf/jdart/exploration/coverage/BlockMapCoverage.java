package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.blockmap.model.BlockMapDTO;
import com.kuleuven.blockmap.model.MethodBlockMapDTO;

import java.util.HashMap;
import java.util.Map;

public class BlockMapCoverage {

    private final BlockMapDTO blockMap;
    private final Map<String, MethodBlockMapCoverage> methodToBlockMapCoverage = new HashMap<>();

    public BlockMapCoverage(BlockMapDTO blockMap) {
        this.blockMap = blockMap;

        for (MethodBlockMapDTO methodCoverage : blockMap.methodBlockMaps) {
            // JDart works with dot notation for method full names
            String jdartFullName = methodCoverage.fullName.replace('/', '.');

            methodToBlockMapCoverage.put(jdartFullName, new MethodBlockMapCoverage(methodCoverage));
        }
    }

    public MethodBlockMapCoverage getMethodBlockMapCoverage(String methodFullName) {
        return methodToBlockMapCoverage.get(methodFullName);
    }
}
