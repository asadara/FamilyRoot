package com.example.familytreeplatform

import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipPathLabelTest {
    @Test
    fun `parent child labels follow traversal direction`() {
        assertEquals(
            "orang tua dari Budi",
            relationshipPathLabel("PARENT_CHILD", "BIOLOGICAL", "FORWARD", "Budi")
        )
        assertEquals(
            "anak dari Hadi",
            relationshipPathLabel("PARENT_CHILD", "BIOLOGICAL", "REVERSE", "Hadi")
        )
    }

    @Test
    fun `adoptive and spouse labels remain explicit`() {
        assertEquals(
            "anak angkat dari Ibu",
            relationshipPathLabel("PARENT_CHILD", "ADOPTIVE", "REVERSE", "Ibu")
        )
        assertEquals(
            "pasangan dari Siti",
            relationshipPathLabel("SPOUSE", "MARRIED", "FORWARD", "Siti")
        )
    }

    @Test
    fun `care paths use explicit non-lineage labels`() {
        assertEquals(
            "pengasuh dari Budi",
            relationshipPathLabel("PARENT_CHILD", "FOSTER", "FORWARD", "Budi")
        )
        assertEquals(
            "anak asuh dari Ibu Asuh",
            relationshipPathLabel("PARENT_CHILD", "FOSTER", "REVERSE", "Ibu Asuh")
        )
        assertEquals(
            "wali keluarga dari Sari",
            relationshipPathLabel("PARENT_CHILD", "GUARDIAN", "FORWARD", "Sari")
        )
    }
}
