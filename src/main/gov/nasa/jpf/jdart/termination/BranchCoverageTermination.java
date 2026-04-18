package gov.nasa.jpf.jdart.termination;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.exploration.CoverageHeuristicStrategy;
import gov.nasa.jpf.util.JPFLogger;

/**
 * Stops JDart once the runtime branch coverage of the method under test
 * (initial test-suite hits + JDart-discovered branches) reaches a configured
 * threshold percentage.
 *
 * Configure in sut.jpf:
 *   jdart.termination = gov.nasa.jpf.jdart.termination.BranchCoverageTermination,80
 *
 * Requires the exploration strategy to be {@link CoverageHeuristicStrategy};
 * with any other strategy this terminator never fires.
 */
public class BranchCoverageTermination extends TerminationStrategy {
  private static final JPFLogger logger = JPF.getLogger("jdart");

  private final int thresholdPercent;
  private CoverageHeuristicStrategy coverageStrategy;
  private double lastCoverage = 0.0;
  private boolean warnedMissingStrategy = false;


  public BranchCoverageTermination(int thresholdPercent) {
    if (thresholdPercent < 0 || thresholdPercent > 100) {
      throw new IllegalArgumentException(
          "branch coverage threshold must be in [0,100], got " + thresholdPercent);
    }
    this.thresholdPercent = thresholdPercent;
  }

  public void setCoverageStrategy(CoverageHeuristicStrategy strategy) {
    this.coverageStrategy = strategy;
  }

  @Override
  public boolean isDone() {
    if (coverageStrategy == null) {
      if (!warnedMissingStrategy) {
        logger.warning("BranchCoverageTermination requires CoverageHeuristicStrategy; "
            + "never terminating.");
        warnedMissingStrategy = true;
      }
      return false;
    }
    lastCoverage = coverageStrategy.getBranchCoveragePercentage();
    return lastCoverage >= thresholdPercent;
  }

  @Override
  public String getReason() {
    return String.format("Branch coverage threshold reached: %.2f%% >= %d%%",
        lastCoverage, thresholdPercent);
  }
}
