package gov.nasa.jpf.jdart.termination;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.exploration.coverage.CfgCoverageTracker;
import gov.nasa.jpf.util.JPFLogger;

/**
 * Stops JDart once the runtime branch coverage of the method under test
 * (initial test-suite hits + JDart-discovered branches) reaches a configured
 * threshold percentage.
 *
 * Configure in sut.jpf:
 *   jdart.termination = gov.nasa.jpf.jdart.termination.BranchCoverageTermination,80
 *
 * Requires a {@link CfgCoverageTracker} to be available on the active
 * {@link gov.nasa.jpf.jdart.config.ConcolicConfig} (loaded automatically by
 * the coverage heuristic, or via {@code jdart.coverage.block_map_path} for
 * other strategies). Without one this terminator never fires.
 */
public class BranchCoverageTermination extends TerminationStrategy {
  private static final JPFLogger logger = JPF.getLogger("jdart");

  private final int thresholdPercent;
  private CfgCoverageTracker coverageTracker;
  private double lastCoverage = 0.0;
  private boolean warnedMissingTracker = false;

  public BranchCoverageTermination(int thresholdPercent) {
    if (thresholdPercent < 0 || thresholdPercent > 100) {
      throw new IllegalArgumentException(
          "branch coverage threshold must be in [0,100], got " + thresholdPercent);
    }
    this.thresholdPercent = thresholdPercent;
  }

  public void setCoverageTracker(CfgCoverageTracker tracker) {
    this.coverageTracker = tracker;
  }

  @Override
  public boolean isDone() {
    if (coverageTracker == null) {
      if (!warnedMissingTracker) {
        logger.warning("BranchCoverageTermination requires a coverage tracker "
            + "(coverage heuristic or jdart.coverage.block_map_path); never terminating.");
        warnedMissingTracker = true;
      }
      return false;
    }
    lastCoverage = coverageTracker.getBranchCoveragePercentage();
    return lastCoverage >= thresholdPercent;
  }

  @Override
  public String getReason() {
    return String.format("Branch coverage threshold reached: %.2f%% >= %d%%",
        lastCoverage, thresholdPercent);
  }
}
