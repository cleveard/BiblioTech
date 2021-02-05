package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewDaoTest {
    private lateinit var db: BookDatabase

    @Before
    fun startUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
    }

    @After
    fun tearDown() {
        BookDatabase.close()
    }

    @Test/*(timeout = 1000L)*/ fun testViewDao() {
        runBlocking {
            val viewDao = db.getViewDao()
            // Make some views
            val views = arrayOf(
                ViewEntity(id = 0L, name = "view1", desc = "desc1"),
                ViewEntity(
                    id = 0L, name = "view\\", desc = "desc\\", filter = BookFilter(
                        arrayOf(OrderField(Column.LAST_NAME, Order.Ascending, false)),
                        emptyArray()
                    )
                ),
                ViewEntity(
                    id = 0L, name = "view_", desc = "desc_", filter = BookFilter(
                        arrayOf(OrderField(Column.DATE_MODIFIED, Order.Descending, false)),
                        arrayOf(FilterField(Column.TAGS, Predicate.NOT_ONE_OF, arrayOf("SciFi")))
                    )
                ),
            )

            // Keep the names in a set, because the query order may not
            // match the order of the views array
            val names = HashMap<String, ViewEntity>()
            // Add the views
            for (v in views) {
                // Save the name
                names[v.name] = v
                // Add the view
                val id = viewDao.addOrUpdate(v) { false }
                // Check id and findByName
                assertWithMessage("Add View %s", v.name).let { assert ->
                    assert.that(id).isNotEqualTo(0L)
                    assert.that(id).isEqualTo(v.id)
                    assert.that(viewDao.findByName(v.name)).isEqualTo(v)
                }
            }

            suspend fun StandardSubjectBuilder.checkNames() {
                val nameList = getLive(viewDao.getViewNames())
                that(nameList?.size).isEqualTo(names.size)
                val inList = HashSet<String>()
                for (n in nameList!!) {
                    that(names.contains(n)).isTrue()
                    that(inList.contains(n)).isFalse()
                    that(viewDao.findByName(n)).isEqualTo(names[n])
                    inList.add(n)
                }
            }

            // Get the names an makes sure they are all there
            assertWithMessage("All Views").checkNames()

            val newView = views[0].copy(id = 0L, desc = "descNew", filter = BookFilter(emptyArray(), emptyArray()))
            // Fail to add a conflicting view
            assertWithMessage("Conflict Fail").let {assert ->
                assert.that(viewDao.addOrUpdate(newView) { false }).isEqualTo(0L)
                assert.checkNames()
            }

            // Add a conflicting view
            assertWithMessage("Conflict Succeed").let { assert ->
                val id = viewDao.addOrUpdate(newView) { true }
                assert.that(id).isEqualTo(views[0].id)
                assert.that(id).isEqualTo(newView.id)
                assert.that(viewDao.findByName(newView.name)).isEqualTo(newView)
                names[newView.name] = newView
                assert.checkNames()
            }

            // Delete the views one at a time and check the list
            for (i in views.size - 1 downTo 0) {
                assertWithMessage("Delete View %s", views[i].name).let { assert ->
                    assert.that(viewDao.delete(views[i].name)).isEqualTo(1)
                    assert.that(viewDao.findByName(views[i].name)).isEqualTo(null)
                    names.remove(views[i].name)
                    assert.checkNames()
                }
            }
        }
    }
}
