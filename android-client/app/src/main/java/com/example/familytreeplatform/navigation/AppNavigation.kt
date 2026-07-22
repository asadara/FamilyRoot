package com.example.familytreeplatform.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.familytreeplatform.FamilyTreeApplication
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.feature.auth.AuthScreen
import com.example.familytreeplatform.feature.auth.AuthViewModel
import com.example.familytreeplatform.feature.spaces.SpaceSelectionScreen
import com.example.familytreeplatform.feature.spaces.SpaceSelectionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.familytreeplatform.feature.people.PeopleScreen
import com.example.familytreeplatform.feature.people.PeopleViewModel
import com.example.familytreeplatform.feature.activity.ActivityScreen
import com.example.familytreeplatform.feature.activity.ActivityViewModel
import com.example.familytreeplatform.feature.graph.TreeGraphScreen
import com.example.familytreeplatform.feature.graph.TreeGraphViewModel
import com.example.familytreeplatform.feature.home.HomeScreen
import com.example.familytreeplatform.feature.home.HomeViewModel
import com.example.familytreeplatform.feature.persondetail.PersonDetailScreen
import com.example.familytreeplatform.feature.persondetail.PersonDetailViewModel
import com.example.familytreeplatform.feature.profile.ProfileScreen
import com.example.familytreeplatform.feature.spacesettings.SpaceSettingsScreen
import com.example.familytreeplatform.feature.spacesettings.SpaceSettingsViewModel
import com.example.familytreeplatform.feature.support.AboutScreen
import com.example.familytreeplatform.feature.support.HelpScreen
import com.example.familytreeplatform.data.local.OfflineMutationStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

object Routes {
    const val AUTH = "auth"
    const val SPACES = "spaces"
    const val PEOPLE = "people"
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val GRAPH = "graph"
    const val SPACE_SETTINGS = "space-settings"
    const val PROFILE = "profile"
    const val ABOUT = "about"
    const val HELP = "help"
    const val PERSON_DETAIL = "person/{personId}"
    fun personDetail(personId: String) = "person/$personId"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val repository = (context.applicationContext as FamilyTreeApplication).container.personRepository
    val token by SessionStore.accessToken.collectAsState()
    val spaceId by SessionStore.activeSpaceId.collectAsState()
    val spaceName by SessionStore.activeSpaceName.collectAsState()
    val spaceRole by SessionStore.activeSpaceRole.collectAsState()
    val userDisplayName by SessionStore.userDisplayName.collectAsState()
    val userEmail by SessionStore.userEmail.collectAsState()
    val userId by SessionStore.userId.collectAsState()
    val restoring by SessionStore.restoring.collectAsState()
    val hasPersistedSession by SessionStore.hasPersistedSession.collectAsState()
    val scope = rememberCoroutineScope()
    val shellPeopleFlow = remember(spaceId) {
        spaceId?.let(repository::observePersons) ?: flowOf(emptyList())
    }
    val shellPeople by shellPeopleFlow.collectAsState(initial = emptyList())
    val syncMutationsFlow = remember(spaceId) {
        spaceId?.let(repository::observeOfflineMutationsForSpace) ?: flowOf(emptyList())
    }
    val syncMutations by syncMutationsFlow.collectAsState(initial = emptyList())
    val pendingSyncCount = syncMutations.count {
        it.status == OfflineMutationStatus.PENDING || it.status == OfflineMutationStatus.SYNCING
    }
    val syncConflictCount = syncMutations.count { it.status == OfflineMutationStatus.CONFLICT }
    val syncFailedCount = syncMutations.count { it.status == OfflineMutationStatus.FAILED }
    var requestedSearchPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    val navigateTopLevel: (String) -> Unit = { route ->
        if (route == Routes.ABOUT || route == Routes.HELP) {
            navController.navigate(route) { launchSingleTop = true }
        } else {
            navController.navigate(route) {
                popUpTo(Routes.GRAPH) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (restoring) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val target = when {
        token == null && !hasPersistedSession -> Routes.AUTH
        spaceId == null -> Routes.SPACES
        else -> Routes.GRAPH
    }

    LaunchedEffect(target) {
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(spaceId) {
        val selectedSpaceId = spaceId ?: return@LaunchedEffect
        repository.listSpaces().onSuccess { spaces ->
            spaces.firstOrNull { it.spaceId == selectedSpaceId }?.let { space ->
                SessionStore.updateActiveSpace(space.name, space.role)
            }
        }
        repository.listPersons(selectedSpaceId)
    }

    val openSearchResult: (String) -> Unit = { personId ->
        requestedSearchPersonId = personId
        navigateTopLevel(Routes.GRAPH)
    }

    NavHost(navController = navController, startDestination = target, modifier = modifier) {
        composable(Routes.AUTH) {
            val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(repository))
            AuthScreen(viewModel = authViewModel)
        }
        composable(Routes.SPACES) {
            val spaceSelectionViewModel: SpaceSelectionViewModel = viewModel(
                factory = SpaceSelectionViewModel.Factory(repository)
            )
            SpaceSelectionScreen(viewModel = spaceSelectionViewModel)
        }
        composable(Routes.PEOPLE) {
            val selectedSpaceId = spaceId ?: return@composable
            val peopleViewModel: PeopleViewModel = viewModel(
                factory = PeopleViewModel.Factory(selectedSpaceId, repository)
            )
            FamilyRootNavigationShell(
                currentRoute = Routes.PEOPLE,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                PeopleScreen(
                    viewModel = peopleViewModel,
                    onPersonClick = { navController.navigate(Routes.personDetail(it)) },
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.GRAPH) {
            val selectedSpaceId = spaceId ?: return@composable
            val graphViewModel: TreeGraphViewModel = viewModel(
                factory = TreeGraphViewModel.Factory(selectedSpaceId, repository)
            )
            var graphShellAction by remember { mutableStateOf<GraphShellAction?>(null) }
            LaunchedEffect(requestedSearchPersonId) {
                requestedSearchPersonId?.let(graphViewModel::selectSearchResult)
                requestedSearchPersonId = null
            }
            FamilyRootNavigationShell(
                currentRoute = Routes.GRAPH,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } },
                onGraphAction = { graphShellAction = it }
            ) { shellModifier ->
                TreeGraphScreen(
                    viewModel = graphViewModel,
                    canEditRelationships = spaceRole != null && spaceRole != "VIEWER",
                    onBack = { navigateTopLevel(Routes.PEOPLE) },
                    onOpenPerson = { navController.navigate(Routes.personDetail(it)) },
                    shellAction = graphShellAction,
                    onShellActionConsumed = { graphShellAction = null },
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.HOME) {
            val selectedSpaceId = spaceId ?: return@composable
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(selectedSpaceId, repository)
            )
            FamilyRootNavigationShell(
                currentRoute = Routes.HOME,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                HomeScreen(
                    viewModel = homeViewModel,
                    displayName = userDisplayName ?: "Keluarga",
                    spaceName = spaceName ?: "Silsilah",
                    pendingSyncCount = pendingSyncCount,
                    onOpenTree = { navigateTopLevel(Routes.GRAPH) },
                    onOpenFamily = { navigateTopLevel(Routes.PEOPLE) },
                    onOpenActivity = { navigateTopLevel(Routes.ACTIVITY) },
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.SPACE_SETTINGS) {
            val selectedSpaceId = spaceId ?: return@composable
            val spaceSettingsViewModel: SpaceSettingsViewModel = viewModel(
                factory = SpaceSettingsViewModel.Factory(selectedSpaceId, repository)
            )
            FamilyRootNavigationShell(
                currentRoute = Routes.SPACE_SETTINGS,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) { launchSingleTop = true } },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                SpaceSettingsScreen(
                    viewModel = spaceSettingsViewModel,
                    onBack = { navController.popBackStack() },
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.PROFILE) {
            FamilyRootNavigationShell(
                currentRoute = Routes.PROFILE,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                ProfileScreen(
                    displayName = userDisplayName ?: "Akun TRêdhAH",
                    email = userEmail,
                    userId = userId,
                    spaceName = spaceName ?: "Silsilah",
                    pendingSyncCount = pendingSyncCount,
                    onOpenSpaceSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                    onSignOut = { scope.launch { repository.logout() } },
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.ACTIVITY) {
            val selectedSpaceId = spaceId ?: return@composable
            val activityViewModel: ActivityViewModel = viewModel(
                factory = ActivityViewModel.Factory(selectedSpaceId, repository)
            )
            FamilyRootNavigationShell(
                currentRoute = Routes.ACTIVITY,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                ActivityScreen(
                    viewModel = activityViewModel,
                    currentUserId = userId,
                    modifier = shellModifier
                )
            }
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) { entry ->
            val selectedSpaceId = spaceId ?: return@composable
            val personId = requireNotNull(entry.arguments?.getString("personId"))
            val detailViewModel: PersonDetailViewModel = viewModel(
                factory = PersonDetailViewModel.Factory(selectedSpaceId, personId, repository)
            )
            FamilyRootNavigationShell(
                currentRoute = Routes.PERSON_DETAIL,
                onNavigate = navigateTopLevel,
                spaceName = spaceName ?: "Silsilah",
                userDisplayName = userDisplayName ?: "Akun",
                userEmail = userEmail,
                people = shellPeople,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                onSearchPerson = openSearchResult,
                onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SPACE_SETTINGS) },
                onSignOut = { scope.launch { repository.logout() } }
            ) { shellModifier ->
                PersonDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    modifier = shellModifier
                )
            }
        }
    }
}
