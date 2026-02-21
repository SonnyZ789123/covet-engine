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

    /**
     * Get the coverage state for a block, given the line number of a statement in that block.
     *
     * @param line line number of a statement in the block
     * @return the coverage state for the block, or null if the line does not map to any block
     */
    public BlockCoverageDataDTO.CoverageState getCoverageStateForLine(int line) {
        BlockDataDTO blockData = lineToBlockDataMap.get(line);
        if (blockData == null) {
            return null;
        }
        return blockData.coverageData.coverageState;
    }

    public String getBlockHashForLine(int line) {
        BlockDataDTO blockData = lineToBlockDataMap.get(line);
        if (blockData == null) {
            return null;
        }
        return blockData.sourceHash;
    }
}
