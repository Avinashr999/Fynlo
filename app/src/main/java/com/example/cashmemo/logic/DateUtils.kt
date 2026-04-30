package com.example.cashmemo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val standardFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun formatToDisplay(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, dbFormatter)
            date.format(standardFormatter)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun parseInput(input: String): String {
        // Basic parser for common Indian date formats
        val patterns = listOf("dd-MM-yyyy", "dd-M-yy", "d-M-yy", "dd/MM/yyyy", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val date = LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern))
                return date.format(dbFormatter)
            } catch (e: Exception) { continue }
        }
        return LocalDate.now().format(dbFormatter)
    }
}