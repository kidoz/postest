package su.kidoz.postest.viewmodel

/**
 * Interface for emitting one-time side effects (toasts, errors, navigation).
 * Used by feature managers to communicate events to the UI without coupling to Channel.
 */
interface SideEffectEmitter {
    /**
     * Emits a side effect to be consumed by the UI.
     * @param effect The side effect to emit
     */
    fun emit(effect: AppSideEffect)
}
