package com.example.familytreeplatform.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.familytreeplatform.FamilyTreeApplication
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.feature.auth.AuthScreen
import com.example.familytreeplatform.feature.spaces.SpaceSelectionScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.familytreeplatform.feature.people.PeopleScreen
import com.example.familytreeplatform.feature.people.PeopleViewModel
import com.example.familytreeplatform.feature.activity.ActivityScreen
import com.example.familytreeplatform.feature.activity.ActivityViewModel
import com.example.familytreeplatform.feature.persondetail.PersonDetailScreen
import com.example.familytreeplatform.feature.persondetail.PersonDetailViewModel
import com.example.familytreeplatform.feature.spacesettings.SpaceSettingsScreen
import com.example.familytreeplatform.feature.spacesettings.SpaceSettingsViewModel

object Routes {
    const val AUTH = "auth"
    const val SPACES = "spaces"
    const val PEOPLE = "people"
    const val ACTIVITY = "activity"
    const val SPACE_SETTINGS = "space-settings"
    const val PERSON_DETAIL = "person/{personId}"
    fun personDetail(personId: String) = "person/$personId"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val repository = (context.applicationContext as FamilyTreeApplication).container.personRepository
    val token by SessionStore.accessToken.collectAsState()
    val spaceId by SessionStore.activeSpaceId.collectAsState()

    val target = when {
        token == null -> Routes.AUTH
        spaceId == null -> Routes.SPACES
        else -> Routes.PEOPLE
    }

    LaunchedEffect(target) {
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = target, modifier = modifier) {
        composable(Routes.AUTH) { AuthScreen(repository = repository) }
        composable(Routes.SPACES) { SpaceSelectionScreen(repository = repository) }
        composable(Routes.PEOPLE) {
            val selectedSpaceId = requireNotNull(spaceId)
            val peopleViewModel: PeopleViewModel = viewModel(
                factory = PeopleViewModel.Factory(selectedSpaceId, repository)
            )
            PeopleScreen(
                viewModel = peopleViewModel,
                onPersonClick = { navController.navigate(Routes.personDetail(it)) },
                onActivityClick = { navController.navigate(Routes.ACTIVITY) },
                onSpaceSettingsClick = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = SessionStore::clear
            )
        }
        composable(Routes.SPACE_SETTINGS) {
            val spaceSettingsViewModel: SpaceSettingsViewModel = viewModel(
                factory = SpaceSettingsViewModel.Factory(requireNotNull(spaceId), repository)
            )
            SpaceSettingsScreen(
                viewModel = spaceSettingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ACTIVITY) {
            val activityViewModel: ActivityViewModel = viewModel(
                factory = ActivityViewModel.Factory(requireNotNull(spaceId), repository)
            )
            ActivityScreen(
                viewModel = activityViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) { entry ->
            val personId = requireNotNull(entry.arguments?.getString("personId"))
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(requireNotNull(spaceId), personId, repository)
            )
            PersonDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
