package com.frank.magic8ball

import kotlin.reflect.KProperty

/**
 * A thread-safe Kotlin class that provides synchronized access to a Boolean flag.
 * This class uses a lock to ensure thread safety for both reading and updating the flag.
 *
 * @param initValue The initial value of the flag.
 *
 * The class defines custom property delegates using `getValue` and `setValue` operators,
 * which allow it to be used with Kotlin's delegation syntax while ensuring thread-safe
 * reads and writes to the flag.
 *
 * @author Fr4nKB
 */
class SynchronizedFlag(initValue: Boolean) {
    private val lock = Any()
    private var flag = initValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        synchronized(lock) {
            return flag
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        synchronized(lock) {
            flag = value
        }
    }
}