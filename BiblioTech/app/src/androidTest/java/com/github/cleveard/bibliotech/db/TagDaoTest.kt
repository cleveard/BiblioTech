package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.testutils.UndoTracker
import com.github.cleveard.bibliotech.utils.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.*
import org.junit.*
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TagDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var undo: UndoTracker
    private lateinit var context: Context

    @Before fun startUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
        undo = UndoTracker(db.getUndoRedoDao())
    }

    @After fun tearDown() {
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

    private suspend fun doTestAddUpdateDelete()
    {
        // Add a couple of tags
        val tags = listOf(
            TagEntity(0L, "tag1", "desc1", 0),
            TagEntity(0L, "tag\\", "desc\\", 0),
            TagEntity(0L, "tag_", "desc_", 0),
        )

        // Check Add
        val tagDao = db.getTagDao()
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                tagDao.addWithUndo(t)
                that(t.id).isNotEqualTo(0L)
                that(tagDao.get(t.id)).isEqualTo(t)
                that(tagDao.findByName(t.name)).isEqualTo(t)
            }
        }

        // Verify that we can update
        val update = tags[2].copy(id = 0L)
        update.isSelected = true
        assertWithMessage("Update Succeeded %s", update.name).apply {
            tagDao.addWithUndo(update) { true }
            that(update.id).isEqualTo(tags[2].id)
            that(tagDao.get(update.id)).isEqualTo(update)
            that(tagDao.findByName(update.name)).isEqualTo(update)
        }

        // Change the name
        update.name = "tag%"
        assertWithMessage("Name Change %s", update.name).apply {
            tagDao.addWithUndo(update)   // Don't expect a conflict
            that(update.id).isEqualTo(tags[2].id)
            that(tagDao.get(update.id)).isEqualTo(update)
            that(tagDao.findByName(update.name)).isEqualTo(update)
            that(tagDao.findByName(tags[2].name)).isNull()
        }

        // Merge two tags
        update.name = tags[1].name
        assertWithMessage("Merge Tags %s", update.name).apply {
            tagDao.addWithUndo(update) { true }    // Don't expect a conflict
            that(update.id).isAnyOf(tags[2].id, tags[1].id)
            that(tagDao.get(update.id)).isEqualTo(update)
            that(tagDao.findByName(update.name)).isEqualTo(update)
            that(tagDao.findByName("tag%")).isNull()
            if (update.id == tags[2].id)
                that(tagDao.get(tags[1].id)).isNull()
            else
                that(tagDao.get(tags[2].id)).isNull()
        }

        // Make sure we can delete using id
        tagDao.delete(update.id)
        assertWithMessage("Delete %s", update.name).that(tagDao.get(update.id)).isNull()

        // Delete selected
        assertWithMessage("Delete selected").apply {
            tags[2].isSelected = true
            tags[2].id = 0L
            tags[1].isSelected = true
            tags[1].id = 0L
            tagDao.addWithUndo(tags[2])
            that(tagDao.get(tags[2].id)).isEqualTo(tags[2])
            tagDao.addWithUndo(tags[1])
            that(tagDao.get(tags[1].id)).isEqualTo(tags[1])
            tagDao.deleteSelectedWithUndo()
            that(tagDao.get(tags[2].id)).isNull()
            that(tagDao.get(tags[1].id)).isNull()
        }
    }

    @Test fun testQueries() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestQueries()
        }
    }

    @Test fun testQueriesWithUndo() {
        runBlocking {
            undo.record("TestQueriesWithUndo") { doTestQueries() }
        }
    }

    private suspend fun doTestQueries() {
        // Add a couple of tags
        val tags = listOf(
            TagEntity(0L, "tag1", "desc1", 0),
            TagEntity(0L, "tag\\", "desc\\", TagEntity.SELECTED),
            TagEntity(0L, "tag_", "desc_", 0),
        )

        // Check Add
        val tagDao = db.getTagDao()
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                tagDao.addWithUndo(t)
                that(t.id).isNotEqualTo(0L)
                that(tagDao.get(t.id)).isEqualTo(t)
                that(tagDao.findByName(t.name)).isEqualTo(t)
            }
        }

        // Check the paging source get
        assertWithMessage("PagingSource").apply {
            val page = tagDao.get()
            val result = page.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = tags.size,
                    placeholdersEnabled = false
                )
            )
            that(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, TagEntity>
            that(result.data.size).isEqualTo(tags.size)
            for (i in tags.indices)
                that(result.data[i]).isEqualTo(tags[i])
        }

        var tagList: List<TagEntity>?
        // Check the live list
        assertWithMessage("getLive").apply {
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(tags.size)
            for (i in tags.indices)
                that(tagList?.get(i)).isEqualTo(tags[i])
        }

        // Check the live list, selected
        assertWithMessage("getLive selected").apply {
            tagList = tagDao.getLive(true).getLive()
            that(tagList?.size).isEqualTo(1)
            that(tagList?.get(0)).isEqualTo(tags[1])
        }

        var idList: List<Long>?
        assertWithMessage("queryTagIds: null").apply {
            idList = tagDao.queryTagIds(null)
            that(idList?.size).isEqualTo(1)
            that(idList?.get(0)).isEqualTo(tags[1].id)
        }
        assertWithMessage("queryTagIds: %s %s", tags[0].id, tags[1].id).apply {
            idList = tagDao.queryTagIds(arrayOf(tags[0].id, tags[1].id))
            that(idList?.size).isEqualTo(2)
            that(idList?.get(0)).isEqualTo(tags[0].id)
            that(idList?.get(1)).isEqualTo(tags[1].id)
            that(tagDao.querySelectedTagCount()).isEqualTo(1)
        }
    }

    @Test fun testBitChanges() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestBitChanges()
        }
    }

    @Test fun testBitChangesWithUndo() {
        runBlocking {
            undo.record("TestBitChanges") { doTestBitChanges() }
        }
    }

    private suspend fun doTestBitChanges() {
        // Add a couple of tags
        val tags = listOf(
            TagEntity(0L, "tag1", "desc1", 0b01101),
            TagEntity(0L, "tag2", "desc2", 0b10001),
            TagEntity(0L, "tag3", "desc3", 0b11100),
        )

        // Check Add
        val tagDao = db.getTagDao()
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                tagDao.addWithUndo(t)
                that(t.id).isNotEqualTo(0L)
                that(tagDao.get(t.id)).isEqualTo(t)
                that(tagDao.findByName(t.name)).isEqualTo(t)
            }
        }

        suspend fun checkCount(bits: Int, value: Int, id: Long?, expTrue: Int, expFalse: Int) {
            assertWithMessage("checkCount value: %s id: %s", value, id).apply {
                that(tagDao.countBits(bits, value, true, id)).isEqualTo(expTrue)
                that(tagDao.countBits(bits, value, false, id)).isEqualTo(expFalse)
                that(tagDao.countBitsLive(bits, value, true, id).getLive()).isEqualTo(expTrue)
                that(tagDao.countBitsLive(bits, value, false, id).getLive()).isEqualTo(expFalse)
            }
        }
        checkCount(0b101, 0b001, null, 1, 2)
        checkCount(0b101, 0b101, null, 1, 2)
        checkCount(0b101, 0b101, tags[0].id, 1, 0)
        checkCount(0b101, 0b001, tags[0].id, 0, 1)

        var count: Int?
        assertWithMessage(
            "changeBits single op: %s, mask: %s", false, 0b101
        ).apply {
            count = tagDao.changeBits(false, 0b101, tags[0].id)
            that(count).isEqualTo(1)
            that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b01000)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", true, 0b10001
        ).apply {
            count = tagDao.changeBits(true, 0b10001, tags[0].id)
            that(count).isEqualTo(1)
            that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b11001)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", null, 0b10100
        ).apply {
            count = tagDao.changeBits(null, 0b10100, tags[0].id)
            that(count).isEqualTo(1)
            that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b01101)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", true, 0b01101
        ).apply {
            count = tagDao.changeBits(true, 0b01101, tags[0].id)
            that(count).isEqualTo(0)
            that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b01101)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", false, 0b10000
        ).apply {
            count = tagDao.changeBits(false, 0b10000, tags[0].id)
            that(count).isEqualTo(0)
            that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b01101)
        }

        suspend fun StandardSubjectBuilder.checkChange(vararg values: Int) {
            for (i in tags.indices) {
                that(tagDao.get(tags[i].id)?.flags).isEqualTo(values[i])
            }
        }
        assertWithMessage(
            "changeBits all op: %s, mask: %s", false, 0b00100
        ).apply {
            count = tagDao.changeBits(false, 0b00100, null)
            that(count).isEqualTo(2)
            checkChange(0b01001, 0b10001, 0b11000)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", true, 0b10001
        ).apply {
            count = tagDao.changeBits(true, 0b10001, null)
            that(count).isEqualTo(2)
            checkChange(0b11001, 0b10001, 0b11001)
        }
        assertWithMessage(
            "changeBits single op: %s, mask: %s", null, 0b10100
        ).apply {
            count = tagDao.changeBits(null, 0b10100, null)
            that(count).isEqualTo(tags.size)
            checkChange(0b01101, 0b00101, 0b01101)
        }
    }
}
