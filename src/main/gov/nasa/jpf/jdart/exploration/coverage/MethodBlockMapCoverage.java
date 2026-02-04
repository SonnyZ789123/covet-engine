package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.blockmap.model.BlockCoverageDataDTO;
import com.kuleuven.blockmap.model.BlockDataDTO;
import com.kuleuven.blockmap.model.MethodBlockMapDTO;
import com.kuleuven.coverage.model.LineDTO;

import java.util.HashMap;
import java.util.Map;

public class MethodBlockMapCoverage {

    private final MethodBlockMapDTO methodBlockMap;

    private final Map<Integer, BlockDataDTO> lineToBlockDataMap = new HashMap<>();

    public MethodBlockMapCoverage(MethodBlockMapDTO methodBlockMap) {
        this.methodBlockMap = methodBlockMap;

        for (BlockDataDTO blockCoverage : methodBlockMap.blocks) {
            for (LineDTO lineCoverage : blockCoverage.coverageData.lines) {
                lineToBlockDataMap.put(lineCoverage.line, blockCoverage);
            }
        }
    }

    public BlockCoverageDataDTO.CoverageState getCoverageStateForLine(int line) {
        BlockDataDTO blockData = lineToBlockDataMap.get(line);
        if (blockData == null) {
            return null;
        }
        return blockData.coverageData.coverageState;
    }
}
