package com.margin.app.ai

import com.margin.app.domain.model.Subject

/**
 * Finds the subject someone means from the words they used: "DS", "data structures",
 * "maths", "digital electronics", "DELD". Deterministic and offline; the model is never
 * trusted to name a subject code on its own.
 */
object SubjectMatcher {

    fun match(text: String?, subjects: List<Subject>): Subject? {
        val query = normalise(text ?: return null)
        if (query.isEmpty()) return null

        subjects.firstOrNull { normalise(it.code) == query }?.let { return it }
        subjects.firstOrNull { normalise(it.shortName) == query }?.let { return it }
        subjects.firstOrNull { normalise(it.name) == query }?.let { return it }
        subjects.firstOrNull { initials(it.name) == query }?.let { return it }
        // "ds" for Data Structures and Algorithms: initials of the words that are not filler.
        subjects.firstOrNull { initials(it.name).startsWith(query) && query.length >= 2 }?.let { return it }

        val aliases = mapOf(
            "math" to "mathematics",
            "maths" to "mathematics",
            "econ" to "economics",
            "eco" to "economics",
            "economy" to "economics",
        )
        val expanded = aliases[query] ?: query
        // A short name typed in part: "eco" for "Econ", "struct" is too loose and is not tried.
        if (query.length >= 3) {
            subjects.firstOrNull { normalise(it.shortName).startsWith(query) }?.let { return it }
        }
        subjects.firstOrNull { normalise(it.name).contains(expanded) }?.let { return it }

        val words = expanded.split(' ').filter { it.length >= 3 }
        if (words.isEmpty()) return null
        return subjects
            .map { subject -> subject to words.count { normalise(subject.name).contains(it) } }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy({ it.second }, { -it.first.code.length }))
            ?.first
    }

    /** Finds the first subject mentioned anywhere in a sentence. */
    fun find(sentence: String, subjects: List<Subject>): Subject? {
        val text = " " + normalise(sentence) + " "
        subjects.firstOrNull { text.contains(" " + normalise(it.code) + " ") }?.let { return it }
        subjects.firstOrNull { text.contains(" " + normalise(it.shortName) + " ") }?.let { return it }
        subjects.firstOrNull { text.contains(normalise(it.name)) }?.let { return it }
        val tokens = text.trim().split(' ').filter { it.isNotBlank() }
        for (size in 3 downTo 1) {
            for (index in 0..tokens.size - size) {
                val phrase = tokens.subList(index, index + size).joinToString(" ")
                if (phrase.length < 2 || phrase in STOP) continue
                match(phrase, subjects)?.let { return it }
            }
        }
        return null
    }

    private fun normalise(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun initials(name: String): String =
        normalise(name).split(' ').filter { it.isNotBlank() && it !in FILLER }.joinToString("") { it.take(1) }

    private val FILLER = setOf("and", "of", "for", "the", "in", "to")
    private val STOP = setOf(
        "i", "to", "do", "a", "an", "the", "for", "today", "study", "want", "dont", "don", "t",
        "not", "my", "me", "on", "at", "is", "it", "another", "more", "minutes", "min", "hour",
    )
}
