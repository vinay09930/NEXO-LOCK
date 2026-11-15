package com.vinay.nexolock

import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.vault.VaultKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Intruder Vault must stay independent from the Personal Vault. These
 * assertions pin the construction-level separation: different credential,
 * different Keystore alias, different AES-GCM associated data and different
 * directory id. A Personal Vault unlock can therefore never decrypt Intruder
 * data (and vice versa), even if both PINs were somehow equal.
 */
class VaultKindIndependenceTest {

    @Test
    fun everyVaultHasItsOwnIdentityCredentialKeyAndAad() {
        val kinds = VaultKind.entries
        assertEquals(2, kinds.size)
        assertEquals(kinds.size, kinds.map { it.id }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.credentialKind }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.keyAlias }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.aad }.toSet().size)
    }

    @Test
    fun vaultCredentialsNeverReuseTheMainOrRecoveryCredentials() {
        val vaultCredentials = VaultKind.entries.map { it.credentialKind }.toSet()
        assertFalse(CredentialKind.MAIN_PIN in vaultCredentials)
        assertFalse(CredentialKind.RECOVERY_PIN in vaultCredentials)
        assertFalse(CredentialKind.MAIN_GESTURE in vaultCredentials)
        assertFalse(CredentialKind.RECOVERY_PATTERN in vaultCredentials)
        assertTrue(CredentialKind.PERSONAL_VAULT_PIN in vaultCredentials)
        assertTrue(CredentialKind.INTRUDER_VAULT_PIN in vaultCredentials)
    }

    @Test
    fun aadNamespacesCannotCollideAcrossVaults() {
        val personal = VaultKind.PERSONAL
        val intruder = VaultKind.INTRUDER
        assertFalse(personal.aad.contains(intruder.id))
        assertFalse(intruder.aad.contains(personal.id))
        // Attachment/index AADs are derived from these prefixes, so distinct
        // prefixes keep every derived AAD distinct by construction.
        assertFalse("${personal.aad}|attachment|x" == "${intruder.aad}|attachment|x")
    }
}
