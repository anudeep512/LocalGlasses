package com.localglasses.app.agent.actions

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.text.Normalizer

data class ContactCandidate(
    val id: String,
    val name: String,
    val phone: String,
)

sealed interface ContactResolution {
    data class Unique(val contact: ContactCandidate) : ContactResolution
    data class Multiple(val candidates: List<ContactCandidate>) : ContactResolution
    data object None : ContactResolution
}

fun interface ContactSource {
    fun search(query: String): List<ContactCandidate>
}

class AndroidContactSource(private val context: Context) : ContactSource {
    override fun search(query: String): List<ContactCandidate> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER)
        return context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ContactCandidate(
                            id = cursor.getString(0) ?: continue,
                            name = cursor.getString(1) ?: continue,
                            phone = cursor.getString(2) ?: continue,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }
}

class ContactsResolver(private val source: ContactSource) {
    fun resolve(query: String): ContactResolution {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ContactResolution.None
        if (PhoneNumbers.isPhoneNumber(trimmed)) {
            return ContactResolution.Unique(
                ContactCandidate(
                    id = trimmed,
                    name = trimmed,
                    phone = PhoneNumbers.normalize(trimmed),
                ),
            )
        }

        val candidates = runCatching { source.search(trimmed) }.getOrDefault(emptyList())
        val target = normalize(trimmed)
        val exact = candidates.filter { normalize(it.name) == target }
        val pool = if (exact.isNotEmpty()) exact else candidates
        val unique = pool
            .distinctBy { it.id to it.phone }
            .sortedBy { it.name.lowercase() }

        return when {
            unique.isEmpty() -> ContactResolution.None
            unique.size == 1 -> ContactResolution.Unique(unique.first())
            else -> ContactResolution.Multiple(unique)
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
