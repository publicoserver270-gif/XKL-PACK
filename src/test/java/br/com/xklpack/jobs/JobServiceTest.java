package br.com.xklpack.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobServiceTest {
    @Test
    void firstFortyGoalsKeepStartingMultiplier() {
        assertEquals(1.3, JobService.multiplierForGoalNumber(1, 1.3, 0.1, 40), 0.000001);
        assertEquals(1.3, JobService.multiplierForGoalNumber(40, 1.3, 0.1, 40), 0.000001);
    }

    @Test
    void multiplierIncreasesOnlyAfterFortyCompletedGoals() {
        assertEquals(1.4, JobService.multiplierForGoalNumber(41, 1.3, 0.1, 40), 0.000001);
        assertEquals(1.4, JobService.multiplierForGoalNumber(80, 1.3, 0.1, 40), 0.000001);
        assertEquals(1.5, JobService.multiplierForGoalNumber(81, 1.3, 0.1, 40), 0.000001);
    }

    @Test
    void multiplierUsesConfiguredInterval() {
        assertEquals(1.6, JobService.multiplierForGoalNumber(4, 1.3, 0.1, 3), 0.000001);
    }

    @Test
    void invalidGoalAndIntervalAreHandledSafely() {
        assertEquals(1.3, JobService.multiplierForGoalNumber(0, 1.3, 0.1, 0), 0.000001);
    }
}
