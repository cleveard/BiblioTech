package com.example.cleve.bibliotech.db

import com.example.cleve.bibliotech.R
import java.lang.Exception
import java.lang.StringBuilder
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

/**
 * Predicate Data description
 */
open class PredicateDataDescription(
    /** Resource Id of the predicate name */
    val nameResourceId: Int,
    /** The SQLite operator for the predicate */
    private val operator: String,
    /** True if the predicate needs an escape clause */
    escape: Boolean)
{
    /**
     * Convert the escape character to the escape clause for SQLite
     */
    private val escapeString = if (escape) " ESCAPE \"%\"" else ""

    /**
     * Convert string values to ints and add to the argument list
     * @param buildQuery The query builder for the query we are creating
     * @param v The values we need to convert
     */
    open fun convertInt(buildQuery: BuildQuery, vararg v: String): Boolean {
        return convert(buildQuery, *v) { it.toInt() }
    }

    /**
     * Convert string values to longs and add to the argument list
     * @param buildQuery The query builder for the query we are creating
     * @param v The values we need to convert
     */
    open fun convertLong(buildQuery: BuildQuery, vararg v: String): Boolean {
        return convert(buildQuery, *v) { it.toLong() }
    }

    /**
     * Convert string values to doubles and add to the argument list
     * @param buildQuery The query builder for the query we are creating
     * @param v The values we need to convert
     */
    open fun convertDouble(buildQuery: BuildQuery, vararg v: String): Boolean {
        return convert(buildQuery, *v) { it.toDouble() }
    }

    /**
     * Convert string values to strings and add to the argument list
     * @param buildQuery The query builder for the query we are creating
     * @param v The values we need to convert
     */
    open fun convertString(buildQuery: BuildQuery, vararg v: String): Boolean {
        return convert(buildQuery, *v) { escapeLikeWildCards(it) }
    }

    /**
     * Build a query for a date
     * @param buildQuery The query we are building
     * @param value The value for the query
     * @param min Set to true to use the minimum time value
     * @param max Set to true to use the maximum time value
     * Dates in the database are kept in longs. We convert the date string
     * to a date and then convert that to a long. We mess with the date value
     * depending on the predicate:
     *    GLOB predicate is not allowed
     *    GT bumps the long value up by the time unit we parse
     *    ONE_OF does a range check within the time unit we parse
     * The parse time unit is the smallest date unit that is parsed from
     * from the string. So if the string is only year, then the time unit
     * is one year. If the string is only month and year, the time unit
     * is month and if the string is day, month and year, the time unit is day.
     * Since months are variable length we need to account for that too
     */
    open fun convertDate(
        buildQuery: BuildQuery,
        value: String,
        min: Boolean = true,
        max: Boolean = false
    ): Boolean {
        try {
            var time = 0L
            var limit = 0L

            fun parse(): Boolean {
                // Start parsing at the start of the value string
                val pos = ParsePosition(0)
                val millisPerDay = 60L * 60L * 1000L * 24L

                val locale = buildQuery.context.resources.configuration.locales[0]
                // Get the locale dependent date format
                var format = DateFormat.getDateInstance(DateFormat.SHORT, locale)
                // Parse the date. If the parse fails try year month
                if (format.parse(value, pos) != null && pos.index >= value.length) {
                    // Got the date, set the limit to one day
                    time = format.calendar.timeInMillis
                    limit = millisPerDay
                    return true
                }

                // Try month and year. Get the date formatter and try to parse the string
                format = SimpleDateFormat(buildQuery.context.resources.getString(R.string.month_year_pattern), locale)
                pos.index = 0
                if (format.parse(value, pos) != null && pos.index >= value.length) {
                    // Got the date, set the limit to one month
                    val calendar = format.calendar
                    time = calendar.timeInMillis
                    limit = millisPerDay * (calendar.getActualMaximum(Calendar.DAY_OF_MONTH) + 1 -
                        calendar.getActualMinimum(Calendar.DAY_OF_MONTH))
                    return true
                }

                format.applyPattern("y")
                pos.index = 0
                if (format.parse(value, pos) != null && pos.index >= value.length) {
                    // Got the date, set the limit to one month
                    val calendar = format.calendar
                    time = calendar.timeInMillis
                    limit = millisPerDay * (calendar.getActualMaximum(Calendar.DAY_OF_YEAR) + 1 -
                        calendar.getActualMinimum(Calendar.DAY_OF_YEAR))
                    return true
                }

                return false
            }

            if (!parse())
                return false

            if (min)
                buildQuery.argList.add(time)
            if (max)
                buildQuery.argList.add(time + limit - 1)
        } catch (e: Exception) {
            return false
        }

        return true
    }

    /**
     * Build an expression for a predicate
     * @param buildQuery The query builder for the query we are creating
     * @param c The columns in the expression
     */
    open fun buildExpression(buildQuery: BuildQuery, c: Array<String>, logical: String = "OR") {
        // Loop through the array of column names and connect sub expressions using AND
        val expr = StringBuilder()
        for (name in c) {
            // Get the subexpression
            val sub = buildSubExpression(name)
            // Add it to the expression
            expr.append("${if (expr.isEmpty()) "" else " $logical "}( $sub )")
        }
        // Add the expression to the query
        buildQuery.addFilterExpression(expr.toString())
    }

    /**
     * Build a date expression for a predicate
     * @param buildQuery The query builder for the query we are creating
     * @param c The columns in the expression
     */
    open fun buildExpressionDate(buildQuery: BuildQuery, c: Array<String>, logical: String = "OR") {
        // Loop through the array of column names and connect sub expressions using AND
        val expr = StringBuilder()
        for (name in c) {
            // Get the subexpression
            val sub = buildSubExpressionDate(name)
            // Add it to the expression
            expr.append("${if (expr.isEmpty()) "" else " $logical "}( $sub )")
        }
        // Add the expression to the query
        buildQuery.addFilterExpression(expr.toString())
    }

    /**
     * Build the subexpression for a single column
     * @param name The name of the column
     * @return The sub expression with ? for the value argument
     */
    open fun buildSubExpression(name: String): String {
        return "$name $operator ?$escapeString"
    }

    /**
     * Build the subexpression for a single column
     * @param name The name of the column
     * @return The sub expression with ? for the value argument
     */
    open fun buildSubExpressionDate(name: String): String {
        return "$name $operator ?$escapeString"
    }

    companion object {
        /**
         * Pattern for escaping % and _ in like operator
         */
        private val escapeRegex = Regex("([%_])")
        private const val escapeReplace = "%$1"

        /**
         * Escape wilde cards in a string
         * @param input The string to escape
         * @return The escaped string
         */
        fun escapeLikeWildCards(input: String): String {
            return input.replace(escapeRegex, escapeReplace)
        }

        /**
         * Convert some values and add them to the buildQuery argument list
         * @param buildQuery The query builder for the query we are creating
         * @param values The values we need to convert
         * @param cvt A lambda to convert the string
         * @return True if the conversion of all values succeeded
         */
        fun convert(buildQuery: BuildQuery, vararg values: String, cvt: (String) -> Any): Boolean {
            // Get the list and keep track of how bit it was when we started
            val argList = buildQuery.argList
            val rollBack = argList.size
            // Conversion may throw an exception
            try {
                // Add the converted values to the argument list
                for (v in values)
                    argList.add(cvt(v))
            } catch (e: Exception) {
                // Got an exception, remove any arguments we added
                while (argList.size > rollBack)
                    argList.removeLast()
                return false
            }
            return true
        }
    }
}

/**
 * Predicate for column is one of a set of values
 */
private val oneOf = object: PredicateDataDescription(R.string.one_of, "LIKE", true) {
    /** @inheritDoc */
    override fun convertDate(
        buildQuery: BuildQuery,
        value: String,
        min: Boolean,
        max: Boolean
    ): Boolean {
        // oneOf uses both the min and max date values
        return super.convertDate(buildQuery, value, min = true, max = true)
    }

    /** @inheritDoc */
    override fun buildSubExpressionDate(name: String): String {
        return "( $name >= ? ) AND ( $name <= ? )"
    }
}

/**
 * Predicate for column contains one of a set of values
 */
private val glob = object: PredicateDataDescription(R.string.has, "LIKE", true) {
    override fun convertInt(buildQuery: BuildQuery, vararg v: String): Boolean {
        // Always treat value as string with wildcards
        return convert(buildQuery, *v) { "%${escapeLikeWildCards(it)}%" }
    }

    override fun convertLong(buildQuery: BuildQuery, vararg v: String): Boolean {
        // Always treat value as string with wildcards
        return convert(buildQuery, *v) { "%${escapeLikeWildCards(it)}%" }
    }

    override fun convertDouble(buildQuery: BuildQuery, vararg v: String): Boolean {
        // Always treat value as string with wildcards
        return convert(buildQuery, *v) { "%${escapeLikeWildCards(it)}%" }
    }

    override fun convertString(buildQuery: BuildQuery, vararg v: String): Boolean {
        // Always treat value as string with wildcards
        return convert(buildQuery, *v) { "%${escapeLikeWildCards(it)}%" }
    }

    override fun convertDate(
        buildQuery: BuildQuery,
        value: String,
        min: Boolean,
        max: Boolean
    ): Boolean {
        return false        // GLOB doesn't support dates
    }

    override fun buildExpression(buildQuery: BuildQuery, c: Array<String>, logical: String) {
        val expr = StringBuilder()
        for (name in c) {
            expr.append("${if (expr.isEmpty()) "" else " $logical "}( $name LIKE ? ESCAPE \"%\" )")
        }
        buildQuery.addFilterExpression(expr.toString())
    }
}

/**
 * Predicate for column is greater than one of a set of values
 */
private val gt = object: PredicateDataDescription(R.string.gt, ">", false) {
    /** @inheritDoc */
    override fun convertDate(
        buildQuery: BuildQuery,
        value: String,
        min: Boolean,
        max: Boolean
    ): Boolean {
        // gt uses both the max date values
        return super.convertDate(buildQuery, value, min = false, max = true)
    }
}

/**
 * Predicate for column is greater than or equal to one of a set of values
 */
private val ge = object: PredicateDataDescription(R.string.ge, ">=", false) {
}

/**
 * Predicate for column is less than one of a set of values
 */
private val lt = object: PredicateDataDescription(R.string.lt, "<", false) {
}

/**
 * Predicate for column is less than or equal to one of a set of values
 */
private val le = object: PredicateDataDescription(R.string.le, "<=", false) {
    /** @inheritDoc */
    override fun convertDate(
        buildQuery: BuildQuery,
        value: String,
        min: Boolean,
        max: Boolean
    ): Boolean {
        // le uses both the max date values
        return super.convertDate(buildQuery, value, min = false, max = true)
    }
}

/**
 * The predicate available
 */
@Suppress("unused")
enum class Predicate(val desc: PredicateDataDescription) {
    /** Match one of several values */
    ONE_OF(oneOf),
    /** Partial match on of several values */
    GLOB(glob),
    /** Greater than */
    GT(gt),
    /** Greater than or equal */
    GE(ge),
    /** Less than */
    LT(lt),
    /** Less than or equal */
    LE(le)
}
