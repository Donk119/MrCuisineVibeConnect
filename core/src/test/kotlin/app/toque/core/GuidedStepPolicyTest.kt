package app.toque.core

import app.toque.core.recipes.GuidedStepPolicy
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.Weigh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedStepPolicyTest {
    private fun manual(needsReview: Boolean = true) =
        Step("", StepMachine(seconds = 10, speed = 2), needsReview = needsReview)

    @Test fun importedSettingCannotStartBeforeReview() {
        assertFalse(GuidedStepPolicy.canRun(manual(), reviewed = false))
        assertTrue(GuidedStepPolicy.canRun(manual(), reviewed = true))
        assertTrue(GuidedStepPolicy.canRun(manual(needsReview = false), reviewed = false))
    }

    @Test fun reviewDoesNotOverrideMachineSafetyOrCreateAMachineStep() {
        val invalid = manual().copy(machine = StepMachine(seconds = 10, speed = 11))
        assertFalse(GuidedStepPolicy.canRun(invalid, reviewed = true))
        assertFalse(GuidedStepPolicy.canRun(Step("Add ingredients"), reviewed = true))
    }

    @Test fun reviewedFirstStepDoesNotAuthorizeTheFollowingImportedSteps() {
        val steps = listOf(manual(), manual(), manual())
        val reviewed = setOf(0)
        assertEquals(0, GuidedStepPolicy.chainLength(steps, 0, reviewed))
        assertFalse(GuidedStepPolicy.canContinue(steps, 1, reviewed))
        assertEquals(setOf(0), reviewed)
    }

    @Test fun chainStopsAtFirstUnreviewedSettingAndCannotSkipIt() {
        val steps = listOf(manual(), manual(), manual(), manual())
        assertEquals(1, GuidedStepPolicy.chainLength(steps, 0, setOf(0, 1, 3)))
        assertFalse(GuidedStepPolicy.canContinue(steps, 2, setOf(0, 1, 3)))
    }

    @Test fun individuallyReviewedSettingsCanStillChain() {
        val steps = listOf(manual(), manual(), manual())
        assertEquals(2, GuidedStepPolicy.chainLength(steps, 0, setOf(0, 1, 2)))
        assertTrue(GuidedStepPolicy.canContinue(steps, 1, setOf(0, 1, 2)))
    }

    @Test fun trustedSettingsKeepExistingChainingBehavior() {
        val steps = listOf(manual(false), manual(false), manual(false))
        assertEquals(2, GuidedStepPolicy.chainLength(steps, 0, emptySet()))
    }

    @Test fun firstSettingAlsoRequiresReviewEvenIfFollowingSettingsWereReviewed() {
        val steps = listOf(manual(), manual())
        assertEquals(0, GuidedStepPolicy.chainLength(steps, 0, setOf(1)))
        assertFalse(GuidedStepPolicy.canContinue(steps, 1, setOf(1)))
    }

    @Test fun manualInstructionsAndWeighingStillBreakAChain() {
        val first = manual(false)
        assertFalse(GuidedStepPolicy.canContinue(listOf(first, first.copy(text = "Add flour")), 1, emptySet()))
        assertFalse(GuidedStepPolicy.canContinue(listOf(first, first.copy(weigh = Weigh("Flour", 100))), 1, emptySet()))
        val summary = first.copy(text = first.machine!!.summary)
        assertTrue(GuidedStepPolicy.canContinue(listOf(first, summary), 1, emptySet()))
    }

    @Test fun turboAlwaysRemainsOutsideAutomaticChains() {
        val turbo = Step("", StepMachine(program = StepProgram.TURBO))
        assertFalse(GuidedStepPolicy.canContinue(listOf(manual(false), turbo), 1, emptySet()))
        assertFalse(GuidedStepPolicy.canContinue(listOf(turbo, manual(false)), 1, emptySet()))
    }

    @Test fun snapshotsCannotEnableUnreviewedTurboWhenTheMachineBecomesIdle() {
        val turbo = Step("", StepMachine(program = StepProgram.TURBO), needsReview = true)
        assertFalse(GuidedStepPolicy.turboEnabled(turbo, reviewed = false, busy = true, turboActive = false))
        assertFalse(GuidedStepPolicy.turboEnabled(turbo, reviewed = false, busy = false, turboActive = false))
        assertFalse(GuidedStepPolicy.turboEnabled(turbo, reviewed = false, busy = true, turboActive = true))
        assertTrue(GuidedStepPolicy.turboEnabled(turbo, reviewed = true, busy = false, turboActive = false))
        assertFalse(GuidedStepPolicy.turboEnabled(turbo, reviewed = true, busy = true, turboActive = false))
        assertTrue(GuidedStepPolicy.turboEnabled(turbo, reviewed = true, busy = true, turboActive = true))
    }

    @Test fun invalidOrMissingStepsCannotContinue() {
        val steps = listOf(manual(false), manual(false).copy(machine = StepMachine(seconds = 10, speed = 11)))
        assertFalse(GuidedStepPolicy.canContinue(steps, 1, emptySet()))
        assertFalse(GuidedStepPolicy.canContinue(steps, 0, emptySet()))
        assertFalse(GuidedStepPolicy.canContinue(steps, 2, emptySet()))
        assertEquals(0, GuidedStepPolicy.chainLength(steps, 2, emptySet()))
    }
}
