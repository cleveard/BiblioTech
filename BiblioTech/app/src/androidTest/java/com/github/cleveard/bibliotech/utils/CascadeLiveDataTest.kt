package com.github.cleveard.bibliotech.utils

import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CascadeLiveDataTest {
    // Test the CascadeLiveData for a single type with multiple values
    fun <T> testType(vararg vals: T?) {
        // Create the live data
        val live = CascadeLiveData<T>()
        // Should have a mutable live data as the source
        assertThat(live.sourceValue::class).isEqualTo(MutableLiveData::class)
        var observedCount = 0       // Count calls to observe
        var observed: T? = null     // Last value in call to observe

        // Test live with one source live data
        fun testOne() {
            // Get the current count
            var count = observedCount
            // Create a source
            val sub1 = MutableLiveData<T>()
            // Set tje source
            live.sourceValue = sub1
            // Check that the source was set
            assertThat(live.sourceValue).isEqualTo(sub1)
            // We didn't call the observe method
            assertThat(observedCount).isEqualTo(count)
            // Set all of the values and make sure observe was called
            for (v in vals) {
                sub1.value = v
                assertThat(observed).isEqualTo(v)
                assertThat(observedCount).isEqualTo(++count)
            }
        }

        // Run on UI Thread because it is required for observeForever
        runOnUiThread {
            // Observe the changes
            live.observeForever { v ->
                observed = v
                ++observedCount
            }
            // Didn't call observe
            assertThat(observedCount).isEqualTo(0)
            // Test for one livedata source
            testOne()
            // Should still work if it is changed
            testOne()
        }
    }

    @Test fun testSourceValue() {
        // Test with ints
        testType(1, 7, 8, null, 3, 10)
        // Test with strings
        testType("1", "8", null, "1123", "54")
    }
}