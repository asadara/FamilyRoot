package com.example.familytreeplatform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionFeedbackTest {
    @Test
    fun mutationFeedbackIsConciseAndStatusAware() {
        assertEquals(
            ActionFeedbackKind.SUCCESS,
            actionFeedbackForHttp("POST", "/persons", 201)?.kind
        )
        assertEquals(
            ActionFeedbackKind.WARNING,
            actionFeedbackForHttp("PATCH", "/persons/id/profile", 409)?.kind
        )
        assertEquals(
            ActionFeedbackKind.ERROR,
            actionFeedbackForHttp("POST", "/proposals/id/comments", 400)?.kind
        )
    }

    @Test
    fun readsAndNotificationMaintenanceDoNotCreateFeedbackLoops() {
        assertNull(actionFeedbackForHttp("GET", "/persons", 200))
        assertNull(actionFeedbackForHttp("POST", "/notifications/read-all", 201))
    }
}
