package com.example.familytreeplatform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import com.example.familytreeplatform.feature.graph.QuickRelationKind
import com.example.familytreeplatform.feature.graph.GraphQuickAddRequest
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class GraphCardInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionControlsInspectorAndBackgroundFollowLockedGestureStates() {
        var requestedKind: QuickRelationKind? = null
        var clearCount = 0
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            var inspectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "budi",
                    selectedPersonId = selectedPersonId,
                    inspectedPersonId = inspectedPersonId,
                    persons = listOf(person("budi", "Budi Santoso")),
                    relations = RelationsResponse(personId = "budi"),
                    allRelationships = emptyList(),
                    onSelectPerson = {
                        selectedPersonId = it
                        inspectedPersonId = null
                    },
                    onInspectPerson = {
                        selectedPersonId = it
                        inspectedPersonId = it
                    },
                    onQuickAddRequest = { requestedKind = it.kind },
                    onClearSelection = {
                        clearCount++
                        selectedPersonId = null
                        inspectedPersonId = null
                    },
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Tambah anak"))
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Budi Santoso").assertIsSelected()
        composeRule.onAllNodes(hasContentDescription("Tambah orang tua")).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Tambah anak")).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Tambah pasangan")).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Lihat profil lengkap"))
            .assertCountEquals(0)

        composeRule.onNodeWithTag("quick-add-child-budi")
            .performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(QuickRelationKind.CHILD, requestedKind) }

        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performClick()
        composeRule.onNodeWithText("Lihat profil lengkap").assertIsDisplayed()

        composeRule.onNodeWithTag("graph-background", useUnmergedTree = true)
            .performTouchInput { click(Offset(4f, 4f)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, clearCount) }
        composeRule.onAllNodes(hasContentDescription("Tambah anak"))
            .assertCountEquals(0)
        composeRule.onAllNodes(hasText("Lihat profil lengkap"))
            .assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performTouchInput { doubleClick(center) }
        composeRule.onNodeWithText("Lihat profil lengkap").assertIsDisplayed()
    }

    @Test
    fun singleRecordedParentUsesVisualSlotWithoutCreatingADummyPerson() {
        var request: GraphQuickAddRequest? = null
        val relationship = ExportRelationship(
            relationshipId = "budi-raka",
            type = "PARENT_CHILD",
            fromPersonId = "budi",
            toPersonId = "raka",
            meta = "BIOLOGICAL",
            createdAt = "2026-07-20"
        )
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "raka",
                    selectedPersonId = selectedPersonId,
                    persons = listOf(person("budi", "Budi"), person("raka", "Raka")),
                    relations = RelationsResponse(
                        personId = "raka",
                        parents = listOf(
                            RelationItem(
                                relationshipId = relationship.relationshipId,
                                type = relationship.type,
                                fromPersonId = relationship.fromPersonId,
                                toPersonId = relationship.toPersonId,
                                meta = relationship.meta,
                                createdAt = relationship.createdAt
                            )
                        )
                    ),
                    allRelationships = listOf(relationship),
                    onSelectPerson = { selectedPersonId = it },
                    onQuickAddRequest = { request = it },
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("unrecorded-parent-raka").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(QuickRelationKind.PARENT, request?.kind)
            assertEquals("raka", request?.anchorPersonId)
        }
        composeRule.onAllNodes(hasContentDescription("Budi", substring = true))
            .assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Raka"))
            .assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Raka").performClick()
        composeRule.onNodeWithTag("lineage-parents-center").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Tambah orang tua")).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Tambah anak")).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Tambah pasangan")).assertCountEquals(1)
    }

    @Test
    fun currentPartnerIsNotInferredAsParentOfSingleParentChild() {
        var snapshot: GraphExportSnapshot? = null
        val relationships = listOf(
            ExportRelationship(
                relationshipId = "raka-alya",
                type = "SPOUSE",
                fromPersonId = "raka",
                toPersonId = "alya",
                meta = "MARRIED",
                startDate = "2020-01-01",
                createdAt = "2026-07-20"
            ),
            ExportRelationship(
                relationshipId = "raka-child",
                type = "PARENT_CHILD",
                fromPersonId = "raka",
                toPersonId = "child",
                meta = "BIOLOGICAL",
                createdAt = "2026-07-20"
            )
        )
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "raka",
                    selectedPersonId = null,
                    persons = listOf(
                        person("raka", "Raka Santoso").copy(
                            gender = "MALE",
                            birthDate = "1990-01-01"
                        ),
                        person("alya", "Alya"),
                        person("child", "Anak Raka")
                    ),
                    relations = RelationsResponse(
                        personId = "raka",
                        children = listOf(relationships[1].asRelationItem()),
                        spouses = listOf(relationships[0].asRelationItem())
                    ),
                    allRelationships = relationships,
                    onSelectPerson = {},
                    onClearSelection = {},
                    onOpenPerson = {},
                    onExportSnapshotChanged = { snapshot = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Anak Raka").assertIsDisplayed()
        composeRule.runOnIdle {
            val export = snapshot
            assertNotNull(export)
            val raka = export!!.tiles.single { it.id == "raka" }
            val alya = export.tiles.single { it.id == "alya" }
            val childEdge = export.lineageLines.single { it.type == "PARENT_CHILD" }
            val rakaCenter = raka.x + raka.width / 2f
            val partnershipCenter = (rakaCenter + alya.x + alya.width / 2f) / 2f
            assertEquals(rakaCenter, childEdge.fromX, 0.01f)
            assertNotEquals(partnershipCenter, childEdge.fromX, 0.01f)
            assertEquals("Raka", raka.label)
            assertEquals("MALE", raka.gender)
            assertEquals("ALIVE", raka.lifeStatus)
            assertNotNull(raka.age)
        }
    }

    @Test
    fun viewerGetsLockedMissingDirectionsInsteadOfAddActions() {
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "budi",
                    selectedPersonId = selectedPersonId,
                    persons = listOf(person("budi", "Budi")),
                    relations = RelationsResponse(personId = "budi"),
                    allRelationships = emptyList(),
                    canEditRelationships = false,
                    onSelectPerson = { selectedPersonId = it },
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Budi").performClick()
        composeRule.onAllNodes(hasContentDescription("Tambah orang tua")).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Tambah anak")).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Tambah pasangan")).assertCountEquals(0)
        composeRule.onNodeWithTag("locked-parent-budi").assertIsDisplayed()
        composeRule.onNodeWithTag("locked-child-budi").assertIsDisplayed()
        composeRule.onNodeWithTag("locked-partner-budi").assertIsDisplayed()
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-07-20",
        lifeStatus = "ALIVE"
    )

    private fun ExportRelationship.asRelationItem() = RelationItem(
        relationshipId = relationshipId,
        type = type,
        fromPersonId = fromPersonId,
        toPersonId = toPersonId,
        meta = meta,
        startDate = startDate,
        endDate = endDate,
        createdAt = createdAt
    )
}
