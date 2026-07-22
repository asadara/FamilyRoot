package com.example.familytreeplatform.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.feature.support.applicationVersionLabel
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.ui.branding.TredhahBrand
import com.example.familytreeplatform.ui.branding.TredhahLogo

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ShellIcon
)

internal enum class ShellIcon { TREE, HOME, FAMILY, ACTIVITY }

enum class GraphShellAction { EXPORT_PDF, EXPORT_PNG, RESET_VIEW }

internal val topLevelDestinations = listOf(
    TopLevelDestination(Routes.GRAPH, "Pohon", ShellIcon.TREE),
    TopLevelDestination(Routes.HOME, "Beranda", ShellIcon.HOME),
    TopLevelDestination(Routes.PEOPLE, "Keluarga", ShellIcon.FAMILY),
    TopLevelDestination(Routes.ACTIVITY, "Aktivitas", ShellIcon.ACTIVITY)
)

@Composable
internal fun FamilyRootNavigationShell(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    spaceName: String,
    userDisplayName: String,
    userEmail: String?,
    people: List<PersonListItem>,
    pendingSyncCount: Int,
    syncConflictCount: Int = 0,
    syncFailedCount: Int = 0,
    onSearchPerson: (String) -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onGraphAction: ((GraphShellAction) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 840.dp
        val appBar: @Composable () -> Unit = {
            FamilyRootGlobalAppBar(
                spaceName = spaceName,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                people = people,
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount,
                syncFailedCount = syncFailedCount,
                compact = !useNavigationRail,
                onSearchPerson = onSearchPerson,
                onOpenProfile = onOpenProfile,
                onOpenSettings = onOpenSettings,
                onSignOut = onSignOut
            )
        }
        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    TredhahLogo(Modifier.size(48.dp).padding(vertical = 6.dp))
                    topLevelDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            icon = { ShellDestinationIcon(destination.icon) },
                            label = {
                                ShellNavigationLabel(
                                    label = destination.label,
                                    selected = currentRoute == destination.route
                                )
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.width(48.dp).padding(vertical = 4.dp))
                    RailToolsMenu(
                        selected = currentRoute in setOf(Routes.ABOUT, Routes.HELP),
                        onGraphAction = onGraphAction,
                        onNavigate = onNavigate
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "\u00a9 sadar@studio\n2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    appBar()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        content(Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Scaffold(
                topBar = appBar,
                bottomBar = {
                    Column {
                        NavigationBar(
                            modifier = Modifier.height(64.dp),
                            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            topLevelDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { onNavigate(destination.route) },
                                    icon = { ShellDestinationIcon(destination.icon) },
                                    label = {
                                        ShellNavigationLabel(
                                            label = destination.label,
                                            selected = currentRoute == destination.route
                                        )
                                    }
                                )
                            }
                            CompactToolsMenu(
                                selected = currentRoute in setOf(Routes.ABOUT, Routes.HELP),
                                onGraphAction = onGraphAction,
                                onNavigate = onNavigate
                            )
                        }
                        Text(
                            "\u00a9 sadar@studio 2026",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            ) { innerPadding ->
                content(Modifier.fillMaxSize().padding(innerPadding))
            }
        }
    }
}

@Composable
private fun FamilyRootGlobalAppBar(
    spaceName: String,
    userDisplayName: String,
    userEmail: String?,
    people: List<PersonListItem>,
    pendingSyncCount: Int,
    syncConflictCount: Int,
    syncFailedCount: Int,
    compact: Boolean,
    onSearchPerson: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var accountMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchExpanded, compact) {
        if (compact && searchExpanded) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val online = rememberNetworkAvailable()
    val results = filterShellPeople(query, people)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
        ) {
            if (!compact || !searchExpanded) {
                TredhahLogo(Modifier.size(40.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        TredhahBrand.NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        spaceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = if (compact && searchExpanded) Modifier.weight(1f) else Modifier) {
                if (!compact || searchExpanded) {
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            searchExpanded = true
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = (if (compact) Modifier.fillMaxWidth() else Modifier.width(220.dp))
                            .height(44.dp)
                            .focusRequester(searchFocusRequester),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                                ) {
                                    if (query.isBlank()) {
                                        Text(
                                            "Cari person",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = searchExpanded && query.isNotBlank(),
                        onDismissRequest = {
                            searchExpanded = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.width(if (compact) 300.dp else 320.dp),
                        properties = PopupProperties(focusable = false)
                    ) {
                        if (results.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Person tidak ditemukan") },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            results.forEach { person ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(person.fullName, maxLines = 1)
                                            Text(
                                                "Buka inspector di graph",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        query = ""
                                        searchExpanded = false
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onSearchPerson(person.personId)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { searchExpanded = true }) { Text("Cari") }
                }
            }

            if (compact && searchExpanded) {
                TextButton(onClick = {
                    query = ""
                    searchExpanded = false
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }) { Text("Batal") }
            } else {
                Text(
                    text = when {
                        !online -> "Offline"
                        syncConflictCount > 0 -> "$syncConflictCount konflik"
                        syncFailedCount > 0 -> "$syncFailedCount gagal"
                        pendingSyncCount > 0 -> "$pendingSyncCount menunggu sync"
                        else -> "Sinkron"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !online -> MaterialTheme.colorScheme.tertiary
                        syncConflictCount > 0 || syncFailedCount > 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                if (!compact) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 8.dp).widthIn(max = 150.dp)
                    ) {
                        Text(
                            userDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Akun saya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(38.dp)
                            .semantics { contentDescription = "Akun $userDisplayName" }
                            .clickable { accountMenuExpanded = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                initials(userDisplayName),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false },
                        modifier = Modifier.width(280.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp)) {
                            Text(
                                userDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            userEmail?.takeIf(String::isNotBlank)?.let { email ->
                                Text(
                                    email,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "Akun yang sedang digunakan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Lihat profil akun")
                                    Text(
                                        "Identitas dan sesi TRêdhAH",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                accountMenuExpanded = false
                                onOpenProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pengaturan silsilah") },
                            onClick = {
                                accountMenuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Keluar") },
                            onClick = {
                                accountMenuExpanded = false
                                onSignOut()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailToolsMenu(
    selected: Boolean,
    onGraphAction: ((GraphShellAction) -> Unit)?,
    onNavigate: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.width(80.dp).height(40.dp)
        ) {
            ShellNavigationLabel("Alat", selected || expanded)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ToolsMenuItems(
                onGraphAction = onGraphAction,
                onNavigate = onNavigate,
                close = { expanded = false }
            )
        }
    }
}

@Composable
private fun RowScope.CompactToolsMenu(
    selected: Boolean,
    onGraphAction: ((GraphShellAction) -> Unit)?,
    onNavigate: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    NavigationBarItem(
        selected = selected || expanded,
        onClick = { expanded = true },
        icon = {
            Box {
                Text("\u22ef")
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ToolsMenuItems(
                        onGraphAction = onGraphAction,
                        onNavigate = onNavigate,
                        close = { expanded = false }
                    )
                }
            }
        },
        label = { ShellNavigationLabel("Alat", selected || expanded) }
    )
}

@Composable
private fun ColumnScope.ToolsMenuItems(
    onGraphAction: ((GraphShellAction) -> Unit)?,
    onNavigate: (String) -> Unit,
    close: () -> Unit
) {
    if (onGraphAction != null) {
        DropdownMenuItem(
            text = { Text("Ekspor PDF") },
            onClick = {
                close()
                onGraphAction(GraphShellAction.EXPORT_PDF)
            }
        )
        DropdownMenuItem(
            text = { Text("Ekspor PNG") },
            onClick = {
                close()
                onGraphAction(GraphShellAction.EXPORT_PNG)
            }
        )
        DropdownMenuItem(
            text = { Text("Atur ulang graph") },
            onClick = {
                close()
                onGraphAction(GraphShellAction.RESET_VIEW)
            }
        )
        HorizontalDivider()
    }
    DropdownMenuItem(
        text = { Text("Tentang aplikasi") },
        onClick = {
            close()
            onNavigate(Routes.ABOUT)
        }
    )
    DropdownMenuItem(
        text = { Text("Petunjuk penggunaan") },
        onClick = {
            close()
            onNavigate(Routes.HELP)
        }
    )
    DropdownMenuItem(
        text = {
            Column {
                Text("Versi aplikasi")
                Text(
                    applicationVersionLabel(BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = {},
        enabled = false
    )
}

@Composable
private fun ShellNavigationLabel(label: String, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { this.selected = selected }
    ) {
        Text(label, maxLines = 1)
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(30.dp)
                .height(3.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}
@Composable
private fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    fun currentStatus(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    var available by rememberSaveable { mutableStateOf(currentStatus()) }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = currentStatus()
            }

            override fun onLost(network: Network) {
                available = currentStatus()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return available
}

private fun initials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "FR" }

internal fun filterShellPeople(
    query: String,
    people: List<PersonListItem>,
    limit: Int = 6
): List<PersonListItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return emptyList()
    return people.asSequence()
        .filter { it.fullName.contains(normalized, ignoreCase = true) }
        .take(limit)
        .toList()
}

@Composable
private fun ShellDestinationIcon(icon: ShellIcon) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        when (icon) {
            ShellIcon.TREE -> {
                drawLine(color, Offset(size.width / 2f, size.height * .28f), Offset(size.width / 2f, size.height * .67f), stroke.width)
                drawLine(color, Offset(size.width * .25f, size.height * .52f), Offset(size.width * .75f, size.height * .52f), stroke.width)
                drawCircle(color, 3.dp.toPx(), Offset(size.width / 2f, size.height * .2f), style = stroke)
                drawCircle(color, 3.dp.toPx(), Offset(size.width * .25f, size.height * .75f), style = stroke)
                drawCircle(color, 3.dp.toPx(), Offset(size.width * .75f, size.height * .75f), style = stroke)
                drawLine(color, Offset(size.width * .25f, size.height * .52f), Offset(size.width * .25f, size.height * .62f), stroke.width)
                drawLine(color, Offset(size.width * .75f, size.height * .52f), Offset(size.width * .75f, size.height * .62f), stroke.width)
            }
            ShellIcon.HOME -> {
                val path = Path().apply {
                    moveTo(size.width * .18f, size.height * .48f)
                    lineTo(size.width * .5f, size.height * .2f)
                    lineTo(size.width * .82f, size.height * .48f)
                    lineTo(size.width * .75f, size.height * .48f)
                    lineTo(size.width * .75f, size.height * .82f)
                    lineTo(size.width * .25f, size.height * .82f)
                    lineTo(size.width * .25f, size.height * .48f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            ShellIcon.FAMILY -> {
                drawCircle(color, 3.5.dp.toPx(), Offset(size.width * .38f, size.height * .34f), style = stroke)
                drawCircle(color, 3.dp.toPx(), Offset(size.width * .68f, size.height * .4f), style = stroke)
                drawArc(color, 195f, 150f, false, Offset(size.width * .16f, size.height * .48f), androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .38f), style = stroke)
                drawArc(color, 205f, 130f, false, Offset(size.width * .5f, size.height * .55f), androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .28f), style = stroke)
            }
            ShellIcon.ACTIVITY -> {
                drawCircle(color, size.width * .32f, Offset(size.width / 2f, size.height / 2f), style = stroke)
                drawLine(color, Offset(size.width / 2f, size.height / 2f), Offset(size.width / 2f, size.height * .3f), stroke.width)
                drawLine(color, Offset(size.width / 2f, size.height / 2f), Offset(size.width * .66f, size.height * .58f), stroke.width)
            }
        }
    }
}
