package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
                TagEntity(0L, "tag2", "desc2", 0),
                TagEntity(0L, "tag3", "desc3", 0),
            )

            // Check Add
            val tagDao = db.getTagDao()
            for (t in tags) {
                tagDao.add(t)
                assertThat(t.id).isNotEqualTo(0L)
                assertThat(tagDao.get(t.id)).isEqualTo(t)
                assertThat(tagDao.findByName(t.name)).isEqualTo(t)
            }

            // Verify that we can update
            val update = tags[0].copy(id = 0L)
            update.isSelected = true
            tagDao.add(update) { true }
            assertThat(update.id).isEqualTo(tags[0].id)
            assertThat(tagDao.get(update.id)).isEqualTo(update)
            assertThat(tagDao.findByName(update.name)).isEqualTo(update)

            // Change the name
            update.name = "tag4"
            tagDao.add(update)   // Don't expect a conflict
            assertThat(update.id).isEqualTo(tags[0].id)
            assertThat(tagDao.get(update.id)).isEqualTo(update)
            assertThat(tagDao.findByName(update.name)).isEqualTo(update)
            assertThat(tagDao.findByName(tags[0].name)).isNull()

            // Merge two tags
            update.name = tags[1].name
            tagDao.add(update) { true }    // Don't expect a conflict
            assertThat(update.id).isAnyOf(tags[0].id, tags[1].id)
            assertThat(tagDao.get(update.id)).isEqualTo(update)
            assertThat(tagDao.findByName(update.name)).isEqualTo(update)
            assertThat(tagDao.findByName("tag4")).isNull()
            if (update.id == tags[0].id)
                assertThat(tagDao.get(tags[1].id)).isNull()
            else
                assertThat(tagDao.get(tags[0].id)).isNull()

            // Make sure we can delete using id
            tagDao.delete(update.id)
            assertThat(tagDao.get(update.id)).isNull()

            // Delete selected
            tags[0].isSelected = true
            tags[0].id = 0L
            tags[1].isSelected = true
            tags[1].id = 0L
            tagDao.add(tags[0])
            assertThat(tagDao.get(tags[0].id)).isEqualTo(tags[0])
            tagDao.add(tags[1])
            assertThat(tagDao.get(tags[1].id)).isEqualTo(tags[1])
            tagDao.deleteSelected()
            assertThat(tagDao.get(tags[0].id)).isNull()
            assertThat(tagDao.get(tags[1].id)).isNull()
        }
    }

    private suspend fun <T> getLive(live: LiveData<T>): T? {
        return withContext(MainScope().coroutineContext) {
            var observer: Observer<T>? = null
            try {
                suspendCoroutine {
                    observer = Observer<T> { value ->
                        if (value != null)
                            it.resume((value))
                    }.also { obs ->
                        live.observeForever(obs)
                    }
                }
            } finally {
                observer?.let { live.removeObserver(it) }
            }
        }
    }

    @Test(timeout = 1000L) fun testQueries() {
        runBlocking {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0),
                TagEntity(0L, "tag2", "desc2", TagEntity.SELECTED),
                TagEntity(0L, "tag3", "desc3", 0),
            )

            // Check Add
            val tagDao = db.getTagDao()
            for (t in tags) {
                tagDao.add(t)
                assertThat(t.id).isNotEqualTo(0L)
                assertThat(tagDao.get(t.id)).isEqualTo(t)
                assertThat(tagDao.findByName(t.name)).isEqualTo(t)
            }

            // Check the paging source get
            val page = tagDao.get()
            val result = page.load(PagingSource.LoadParams.Refresh(key = 0, loadSize = tags.size, placeholdersEnabled = false))
            assertThat(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, TagEntity>
            assertThat(result.data.size).isEqualTo(tags.size)
            for (i in tags.indices)
                assertThat(result.data[i]).isEqualTo(tags[i])

            // Check the live list
            var tagList = getLive(tagDao.getLive())
            assertThat(tagList?.size).isEqualTo(tags.size)
            for (i in tags.indices)
                assertThat(tagList?.get(i)).isEqualTo(tags[i])

            // Check the live list, selected
            tagList = getLive(tagDao.getLive(true))
            assertThat(tagList?.size).isEqualTo(1)
            assertThat(tagList?.get(0)).isEqualTo(tags[1])

            var idList = tagDao.queryTagIds(null)
            assertThat(idList?.size).isEqualTo(1)
            assertThat(idList?.get(0)).isEqualTo(tags[1].id)

            idList = tagDao.queryTagIds(arrayOf(tags[0].id, tags[1].id))
            assertThat(idList?.size).isEqualTo(2)
            assertThat(idList?.get(0)).isEqualTo(tags[0].id)
            assertThat(idList?.get(1)).isEqualTo(tags[1].id)

            assertThat(tagDao.querySelectedTagCount()).isEqualTo(1)
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
                tagDao.add(t)
                assertThat(t.id).isNotEqualTo(0L)
                assertThat(tagDao.get(t.id)).isEqualTo(t)
                assertThat(tagDao.findByName(t.name)).isEqualTo(t)
            }

            suspend fun checkCount(bits: Int, value: Int, id: Long?, expTrue: Int, expFalse: Int) {
                assertThat(tagDao.countBits(bits, value, true, id)).isEqualTo(expTrue)
                assertThat(tagDao.countBits(bits, value, false, id)).isEqualTo(expFalse)
                assertThat(getLive(tagDao.countBitsLive(bits, value, true, id))).isEqualTo(expTrue)
                assertThat(getLive(tagDao.countBitsLive(bits, value, false, id))).isEqualTo(expFalse)
            }
            checkCount(0b11, 0b01, null, 1, 2)
            checkCount(0b11, 0b11, null, 1, 2)
            checkCount(0b11, 0b11, tags[0].id, 1, 0)
            checkCount(0b11, 0b01, tags[0].id, 0, 1)

            // TODO: Get count working - resolve writable queries
            var count = tagDao.changeBits(false, 0b11, tags[0].id)
            assertThat(count).isEqualTo(1)
            assertThat(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0100)
            count = tagDao.changeBits(true, 0b1001, tags[0].id)
            assertThat(count).isEqualTo(1)
            assertThat(tagDao.get(tags[0].id)?.flags).isEqualTo(0b1101)
            count = tagDao.changeBits(null, 0b1010, tags[0].id)
            assertThat(count).isEqualTo(1)
            assertThat(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)
            count = tagDao.changeBits(true, 0b0111, tags[0].id)
            assertThat(count).isEqualTo(0)
            assertThat(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)
            count = tagDao.changeBits(false, 0b1000, tags[0].id)
            assertThat(count).isEqualTo(0)
            assertThat(tagDao.get(tags[0].id)?.flags).isEqualTo(0b0111)

            suspend fun checkChange(vararg values: Int) {
                for (i in tags.indices) {
                    assertThat(tagDao.get(tags[i].id)?.flags).isEqualTo(values[i])
                }
            }
            count = tagDao.changeBits(false, 0b0010, null)
            assertThat(count).isEqualTo(2)
            checkChange(0b0101, 0b1001, 0b1100)
            count = tagDao.changeBits(true, 0b1001, null)
            assertThat(count).isEqualTo(2)
            checkChange(0b1101, 0b1001, 0b1101)
            count = tagDao.changeBits(null, 0b1010, null)
            assertThat(count).isEqualTo(tags.size)
            checkChange(0b0111, 0b0011, 0b0111)
        }
    }
}
