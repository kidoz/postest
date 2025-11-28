package su.kidoz.postest.viewmodel

/**
 * Functional interface for updating application state atomically.
 * Used by feature managers to modify shared state without direct access to MutableStateFlow.
 */
fun interface StateUpdater<T> {
    /**
     * Atomically updates the state using the provided transform function.
     * @param transform Function that takes current state and returns new state
     */
    fun update(transform: (T) -> T)
}
