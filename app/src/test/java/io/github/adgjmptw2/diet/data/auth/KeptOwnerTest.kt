package io.github.adgjmptw2.diet.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeptOwnerTest {
    private val guest = AuthSnapshot(AuthState(account = AccountType.GUEST), null, 0, 0)

    @Test fun unlinkedGuestRecordsHaveNoOwner() {
        assertNull(guest.ownerConflict("first-account"))
    }

    @Test fun retainedRecordsRejectAnotherAccountAndIdentifyTheirOwner() {
        val kept = guest.copy(keptOwnerId = "a", keptOwnerEmail = "a@example.com")
        assertEquals("이 기기 기록은 a@example.com 계정 것이에요. 그 계정으로 로그인하거나 설정에서 기록을 지운 뒤 연결하세요",
            kept.ownerConflict("b"))
    }

    @Test fun sameOwnerCanReconnectEvenWhenTheirEmailChanged() {
        assertNull(guest.copy(keptOwnerId = "a", keptOwnerEmail = "old@example.com").ownerConflict("a"))
    }

    @Test fun currentlyLinkedAccountAlsoRejectsAnotherAccount() {
        val linked = guest.copy(state = AuthState(AccountType.GOOGLE, "a@example.com", "a"))
        assertNotNull(linked.ownerConflict("b"))
        assertNull(linked.ownerConflict("a"))
    }
}
