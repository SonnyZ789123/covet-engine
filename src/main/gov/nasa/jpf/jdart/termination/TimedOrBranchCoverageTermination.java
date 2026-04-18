package gov.nasa.jpf.jdart.termination;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.exploration.coverage.CfgCoverageTracker;
import gov.nasa.jpf.util.JPFLogger;

/**
 * Stops JDart when either the elapsed wall-clock time exceeds a budget
 * <em>or</em> runtime branch coverage reaches a threshold.
 *
 * Configure in sut.jpf (hours, minutes, seconds, coverage percent):
 *   jdart.termination = gov.nasa.jpf.jdart.termination.TimedOrBranchCoverageTermination,0,0,30,80
 *
 * The branch-coverage check requires a {@link CfgCoverageTracker} to be
 * available on the active {@link gov.nasa.jpf.jdart.config.ConcolicConfig}
 * (loaded automatically by the coverage heuristic, or via
 * {@code jdart.coverage.block_map_path} for DFS/BFS). Without one the
 * coverage arm is skipped and only the time budget applies.
 */
public class TimedOrBranchCoverageTermination extends TerminationStrategy {
  private static final JPFLogger logger = JPF.getLogger("jdart");

  private final long startTimeMillis;
  private final long runTimeMillis;
  private final int thresholdPercent;

  private CfgCoverageTracker coverageTracker;
  private double lastCoverage = 0.0;
  private boolean warnedMissingTracker = false;

  private enum Reason { NONE, TIME, COVERAGE }
  private Reason firedReason = Reason.NONE;

  public TimedOrBranchCoverageTermination(int hours, int minutes, int seconds, int thresholdPercent) {
    if (thresholdPercent < 0 || thresholdPercent > 100) {
      throw new IllegalArgumentException(
          "branch coverage threshold must be in [0,100], got " + thresholdPercent);
    }
    this.runTimeMillis = (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L);
    this.startTimeMillis = System.currentTimeMillis();
    this.thresholdPercent = thresholdPercent;
  }

  public void setCoverageTracker(CfgCoverageTracker tracker) {
    this.coverageTracker = tracker;
  }

  @Override
  public boolean isDone() {
    if (System.currentTimeMillis() >= startTimeMillis + runTimeMillis) {
      firedReason = Reason.TIME;
      return true;
    }
    if (coverageTracker == null) {
      if (!warnedMissingTracker) {
        logger.warning("TimedOrBranchCoverageTermination: no coverage tracker available; "
            + "only the time budget applies.");
        warnedMissingTracker = true;
      }
      return false;
    }
    lastCoverage = coverageTracker.getBranchCoveragePercentage();
    if (lastCoverage >= thresholdPercent) {
      firedReason = Reason.COVERAGE;
      return true;
    }
    return false;
  }

  @Override
  public String getReason() {
    switch (firedReason) {
      case TIME:
        return "Time limit expired (" + runTimeMillis + " ms)";
      case COVERAGE:
        return String.format("Branch coverage threshold reached: %.2f%% >= %d%%",
            lastCoverage, thresholdPercent);
      default:
        return "Resolved all paths!";
    }
  }
}
