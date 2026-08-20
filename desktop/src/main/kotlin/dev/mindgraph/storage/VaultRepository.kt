package dev.mindgraph.storage

import dev.mindgraph.model.Vault
import dev.mindgraph.model.currentUnixTimestamp

class VaultRepository(private val database: Database) {
    suspend fun listVaults(): List<Vault> = database.readData().vaults.sortedBy { it.id }

    suspend fun createVault(name: String, rootPath: String): Vault =
        database.withData { data ->
            val vault = Vault(
                id = data.allocateVaultId(),
                name = name,
                rootPath = rootPath,
                createdAtUnix = currentUnixTimestamp(),
            )
            data.vaults.add(vault)
            vault
        }
}
