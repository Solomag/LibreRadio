package com.yuriy.openradio.shared.model.net

import android.os.SystemClock

/**
 * Whether the station catalog (Radio Browser) answered lately.
 * Lets the UI tell "the catalog did not answer" from "this folder is empty".
 */
object CatalogStatus {

    @Volatile
    private var mLastFailureAt = 0L

    @Volatile
    private var mLastSuccessAt = 0L

    fun now(): Long = SystemClock.elapsedRealtime()

    fun onSuccess() {
        mLastSuccessAt = now()
    }

    fun onFailure() {
        mLastFailureAt = now()
    }

    /**
     * @return True when a catalog request failed at or after [since] and none succeeded after that failure.
     */
    fun failedSince(since: Long): Boolean {
        val failure = mLastFailureAt
        return failure >= since && failure >= mLastSuccessAt
    }
}
