package dev.orestegabo.portfolio.investments

import kotlin.test.*

class PublicDataPublicationTest {
    @Test fun ownerAndExplicitPublishersAreRecognizedWhileUnknownUsersFailClosed() {
        assertFalse(mayPublishSharedData(null))
        assertFalse(mayPublishSharedData(JsonRecord()))
        assertFalse(mayPublishSharedData(JsonRecord().put("id", "owner")))
        assertFalse(mayPublishSharedData(JsonRecord().put("role", "staff").put("visibility", "shared")))
        assertFalse(mayPublishSharedData(JsonRecord().put("canPublishPublicData", false)))
        assertTrue(mayPublishSharedData(JsonRecord().put("isOwner", true)))
        assertTrue(mayPublishSharedData(JsonRecord().put("isOwner", false).put("canPublishPublicData", true)))
    }
}
