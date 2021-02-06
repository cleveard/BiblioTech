package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagDaoTest {
    private lateinit var db: BookDatabase

    @Before fun startUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
    }

    @After fun tearDown() {
        BookDatabase.close()
    }

    @Test(timeout = 1000L) fun testAddUpdateDelete()
    {
        runBlocking {
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
                    tagDao.add(t)
                    that(t.id).isNotEqualTo(0L)
                    that(tagDao.get(t.id)).isEqualTo(t)
                    that(tagDao.findByName(t.name)).isEqualTo(t)
                }
            }

            // Verify that we can update
            val update = tags[2].copy(id = 0L)
            update.isSelected = true
            assertWithMessage("Update Succeeded %s", update.name).apply {
                tagDao.add(update) { true }
                that(update.id).isEqualTo(tags[2].id)
                that(tagDao.get(update.id)).isEqualTo(update)
                that(tagDao.findByName(update.name)).isEqualTo(update)
            }

            // Change the name
            update.name = "tag%"
            assertWithMessage("Name Change %s", update.name).apply {
                tagDao.add(update)   // Don't expect a conflict
                that(update.id).isEqualTo(tags[2].id)
                that(tagDao.get(update.id)).isEqualTo(update)
                that(tagDao.findByName(update.name)).isEqualTo(update)
                that(tagDao.findByName(tags[2].name)).isNull()
            }

            // Merge two tags
            update.name = tags[1].name
            assertWithMessage("Merge Tags %s", update.name).apply {
                tagDao.add(update) { true }    // Don't expect a conflict
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
                tagDao.add(tags[2])
                that(tagDao.get(tags[2].id)).isEqualTo(tags[2])
                tagDao.add(tags[1])
                that(tagDao.get(tags[1].id)).isEqualTo(tags[1])
                tagDao.deleteSelected()
                that(tagDao.get(tags[2].id)).isNull()
                that(tagDao.get(tags[1].id)).isNull()
            }
        }
    }

    @Test(timeout = 1000L) fun testQueries() {
        runBlocking {
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
                    tagDao.add(t)
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
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(tags.size)
                for (i in tags.indices)
                    that(tagList?.get(i)).isEqualTo(tags[i])
            }

            // Check the live list, selected
            assertWithMessage("getLive selected").apply {
                tagList = getLive(tagDao.getLive(true))
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
    }

    @Test(timeout = 1000L) fun testBitChanges() {
        runBlocking {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0b0111),
                TagEntity(0L, "tag2", "desc2", 0b1001),
                TagEntity(0L, "tag3", "desc3", 0b1110),
            )

            // Check Add
            val tagDao = db.getTagDao()
            for (t in tags) {
                assertWithMessage("Add %s", t.name).apply {
                    tagDao.add(t)
                    that(t.id).isNotEqualTo(0L)
                    that(tagDao.get(t.id)).isEqualTo(t)
                    that(tagDao.findByName(t.name)).isEqualTo(t)
                }
            }

            suspend fun checkCount(bits: Int, value: Int, id: Long?, expTrue: Int, expFalse: Int) {
                assertWithMessage("checkCount value: %s id: %s", value, id).apply {
                    that(tagDao.countBits(bits, value, true, id)).isEqualTo(expTrue)
                    that(tagDao.countBits(bits, value, false, id)).isEqualTo(expFalse)
                    that(getLive(tagDao.countBitsLive(bits, value, true, id))).isEqualTo(expTrue)
                    that(getLive(tagDao.countBitsLive(bits, value, false, id))).isEqualTo(expFalse)
                }
            }
            checkCount(0b11, 0b01, null, 1, 2)
            checkCount(0b11, 0b11, null, 1, 2)
            checkCount(0b11, 0b11, tags[0].id, 1, 0)
            checkCount(0b11, 0b01, tags[0].id, 0, 1)

            var count: Int?
            assertWithMessage(
                "changeBits single op: %s, mask: %s", false, 0b11
            ).apply {
                count = tagDao.changeBits(false, 0b11, tags[0].id)
                that(count).isEqualTo(1)
                that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0100)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b1001
            ).apply {
                count = tagDao.changeBits(true, 0b1001, tags[0].id)
                that(count).isEqualTo(1)
                that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b1101)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", null, 0b1010
            ).apply {
                count = tagDao.changeBits(null, 0b1010, tags[0].id)
                that(count).isEqualTo(1)
                that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b0111
            ).apply {
                count = tagDao.changeBits(true, 0b0111, tags[0].id)
                that(count).isEqualTo(0)
                that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", false, 0b1000
            ).apply {
                count = tagDao.changeBits(false, 0b1000, tags[0].id)
                that(count).isEqualTo(0)
                that(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)
            }

            suspend fun StandardSubjectBuilder.checkChange(vararg values: Int) {
                for (i in tags.indices) {
                    that(tagDao.get(tags[i].id)?.flags).isEqualTo(values[i])
                }
            }
            assertWithMessage(
                "changeBits all op: %s, mask: %s", false, 0b0010
            ).apply {
                count = tagDao.changeBits(false, 0b0010, null)
                that(count).isEqualTo(2)
                checkChange(0b0101, 0b1001, 0b1100)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b1001
            ).apply {
                count = tagDao.changeBits(true, 0b1001, null)
                that(count).isEqualTo(2)
                checkChange(0b1101, 0b1001, 0b1101)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", null, 0b1010
            ).apply {
                count = tagDao.changeBits(null, 0b1010, null)
                that(count).isEqualTo(tags.size)
                checkChange(0b0111, 0b0011, 0b0111)
            }
        }
    }
}
