# Fix: Line-to-block mapping collision in CfgCoverageTracker

**Date:** 2026-04-10

## Problem

The `CfgCoverageTracker` used a simple `Map<Integer, Integer>` to map source line numbers to block IDs. When multiple CFG blocks shared the same source line (which is very common), the last block processed overwrote previous entries.

This caused two classes of mis-mapping:

1. **Body blocks overwriting branch blocks:** For `if (x < 25) risk += 20;` on line 34, both the branch block (with IF_TRUE/IF_FALSE edges) and the body block (with a GOTO edge) share line 34. The body block overwrote the branch block. Since body blocks have only a GOTO edge (always "covered"), the IGNORE check treated every branch at that line as covered.

2. **Compound conditions:** For `if (a < 18 || a > 100)`, both comparisons generate separate branch blocks at the same line. Non-branch instructions (iload, sipush) at intermediate positions polluted the position-to-block mapping.

### Impact on RiskClassifier

With `com.comparison.RiskClassifier#classify` (62.14% initial coverage):
- **Before fix:** 7 OK paths, 218 IGNORE, 82.14% final coverage
- **After fix:** 9 OK paths, 216 IGNORE, 98.57% final coverage

The fix exceeds the 84.29% DFS benchmark with fewer test cases (9 vs 20).

## Root cause

In `CfgCoverageTracker` constructor:
```java
for (LineDTO line : block.coverageData.lines) {
    lineToBlockId.put(line.line, block.id);  // last block wins
}
```

Blocks are processed in ID order (topological). For a line like `if (age < 25) risk += 20;`:
- Block 23 (IF branch, line 34) -> lineToBlockId[34] = 23
- Block 24 (GOTO body, line 34) -> lineToBlockId[34] = 24 (overwrites!)

When `getBlockIdForInstruction` looked up a branch instruction at line 34, it returned Block 24 (the body block with a single GOTO edge, always "covered") instead of Block 23 (the actual branch block with IF_TRUE/IF_FALSE edges).

## Fix

Replaced the simple `lineToBlockId` map with a multi-level resolution strategy:

1. **Filter to branch blocks only:** Only blocks with conditional edges (IF_TRUE/IF_FALSE/SWITCH) are included in the line-to-block mapping. Body blocks (GOTO/NORMAL only) are excluded, preventing them from overwriting branch blocks.

2. **Multi-block line disambiguation:** For compound conditions where multiple branch blocks share a line, uses bytecode instruction position ordering: lower position maps to lower block ID (first block in CFG topological order).

3. **Two-pass edge extraction:** `extractCfgEdges` first registers all branch instruction positions (pass 1), then resolves block IDs (pass 2). This ensures compound conditions have all positions known before assignment.

4. **Position isolation:** Only branch instructions from `extractCfgEdges` register positions. Target instructions from `addChildren` (which may be non-branch instructions at the same line) return -1 instead of corrupting the position-to-block mapping.

### Key data structures

- `methodLineToBranchBlockIds`: `Map<String, Map<Integer, List<Integer>>>` -- per-method, per-line sorted list of branch block IDs
- `instructionBlockIdCache`: `IdentityHashMap<Instruction, Integer>` -- resolved instruction-to-block cache
- `linePositionTrackers`: `Map<String, Map<Integer, TreeMap<Integer, Instruction>>>` -- per-method, per-line position tracker for compound conditions
- `conditionalBranchBlockIds`: `Set<Integer>` -- blocks with IF/SWITCH edges

## Files changed

- `src/main/gov/nasa/jpf/jdart/exploration/coverage/CfgCoverageTracker.java` -- complete rewrite of line-to-block mapping
