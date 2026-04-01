# Coverage Heuristic Analysis: `EdgeCoverage.foo` with 70% Initial Coverage

## 1. SUT

### 1.1 Source Code

Same SUT as the [0% coverage analysis](2026-04-01-edge-coverage-analysis.md). Perfect binary tree: 3 nested `if-else` decisions over `(x, y, z)`, 8 return values.

### 1.2 Initial Test Suite

```java
public class InitialEdgeCoverageFooTest {

    @Test
    public void test3() {
        assertEquals(4, EdgeCoverage.foo(-2147483646, 1, 1));    // x<=0, y>0, z>0
    }

    @Test
    public void test5() {
        assertEquals(2, EdgeCoverage.foo(2, -2147483647, 1));    // x>0, y<=0, z>0
    }

    @Test
    public void test7() {
        assertEquals(0, EdgeCoverage.foo(1, 1, 1));              // x>0, y>0, z>0
    }
}
```

3 tests covering 3 paths: `[1,1,1]`, `[1,0,1]`, `[0,1,1]`.

### 1.3 Initial Coverage State (from block map)

**Initial branch coverage: 70%** (14/20 branches covered, as reported by the pipeline).

Coverage per block:

| Block | Decision | State | Edge to left child | Edge to right child |
|-------|----------|-------|-------------------|-------------------|
| 0 | `if(x > 0)` | COVERED | 0->1 (hits=1) | 0->8 (hits=1) |
| 1 | `if(y > 0)` | COVERED | 1->2 (hits=1) | 1->5 (hits=1) |
| 2 | `if(z > 0)` | PARTIALLY_COVERED | 2->3 (hits=1) | **2->4 (hits=0)** |
| 5 | `if(z > 0)` | PARTIALLY_COVERED | 5->6 (hits=1) | **5->7 (hits=0)** |
| 8 | `if(y > 0)` | PARTIALLY_COVERED | 8->9 (hits=1) | **8->12 (hits=0)** |
| 9 | `if(z > 0)` | PARTIALLY_COVERED | 9->10 (hits=1) | **9->11 (hits=0)** |
| 12 | `if(z > 0)` | NOT_COVERED | **12->13 (hits=0)** | **12->14 (hits=0)** |

**Edge coverage: 8/14 edges covered (57.1%).**

Covered edges: `0->1, 0->8, 1->2, 1->5, 2->3, 5->6, 8->9, 9->10`
Uncovered edges: `2->4, 5->7, 8->12, 9->11, 12->13, 12->14`

Visualized on the CFG (x = covered, . = uncovered):

```
                         Node 0: if(x > 0)
                         [COVERED]
                       /                        \
                  x 0->1                     x 0->8
                    /                              \
           Node 1: if(y > 0)              Node 8: if(y > 0)
           [COVERED]                      [PARTIAL]
           /              \                /              \
      x 1->2          x 1->5         x 8->9          . 8->12
        /                \              /                \
  Node 2 [PARTIAL]  Node 5 [PARTIAL]  Node 9 [PARTIAL]  Node 12 [NOT_COVERED]
   /       \           /       \          /       \           /        \
x 2->3   . 2->4     x 5->6   . 5->7   x 9->10  . 9->11   . 12->13  . 12->14
  |       |         |       |          |       |          |         |
 [3]     [4]       [6]     [7]       [10]    [11]       [13]      [14]
ret 0   ret 1     ret 2   ret 3     ret 4   ret 5     ret 6     ret 7
 HIT              HIT                HIT
```

**Pattern:** The initial tests all use `z>0`, so every `z<=0` branch is uncovered. Additionally, only the `y>0` branch of node 8 (`x<=0`) is covered, leaving the entire `x<=0, y<=0` subtree (block 12) untouched.

### 1.4 Generated Test Suite (JDart Output)

```java
@Test public void test0() { assertEquals(7, EdgeCoverage.foo(-2147483647, 0, 0)); }
@Test public void test1() { assertEquals(6, EdgeCoverage.foo(-2147483646, -2147483647, 1)); }
@Test public void test2() { assertEquals(5, EdgeCoverage.foo(-2147483646, 1, 0)); }
@Test public void test3() { assertEquals(3, EdgeCoverage.foo(2, -2147483647, 0)); }
@Test public void test4() { assertEquals(1, EdgeCoverage.foo(2, 1, -2147483647)); }
```

**5 tests generated** (not 8). The 3 paths already covered by the initial test suite are marked IGNORE and do not produce test cases.

## 2. Coverage Heuristic Strategy

### 2.1 Order of Execution

| # | Solver Target | Valuation | Path | New Edges | Result |
|---|--------------|-----------|------|-----------|--------|
| 0 | `[]` (initial) | `x=1, y=1, z=1` | `[1,1,1]` | -- | IGNORE (test7) |
| 1 | `[0]` | `x=-2147483647` | `[0,0,0]` | 8->12, 12->14 | OK |
| 2 | `[0,1]` | `x=-2147483646, y=1` | `[0,1,0]` | 9->11 | OK |
| 3 | `[1,1,0]` | `x=2, y=1, z=-2147483647` | `[1,1,0]` | 2->4 | OK |
| 4 | `[0,0,1]` | `x=-2147483646, y=-2147483647, z=1` | `[0,0,1]` | 12->13 | OK |
| 5 | `[1,0]` | `x=2, y=-2147483647` | `[1,0,0]` | 5->7 | OK |
| 6 | `[1,0,1]` | `x=2, y=-2147483647, z=1` | `[1,0,1]` | -- | IGNORE (test5) |
| 7 | `[0,1,1]` | `x=-2147483646, y=1, z=1` | `[0,1,1]` | -- | IGNORE (test3) |

**100% edge coverage reached after execution 5** (6 executions). Executions 6 and 7 are IGNORE paths that add no new coverage.

### 2.2 Order Analysis

**Execution 0** -- Initial seed `(1,1,1)` from `main()`. Path `[1,1,1]`: `x>0, y>0, z>0 -> return 0`. All edges on this path (0->1, 1->2, 2->3) are already covered by test7. No new edges. Marked IGNORE.

**Execution 1** -- Target `[0]` (`x<=0`). The heuristic negates the root branch. The solver picks `x=-2147483647` with unconstrained `y=0, z=0`. Path `[0,0,0]`: `x<=0, y<=0, z<=0 -> return 7`. This enters the entirely uncovered `x<=0, y<=0` subtree, discovering 2 new edges: `8->12` and `12->14`.

**Execution 2** -- Target `[0,1]` (`x<=0, y>0`). The heuristic stays in the `x<=0` subtree and explores the `y>0` branch. Path `[0,1,0]`: `x<=0, y>0, z<=0 -> return 5`. Edge `8->9` is already covered (from test3), but `9->11` is NEW (the `z<=0` branch at node 9).

**Execution 3 -- THIS IS WHERE THE HEURISTIC DIVERGES FROM THE 0% CASE.**

Target `[1,1,0]` (`x>0, y>0, z<=0`). In the 0% coverage analysis, the next target after `[0,1,0]` was `[1,0]` (a depth-2 target exploring the `x>0, y<=0` subtree). Here, the heuristic skips to a depth-3 target.

Why? The heuristic knows (from the concolic tree and block map) that:
- `[1,1]` was already explored by exec 0
- `[1,1,1]` is IGNORE (covered by test7)
- Therefore `[1,1,0]` is the uncovered sibling -- a known uncovered leaf

The heuristic **prefers known uncovered siblings over unexplored subtrees**. Targeting `[1,1,0]` is a guaranteed hit (1 new edge: `2->4`), while `[1,0]` would require exploring an unknown subtree that contains a mix of covered and uncovered paths.

**Execution 4** -- Target `[0,0,1]` (`x<=0, y<=0, z>0`). Same logic: the heuristic knows `[0,0,0]` was exec 1, so the sibling `[0,0,1]` is a known uncovered leaf. Covers edge `12->13`. After this, only 1 uncovered edge remains: `5->7`.

**Execution 5** -- Target `[1,0]` (`x>0, y<=0`). Now the heuristic must explore the `[1,0]` subtree. The solver picks `x=2, y=-2147483647` with `z=0`. Path `[1,0,0]`: `x>0, y<=0, z<=0 -> return 3`. Covers the last uncovered edge: `5->7`. **100% edge coverage reached.**

**Executions 6-7** -- The concolic tree explorer must complete the enumeration. It executes the two remaining paths, `[1,0,1]` and `[0,1,1]`, which are both covered by the initial test suite. Both produce no new edges and are marked IGNORE. No test cases are generated for these paths.

### 2.3 Comparison: 0% vs 70% Initial Coverage

| Property | 0% Coverage | 70% Coverage |
|----------|-------------|--------------|
| Initial edge coverage | 0/14 | 8/14 |
| Total executions | 8 | 8 |
| OK paths (tests generated) | 8 | 5 |
| IGNORE paths | 0 | 3 |
| Execs to reach 100% edges | 8 | 6 (exec 0-5) |
| Wasted executions | 0 | 3 (execs 0, 6, 7) |
| New edges per productive exec | 1.5 avg | 1.2 avg |

**Exploration order comparison:**

```
0% :  [1,1,1] -> [0,0,0] -> [0,1,0] -> [1,0,0] -> [0,1,1] -> [1,0,1] -> [0,0,1] -> [1,1,0]
70%:  [1,1,1] -> [0,0,0] -> [0,1,0] -> [1,1,0] -> [0,0,1] -> [1,0,0] -> [1,0,1] -> [0,1,1]
                                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^
                                         divergence: depth-3 targets first  IGNORE paths last
```

The first 3 executions are identical. After that:
- **0% case**: continues DFS-like exploration at depth 2, hitting `[1,0,0]` then exploring siblings
- **70% case**: jumps to depth-3 known uncovered siblings (`[1,1,0]`, `[0,0,1]`), then explores `[1,0]`, and pushes IGNORE paths to the end

### 2.4 Edge Coverage Progression

```
        0%                              70%
Exec 0  ||..........  14.3% (+2)       ||||||||....  57.1% (+0, IGNORE)
Exec 1  ||||........  28.6% (+2)       ||||||||||..  71.4% (+2)
Exec 2  ||||||......  42.9% (+2)       |||||||||||.  78.6% (+1)
Exec 3  ||||||||....  57.1% (+2)       ||||||||||||  85.7% (+1)
Exec 4  |||||||||...  64.3% (+1)       |||||||||||||  92.9% (+1)
Exec 5  ||||||||||..  71.4% (+1)       ||||||||||||||  100% (+1) DONE
Exec 6  |||||||||||.  78.6% (+1)       ||||||||||||||  100% (+0, IGNORE)
Exec 7  ||||||||||||  85.7% (+1)       ||||||||||||||  100% (+0, IGNORE)
```

(12 tracked edges shown. With implicit root edges, both reach 14/14 = 100%.)

### 2.5 Key Observations

**1. The heuristic correctly identifies and deprioritizes covered paths.** The 3 paths already tested (`[1,1,1]`, `[1,0,1]`, `[0,1,1]`) are explored last and marked IGNORE. In the 0% case, these paths appeared at positions 0, 5, and 4 respectively. In the 70% case, they are at positions 0, 6, and 7.

**2. The heuristic prefers known uncovered siblings over unexplored subtrees.** After exec 2, instead of exploring the unknown `[1,0]` subtree (which contains the covered path `[1,0,1]`), it targets the known uncovered leaf `[1,1,0]` (sibling of the already-explored `[1,1,1]`). This avoids potentially wasting an execution on a covered path.

**3. 100% edge coverage is reached after 6 executions (vs 8 in the 0% case).** The heuristic doesn't reduce the total number of executions (both cases run 8), but it frontloads productive executions. Under a time budget, the 70% case reaches full coverage 2 executions sooner.

**4. Only 5 test cases are generated instead of 8.** The 3 IGNORE paths don't produce test cases, avoiding redundant tests that the initial suite already covers.

**5. The constraints tree correctly reflects the IGNORE paths:**

```
-('x' <= 0)
  |-[+]-('y' <= 0)
  |      |-[+]-('z' <= 0)
  |      |      |-[+]_/OK         [0,0,0] exec 1 -> return 7
  |      |      +-[-]_/OK         [0,0,1] exec 4 -> return 6
  |      +-[-]-('z' <= 0)
  |            |-[+]_/OK          [0,1,0] exec 2 -> return 5
  |            +-[-]_/IGNORE      [0,1,1] exec 7 -> return 4 (test3)
  +-[-]-('y' <= 0)
        |-[+]-('z' <= 0)
        |      |-[+]_/OK          [1,0,0] exec 5 -> return 3
        |      +-[-]_/IGNORE      [1,0,1] exec 6 -> return 2 (test5)
        +-[-]-('z' <= 0)
              |-[+]_/OK           [1,1,0] exec 3 -> return 1
              +-[-]_/IGNORE       [1,1,1] exec 0 -> return 0 (test7)
```

The 3 IGNORE leaves correspond exactly to the 3 initial test paths: `z>0` branches where initial tests existed.

**6. The heuristic behaves as expected.** All uncovered edges are discovered before any IGNORE path is revisited. The exploration order is coverage-optimal for this CFG structure.

## 3. JDart Statistics

| Metric | 0% Coverage | 70% Coverage |
|--------|-------------|--------------|
| Total paths | 8 | 8 |
| OK paths | 8 | 5 |
| IGNORE paths | 0 | 3 |
| Valuations generated | 8 | 5 |
| JDart execution time | 532 ms | 471 ms |
| JPF boot time | 354 ms | 271 ms |
