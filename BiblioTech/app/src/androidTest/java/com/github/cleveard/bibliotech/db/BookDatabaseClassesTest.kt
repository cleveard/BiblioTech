package com.github.cleveard.bibliotech.db

import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.testutils.makeBook
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import kotlin.reflect.KMutableProperty0

@RunWith(AndroidJUnit4::class)
class BookDatabaseClassesTest {
    @Test(timeout = 5000L) fun testDateConverter() {
        // Make a converter
        val cvt = DateConverters()
        // Nulls should convert to nulls
        assertThat(cvt.dateToTimestamp(null)).isEqualTo(null)
        assertThat(cvt.fromTimestamp(null)).isEqualTo(null)
        val nowDate: Date = Calendar.getInstance().time
        val nowLong: Long = nowDate.time
        assertThat(nowLong).isEqualTo(cvt.dateToTimestamp(nowDate))
        assertThat(nowDate).isEqualTo(cvt.fromTimestamp(nowLong))
    }

    @Test(timeout = 5000L) fun testFilterConverter() {
        // Make a converter
        val cvt = FilterConverters()
        // Nulls should convert to nulls
        assertThat(cvt.filterFromString(null)).isEqualTo(null)
        assertThat(cvt.filterToString(null)).isEqualTo(null)
        val filter = BookFilter(arrayOf(
            OrderField(Column.ANY, Order.Ascending, true),
            OrderField(Column.BOOK_COUNT, Order.Descending, true),
            OrderField(Column.CATEGORIES, Order.Ascending, false),
            OrderField(Column.DATE_ADDED, Order.Ascending, true),
            OrderField(Column.DATE_MODIFIED, Order.Descending, true),
            OrderField(Column.DESCRIPTION, Order.Ascending, true),
            OrderField(Column.FIRST_NAME, Order.Descending, false),
            OrderField(Column.ISBN, Order.Descending, false),
            OrderField(Column.LAST_NAME, Order.Ascending, false),
            OrderField(Column.PAGE_COUNT, Order.Ascending, true),
            OrderField(Column.RATING, Order.Descending, true),
            OrderField(Column.SOURCE, Order.Ascending, false),
            OrderField(Column.SOURCE_ID, Order.Descending, true),
            OrderField(Column.SUBTITLE, Order.Descending, true),
            OrderField(Column.TAGS, Order.Ascending, false),
            OrderField(Column.TITLE, Order.Descending, true),
            OrderField(Column.SERIES, Order.Ascending, true),
            OrderField(Column.SELECTED, Order.Descending, true)
        ), arrayOf(
            FilterField(Column.ANY, Predicate.GE, arrayOf("xx", "yy")),
            FilterField(Column.BOOK_COUNT, Predicate.GLOB, arrayOf("zz")),
            FilterField(Column.CATEGORIES, Predicate.GT, arrayOf("z", "y", "z")),
            FilterField(Column.DATE_ADDED, Predicate.LT, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.DATE_MODIFIED, Predicate.LE, arrayOf()),
            FilterField(Column.DESCRIPTION, Predicate.ONE_OF, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.FIRST_NAME, Predicate.NOT_GLOB, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.ISBN, Predicate.NOT_ONE_OF, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.LAST_NAME, Predicate.NOT_GLOB, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.PAGE_COUNT, Predicate.NOT_ONE_OF, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.RATING, Predicate.LT, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.SOURCE, Predicate.LE, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.SOURCE_ID, Predicate.GLOB, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.SUBTITLE, Predicate.ONE_OF, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.TAGS, Predicate.GT, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.TITLE, Predicate.GE, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.SERIES, Predicate.ONE_OF, arrayOf("ww", "ll", "33", "44", "55", "765")),
            FilterField(Column.SELECTED, Predicate.NOT_ONE_OF, arrayOf()),
        ))
        val string = BookFilter.encodeToString(filter)
        assertThat(string).isEqualTo(cvt.filterToString(filter))
        assertThat(filter).isEqualTo(cvt.filterFromString(string))
        assertThat(string).isEqualTo(cvt.filterToString(cvt.filterFromString(string)))
    }

    /**
     * Change a property and check that it isn't equal
     * @param v1 Object to be changed
     * @param v2 Object that is equal to v1
     * @param p Property to change
     * @param v Value to set
     */
    private fun <T, R> changeAndCheck(v1: T, v2: T, p: KMutableProperty0<R>, v: R) {
        val save = p.get()
        p.set(v)
        assertThat(p.get()).isEqualTo(v)
        assertThat(v1 == v2).isFalse()
        assertThat(v1.hashCode()).isNotEqualTo(v2.hashCode())
        p.set(save)
        assertThat(p.get()).isEqualTo(save)
        assertThat(v1 == v2).isTrue()
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode())
    }

    @Test(timeout = 5000L) fun bookEntityTest() {
        val time = Calendar.getInstance().timeInMillis
        val book1 = makeBook(0, 0, "0", time)
        val book2 = makeBook(0, 0, "0", time)

        // Assert that the books compare
        assertThat(book1 == book2).isTrue()
        assertThat(book1.hashCode()).isEqualTo(book2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(book1, book2, book1::id, 1L)
        changeAndCheck(book1, book2, book1::volumeId, "volumeIdx")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::sourceId, "sourceIddd")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::title, "tite")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::subTitle, "subTxitle")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::description, "descrciption")
        changeAndCheck(book1, book2, book1::pageCount, 1443)
        changeAndCheck(book1, book2, book1::bookCount, 52)
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::linkUrl, "lcinkUrl")
        changeAndCheck(book1, book2, book1::rating, 3.141)
        changeAndCheck(book1, book2, book1::added, Date(155))
        changeAndCheck(book1, book2, book1::modified, Date(223))
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::smallThumb, "smallTchumb")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(book1, book2, book1::largeThumb, "largeThhumb")
        changeAndCheck(book1, book2, book1::flags, 1)
        changeAndCheck(book1, book2, book1::isSelected, true)
        changeAndCheck(book1, book2, book1::isExpanded, true)
    }

    @Test(timeout = 5000L) fun authorEntityTest() {
        val author1 = AuthorEntity(
            id = 0L,
            lastName = "volumeId",
            remainingName = "sourceId",
        )
        val author2 = AuthorEntity(
            id = 0L,
            in_name = "sourceId volumeId"
        )
        val author3 = AuthorEntity(
            id = 0L,
            in_name = "volumeId, sourceId"
        )
        val author4 = AuthorEntity(
            id = 0L,
            in_name = "volumeId"
        )

        // Assert that the books compare
        assertThat(author1 == author2).isTrue()
        assertThat(author1.hashCode()).isEqualTo(author2.hashCode())
        assertThat(author1 == author3).isTrue()
        assertThat(author1.hashCode()).isEqualTo(author3.hashCode())
        assertThat(author4.lastName).isEqualTo("volumeId")
        assertThat(author4.remainingName).isEmpty()
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(author1, author2, author1::id, 1L)
        changeAndCheck(author1, author2, author1::lastName, "volumeIdx")
        @Suppress("SpellCheckingInspection")
        changeAndCheck(author1, author2, author1::remainingName, "sourceIddd")
    }

    @Test(timeout = 5000L) fun bookAuthorEntityTest() {
        fun makeBookAuthor(): BookAndAuthorEntity {
            return BookAndAuthorEntity(
                id = 0L,
                bookId = 3,
                authorId = 4,
            )
        }

        val author1 = makeBookAuthor()
        val author2 = makeBookAuthor()

        // Assert that the books compare
        assertThat(author1 == author2).isTrue()
        assertThat(author1.hashCode()).isEqualTo(author2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(author1, author2, author1::id, 1L)
        changeAndCheck(author1, author2, author1::bookId, 6L)
        changeAndCheck(author1, author2, author1::authorId, 22L)
    }

    private fun makeCategory(): CategoryEntity {
        return CategoryEntity(
            id = 0L,
            category = "category",
        )
    }

    @Test(timeout = 5000L) fun categoryEntityTest() {
        val category1 = makeCategory()
        val category2 = makeCategory()

        // Assert that the books compare
        assertThat(category1 == category2).isTrue()
        assertThat(category1.hashCode()).isEqualTo(category2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(category1, category2, category1::id, 1L)
        changeAndCheck(category1, category2, category1::category, "somethingElse")
    }

    @Test(timeout = 5000L) fun bookCategoryEntityTest() {
        fun makeBookCategory(): BookAndCategoryEntity {
            return BookAndCategoryEntity(
                id = 0L,
                bookId = 3,
                categoryId = 4,
            )
        }

        val category1 = makeBookCategory()
        val category2 = makeBookCategory()

        // Assert that the books compare
        assertThat(category1 == category2).isTrue()
        assertThat(category1.hashCode()).isEqualTo(category2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(category1, category2, category1::id, 1L)
        changeAndCheck(category1, category2, category1::bookId, 6L)
        changeAndCheck(category1, category2, category1::categoryId, 22L)
    }

    private fun makeTag(): TagEntity {
        return TagEntity(
            id = 0L,
            name = "tag",
            desc = "description",
            flags = 0,
        )
    }

    @Test(timeout = 5000L) fun tagEntityTest() {
        val tag1 = makeTag()
        val tag2 = makeTag()

        // Assert that the books compare
        assertThat(tag1 == tag2).isTrue()
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(tag1, tag2, tag1::id, 1L)
        changeAndCheck(tag1, tag2, tag1::name, "somethingElse")
        changeAndCheck(tag1, tag2, tag1::desc, "A different description")
        changeAndCheck(tag1, tag2, tag1::flags, 44)
        changeAndCheck(tag1, tag2, tag1::isSelected, true)
    }

    @Test(timeout = 5000L) fun bookTagEntityTest() {
        fun makeBookTag(): BookAndTagEntity {
            return BookAndTagEntity(
                id = 0L,
                bookId = 3,
                tagId = 4,
            )
        }

        val tag1 = makeBookTag()
        val tag2 = makeBookTag()

        // Assert that the books compare
        assertThat(tag1 == tag2).isTrue()
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(tag1, tag2, tag1::id, 1L)
        changeAndCheck(tag1, tag2, tag1::bookId, 6L)
        changeAndCheck(tag1, tag2, tag1::tagId, 22L)
    }

    private fun makeIsbn(): IsbnEntity {
        return IsbnEntity(
            id = 0L,
            isbn = "isbn",
        )
    }

    @Test(timeout = 5000L) fun isbnEntityTest() {
        val isbn1 = makeIsbn()
        val isbn2 = makeIsbn()

        // Assert that the books compare
        assertThat(isbn1 == isbn2).isTrue()
        assertThat(isbn1.hashCode()).isEqualTo(isbn2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(isbn1, isbn2, isbn1::id, 1L)
        changeAndCheck(isbn1, isbn2, isbn1::isbn, "somethingElse")
    }

    @Test(timeout = 5000L) fun bookIsbnEntityTest() {
        fun makeBookIsbn(): BookAndIsbnEntity {
            return BookAndIsbnEntity(
                id = 0L,
                bookId = 3,
                isbnId = 4,
            )
        }

        val category1 = makeBookIsbn()
        val category2 = makeBookIsbn()

        // Assert that the books compare
        assertThat(category1 == category2).isTrue()
        assertThat(category1.hashCode()).isEqualTo(category2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(category1, category2, category1::id, 1L)
        changeAndCheck(category1, category2, category1::bookId, 6L)
        changeAndCheck(category1, category2, category1::isbnId, 22L)
    }

    @Test(timeout = 5000L) fun viewEntityTest() {
        fun makeView(): ViewEntity {
            return ViewEntity(
                id = 0L,
                name = "tag",
                desc = "description",
                filter = BookFilter(arrayOf(
                    OrderField(Column.TITLE, Order.Descending, true)
                ), arrayOf(
                    FilterField(Column.TITLE, Predicate.NOT_ONE_OF, arrayOf("x"))
                )),
            )
        }

        val view1 = makeView()
        val view2 = makeView()

        // Assert that the books compare
        assertThat(view1 == view2).isTrue()
        assertThat(view1.hashCode()).isEqualTo(view2.hashCode())
        // Assert that changing any field will cause a mis-compare
        changeAndCheck(view1, view2, view1::id, 1L)
        changeAndCheck(view1, view2, view1::name, "somethingElse")
        changeAndCheck(view1, view2, view1::desc, "A different description")
        changeAndCheck(view1, view2, view1::filter, BookFilter(emptyArray(), emptyArray()))
        changeAndCheck(view1, view2, view1::filter, null)
    }

   @Test(timeout = 5000L) fun bookAndAuthorsTest() {
       fun makeBookAndAuthors(bookTestNow: Long = Calendar.getInstance().timeInMillis): BookAndAuthors {
           return BookAndAuthors(
               book = makeBook(0, 0, "0", bookTestNow),
               authors = listOf(AuthorEntity(0L, "sourceId volumeId")),
               categories = listOf(makeCategory()),
               tags = listOf(makeTag()),
               isbns = listOf(makeIsbn()),
               series = null
           )
       }

       val time = Calendar.getInstance().timeInMillis
       val book1 = makeBookAndAuthors(time)
       val book2 = makeBookAndAuthors(time)

       // Assert that the books compare
       assertThat(book1 == book2).isTrue()
       assertThat(book1.hashCode()).isEqualTo(book2.hashCode())
       // Assert that changing any field will cause a mis-compare
       changeAndCheck(book1, book2, book1.book::id, 1L)
       changeAndCheck(book1, book2, book1.authors[0]::name, "somethingElse")
       changeAndCheck(book1, book2, book1.categories[0]::category, "A different description")
       changeAndCheck(book1, book2, book1.tags[0]::desc, "junk that is different")
       changeAndCheck(book1, book2, book1.isbns[0]::isbn, "junk that is different")
       // Assert the the sort order doesn't affect equals or the hash code
       book1.sortCategory = "ccc"
       book1.sortLast = "lll"
       book1.sortFirst = "fff"
       book1.sortTag = "ttt"
       book1.sortIsbn = "iii"
       assertThat(book1 == book2).isTrue()
       assertThat(book1.hashCode()).isEqualTo(book2.hashCode())

       val bundle = Bundle()
       bundle.putParcelable("book", book1)
       @Suppress("DEPRECATION")
       val book3 = if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU)
           bundle.getParcelable("book", BookAndAuthors::class.java)
       else
           bundle.getParcelable("book")
       assertThat(book3 == book1).isTrue()
       assertThat(book3.hashCode()).isEqualTo(book1.hashCode())
   }
}
