package app.toque.core.recipes

/** Start and chaining rules shared by guided-cooking buttons and automatic transitions. */
object GuidedStepPolicy {
    fun canRun(step: Step, reviewed: Boolean): Boolean =
        step.machine?.valid == true && (!step.needsReview || reviewed)

    /** A following setting can start automatically only after any required review. */
    fun canContinue(steps: List<Step>, index: Int, reviewed: Set<Int>): Boolean {
        val step = steps.getOrNull(index) ?: return false
        val previous = steps.getOrNull(index - 1) ?: return false
        val machine = step.machine ?: return false
        val previousMachine = previous.machine ?: return false
        return canRun(previous, index - 1 in reviewed) && canRun(step, index in reviewed) &&
            machine.program != StepProgram.TURBO && previousMachine.program != StepProgram.TURBO &&
            step.weigh == null && (step.text.isBlank() || step.text == machine.summary)
    }

    fun chainLength(steps: List<Step>, first: Int, reviewed: Set<Int>): Int {
        val step = steps.getOrNull(first) ?: return 0
        if (!canRun(step, first in reviewed)) return 0
        var count = 0
        while (canContinue(steps, first + count + 1, reviewed)) count++
        return count
    }

    fun turboEnabled(step: Step, reviewed: Boolean, busy: Boolean, turboActive: Boolean): Boolean =
        step.machine?.program == StepProgram.TURBO && canRun(step, reviewed) && (!busy || turboActive)
}
