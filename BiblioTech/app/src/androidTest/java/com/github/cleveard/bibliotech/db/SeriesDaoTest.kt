package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.testutils.UndoTracker
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SeriesDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var context: Context
    private lateinit var undo: UndoTracker

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
        undo = UndoTracker(db.getUndoRedoDao())
    }

    @After
    fun tearDown() {
        BookDatabase.close(context)
    }

    @get:Rule
    val timeout = DisableOnAndroidDebug(Timeout(5L, TimeUnit.SECONDS))

    @Test fun testAddUpdateDelete() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddUpdateDelete()
        }
    }

    @Test fun testAddUpdateDeleteWithUndo() {
        runBlocking {
            undo.record("TestAddUpdateDeleteWithUndo") { doTestAddUpdateDelete() }
        }
    }

    private suspend fun doTestAddUpdateDelete() {
        val seriesDao = db.getSeriesDao()
        val series = listOf(
            SeriesEntity(id = 0L, seriesId = "series1", title = "title1", flags = 0),
            SeriesEntity(id = 0L, seriesId = "series\\", title = "title\\", flags = 0),
            SeriesEntity(id = 0L, seriesId = "series%", title = "title%", flags = 0),
        )

        // Add series
        suspend fun addSeries() {
            // Add some categories and books
            for (s in series) {
                assertWithMessage("Add %s", s.title).apply {
                    // Add an series
                    s.id = 0L
                    seriesDao.addWithUndo(s)
                    // Check the id
                    that(s.id).isNotEqualTo(0L)
                    // Look it up by name and check that
                    val foundCategory = seriesDao.findSeriesBySeriesId(s.seriesId)
                    that(foundCategory).isEqualTo(s)
                }
            }

            // Make sure adding an existing category doesn't do anything
            var s = series[2].copy(id = 0L)
            seriesDao.addWithUndo(s)
            assertWithMessage("Update %s", series[2].title).that(s).isEqualTo(series[2])

            // Make sure adding an updated series updates the series
            s = series[2].copy(id = 0L, title = "title2")
            seriesDao.addWithUndo(s)
            assertWithMessage("Update %s", s.title).apply {
                that(seriesDao.findSeriesBySeriesId(s.seriesId)).isEqualTo(s)
                series[2].title = s.title
                that(s).isEqualTo(series[2])
            }
        }

        addSeries()
    }
}
