package com.buzzingmountain.dingclock.core

/** Outcome of a single state-machine step / DryRun operation. */
sealed class StepResult {
    data object Success : StepResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : StepResult()

    val isSuccess: Boolean get() = this is Success
    fun reasonOrNull(): String? = (this as? Failure)?.reason
}
