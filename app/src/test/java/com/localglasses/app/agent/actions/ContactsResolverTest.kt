package com.localglasses.app.agent.actions

import com.localglasses.app.agent.trace.ContactAlias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsResolverTest {

    private val contacts = listOf(
        ContactCandidate("1", "Amma", "+911111111111"),
        ContactCandidate("2", "Amma Rao", "+912222222222"),
        ContactCandidate("3", "Appa", "+913333333333"),
    )

    @Test
    fun aliasWinsOverContactSearch() {
        val resolver = ContactsResolver(
            aliasLookup = { if (it == "Amma") ContactAlias("Amma", "9", "Amma", "+919999999999") else null },
            source = { contacts },
        )

        val result = resolver.resolve("Amma")

        assertTrue(result is ContactResolution.Unique)
        assertEquals("9", (result as ContactResolution.Unique).contact.id)
    }

    @Test
    fun exactNameResolvesUnique() {
        val resolver = ContactsResolver({ null }, { query -> contacts.filter { it.name.contains(query) } })

        val result = resolver.resolve("Appa")

        assertTrue(result is ContactResolution.Unique)
    }

    @Test
    fun multipleMatchesAreReturned() {
        val resolver = ContactsResolver({ null }, { query ->
            contacts.filter { it.name.contains(query, ignoreCase = true) }
        })

        val result = resolver.resolve("Am")

        assertTrue(result is ContactResolution.Multiple)
        assertEquals(2, (result as ContactResolution.Multiple).candidates.size)
    }

    @Test
    fun noMatch() {
        val resolver = ContactsResolver({ null }, { emptyList() })

        assertEquals(ContactResolution.None, resolver.resolve("Nobody"))
    }
}
