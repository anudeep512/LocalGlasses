package com.localglasses.app.agent.trace

/**
 * Contact aliases live in the encrypted trace database rather than a plain preference
 * file, so nicknames like "Amma" are not readable outside the app sandbox.
 */
class AliasStore(private val database: TraceDatabase) {

    fun put(alias: ContactAlias) = database.upsertAlias(alias)

    fun get(name: String): ContactAlias? = database.alias(name)

    fun list(): List<ContactAlias> = database.aliases()

    fun delete(name: String) = database.deleteAlias(name)
}
