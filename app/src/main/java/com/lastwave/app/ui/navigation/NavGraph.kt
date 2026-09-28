package com.lastwave.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lastwave.app.R
import com.lastwave.app.data.local.SessionPreferences
import com.lastwave.app.data.model.AuthState
import com.lastwave.app.data.repository.AuthRepository
import com.lastwave.app.data.ytmusic.YtMusicAuthManager
import com.lastwave.app.ui.album.AlbumDetailScreen
import com.lastwave.app.ui.artist.ArtistDetailScreen
import com.lastwave.app.ui.auth.AuthViewModel
import com.lastwave.app.ui.auth.LoginScreen
import com.lastwave.app.ui.auth.WebAuthState
import com.lastwave.app.ui.common.ExpressiveLoadingIndicator
import com.lastwave.app.ui.common.ExpressiveMotion
import com.lastwave.app.ui.common.PredictiveBackScreen
import com.lastwave.app.ui.discover.DiscoverScreen
import com.lastwave.app.ui.feed.FeedPlaylistDetailScreen
import com.lastwave.app.ui.generate.GenerateScreen
import com.lastwave.app.ui.generate.MixLauncher
import com.lastwave.app.ui.genres.GenreExplorer
import com.lastwave.app.ui.genres.GenresScreen
import com.lastwave.app.ui.home.FriendProfileScreen
import com.lastwave.app.ui.home.FriendsScreen
import com.lastwave.app.ui.home.HomeViewModel
import com.lastwave.app.ui.newreleases.NewReleasesScreen
import com.lastwave.app.ui.playlist.PlaylistDetailScreen
import com.lastwave.app.ui.search.SearchScreen
import com.lastwave.app.ui.settings.DownloadsScreen
import com.lastwave.app.ui.settings.ExcludedSongsScreen
import com.lastwave.app.ui.settings.ExternalPlaylistImportScreen
import com.lastwave.app.ui.settings.HomeSectionsScreen
import com.lastwave.app.ui.settings.ModulesScreen
import com.lastwave.app.ui.settings.ScrobblerAppsScreen
import com.lastwave.app.ui.settings.SettingsScreen
import com.lastwave.app.ui.settings.YouTubeLoginScreen
import com.lastwave.app.ui.settings.YouTubePlaylistImportScreen
import com.lastwave.app.ui.shell.MainShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Thin bridge so LastWaveNavHost can observe GenreExplorer's pending genre and navigate. */
@Stable
@HiltViewModel
class GenreExplorerNavBridge @Inject constructor(genreExplorer: GenreExplorer) : ViewModel() {
    val pendingGenre = genreExplorer.pendingGenre
}

@Stable
@HiltViewModel
class MixLauncherNavBridge @Inject constructor(val mixLauncher: MixLauncher) : ViewModel()

@Stable
@HiltViewModel
class ArtistAlbumNavBridge @Inject constructor(val navigator: ArtistAlbumNavigator) : ViewModel()

@Stable
@HiltViewModel
class AppRouteNavBridge @Inject constructor(val routeNavigator: AppRouteNavigator) : ViewModel()

/**
 * Splash and onboarding gate for the YouTube Music flow.
 *
 * Routes to MainShell when an onboarding signal is present.
 * Otherwise, routes to the login screen.
 */
@HiltViewModel
class LaunchGateViewModel @Inject constructor(
    authRepository: AuthRepository,
    sessionPreferences: SessionPreferences,
    ytAuthManager: YtMusicAuthManager,
) : ViewModel() {
    sealed interface GateTarget {
        data object Loading : GateTarget
        data object Login : GateTarget
        data object MainShell : GateTarget
    }

    val gateTarget: StateFlow<GateTarget> =
        combine(
            authRepository.authState,
            sessionPreferences.session,
            sessionPreferences.guestMode,
            ytAuthManager.connection,
        ) { authState, session, guestMode, ytConnection ->
            if (!session.isLoaded) {
                GateTarget.Loading
            } else if (authState is AuthState.Unknown) {
                GateTarget.Loading
            } else if (authState is AuthState.SignedIn) {
                GateTarget.MainShell
            } else if (guestMode) {
                GateTarget.MainShell
            } else if (ytConnection.isConnected) {
                GateTarget.MainShell
            } else {
                GateTarget.Login
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GateTarget.Loading,
        )
}

@Composable
fun LastWaveNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    mixNavBridge: MixLauncherNavBridge = hiltViewModel(),
    appRouteBridge: AppRouteNavBridge = hiltViewModel(),
    genreExplorerBridge: GenreExplorerNavBridge = hiltViewModel(),
    navBridge: ArtistAlbumNavBridge = hiltViewModel(),
) {
    val pendingMixSeed by mixNavBridge.mixLauncher.pendingSeed.collectAsStateWithLifecycle()
    LaunchedEffect(pendingMixSeed) {
        if (pendingMixSeed != null) {
            navController.navigate(Screen.Create.route) {
                launchSingleTop = true
            }
        }
    }

    val pendingAppRoute by appRouteBridge.routeNavigator.pendingRoute.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    LaunchedEffect(pendingAppRoute, currentRoute) {
        val route = pendingAppRoute
        if (route != null && currentRoute != null &&
            currentRoute != Screen.Splash.route && currentRoute != Screen.Login.route
        ) {
            if (currentRoute != route) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
            appRouteBridge.routeNavigator.consumeRoute()
        }
    }

    val pendingGenre by genreExplorerBridge.pendingGenre.collectAsStateWithLifecycle()
    LaunchedEffect(pendingGenre) {
        if (pendingGenre != null) {
            navController.navigate(Screen.Genres.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navController, navBridge) {
        navBridge.navigator.events.collect { target ->
            when (target) {
                is ArtistAlbumNavTarget.Artist -> {
                    navController.navigate(Screen.ArtistDetail.createRoute(target.name, target.browseId))
                }
                is ArtistAlbumNavTarget.Album -> {
                    navController.navigate(Screen.AlbumDetail.createRoute(target.title, target.artist, target.browseId))
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier,
        enterTransition = { ExpressiveMotion.forwardEnter() },
        exitTransition = { ExpressiveMotion.forwardExit() },
        popEnterTransition = { ExpressiveMotion.backEnter() },
        popExitTransition = { ExpressiveMotion.backExit() },
    ) {

        composable(Screen.Splash.route) {
            val gateViewModel: LaunchGateViewModel = hiltViewModel()
            val gateTarget by gateViewModel.gateTarget.collectAsStateWithLifecycle()

            LaunchedEffect(gateTarget) {
                when (gateTarget) {
                    LaunchGateViewModel.GateTarget.MainShell -> navController.navigate(Screen.MainShell.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                    LaunchGateViewModel.GateTarget.Login -> navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                    LaunchGateViewModel.GateTarget.Loading -> Unit
                }
            }

            LaunchGate()
        }

        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            val gateViewModel: LaunchGateViewModel = hiltViewModel()
            val gateTarget by gateViewModel.gateTarget.collectAsStateWithLifecycle()
            val webAuthState by authViewModel.webAuthState.collectAsStateWithLifecycle()

            LaunchedEffect(gateTarget, webAuthState) {
                if (gateTarget == LaunchGateViewModel.GateTarget.MainShell &&
                    webAuthState == WebAuthState.Idle
                ) {
                    navController.navigate(Screen.MainShell.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }

            val restoreError = (webAuthState as? WebAuthState.Error)?.message
            val restoring = webAuthState == WebAuthState.RestoringBackup
            LoginScreen(
                onLoginWithYouTube = {
                    navController.navigate(Screen.YouTubeLogin.route)
                },
                onContinueAsGuest = {
                    authViewModel.continueAsGuest()
                },
                onRestoreBackupAndSignIn = authViewModel::restoreBackupOnly,
                onDismissError = authViewModel::dismissError,
                errorMessage = restoreError,
                isBusy = restoring,
                onOpenDownloads = {
                    navController.navigate(Screen.Downloads.route)
                },
            )
        }

        composable(Screen.MainShell.route) {
            MainShell(
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenDiscover = { navController.navigate(Screen.Discover.route) },
                onOpenGenres = { navController.navigate(Screen.Genres.route) },
                onOpenFriends = { navController.navigate(Screen.Friends.route) },
                onOpenFriendProfile = { username, displayName, avatarUrl ->
                    navController.navigate(Screen.FriendProfile.createRoute(username, displayName, avatarUrl))
                },
                onOpenPlaylist = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onOpenFeedPlaylist = { playlistId ->
                    navController.navigate(Screen.FeedPlaylistDetail.createRoute(playlistId))
                },
                onOpenGenerator = {
                    navController.navigate(Screen.Create.route) { launchSingleTop = true }
                },
                onOpenNewReleases = {
                    navController.navigate(Screen.NewReleases.route)
                },
            )
        }

        composable(Screen.Create.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                GenerateScreen(
                    onNavigateToPlaylist = { playlistId ->
                        navController.popBackStack()
                        if (playlistId != null) {
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.Friends.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.MainShell.route)
            }
            val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                FriendsScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenFriendProfile = { username, displayName, avatarUrl ->
                        navController.navigate(Screen.FriendProfile.createRoute(username, displayName, avatarUrl))
                    },
                )
            }
        }

        composable(
            route = Screen.FriendProfile.route,
            arguments = listOf(
                navArgument("username") { type = NavType.StringType },
                navArgument("displayName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("avatarUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username").orEmpty()
            val displayName = backStackEntry.arguments?.getString("displayName")?.takeIf(String::isNotBlank)
            val avatarUrl = backStackEntry.arguments?.getString("avatarUrl")?.takeIf(String::isNotBlank)
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                FriendProfileScreen(
                    username = username,
                    initialDisplayName = displayName,
                    initialAvatarUrl = avatarUrl,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    onOpenAlbum = { albumTitle, artistName ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumTitle, artistName))
                    },
                )
            }
        }

        composable(Screen.Genres.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                GenresScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlaylist = {
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Screen.Settings.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.MainShell.route) { inclusive = true }
                        }
                    },
                    onOpenChooseApps = { navController.navigate(Screen.ScrobblerApps.route) },
                    onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                    onOpenModules = { navController.navigate(Screen.ProviderModules.route) },
                    onOpenHomeSections = { navController.navigate(Screen.HomeSections.route) },
                    onOpenExcludedSongs = { navController.navigate(Screen.ExcludedSongs.route) },
                    onOpenYouTubeImport = { navController.navigate(Screen.YouTubeImport.route) },
                    onOpenYouTubeLogin = { navController.navigate(Screen.YouTubeLogin.route) },
                    onOpenExternalImport = { navController.navigate(Screen.ExternalPlaylistImport.route) },
                )
            }
        }

        composable(Screen.YouTubeImport.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                YouTubePlaylistImportScreen(
                    onBack = { navController.popBackStack() },
                    onImportSuccess = {
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Screen.YouTubeLogin.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                YouTubeLoginScreen(
                    onBack = { navController.popBackStack() },
                    onConnect = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.ExternalPlaylistImport.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                ExternalPlaylistImportScreen(
                    onBack = { navController.popBackStack() },
                    onImportSuccess = {
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Screen.Downloads.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.ProviderModules.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                ModulesScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.HomeSections.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                HomeSectionsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.ExcludedSongs.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                ExcludedSongsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.ScrobblerApps.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                ScrobblerAppsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.Search.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name, browseId ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, browseId))
                    },
                    onOpenAlbum = { title, artist, browseId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, browseId))
                    },
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(Screen.FeedPlaylistDetail.createRoute(playlistId))
                    },
                )
            }
        }

        composable(Screen.Discover.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                DiscoverScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.NewReleases.route) {
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                NewReleasesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Screen.FeedPlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                FeedPlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onOpenPlaylist = { newId ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(newId))
                    },
                )
            }
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(
                navArgument("artistName") { type = NavType.StringType },
                navArgument("browseId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
            val browseId = backStackEntry.arguments?.getString("browseId")?.takeIf(String::isNotBlank)
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                ArtistDetailScreen(
                    artistName = artistName,
                    browseId = browseId,
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { title, artist, id ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, id))
                    },
                    onOpenArtist = { name, id ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, id))
                    },
                )
            }
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                navArgument("albumTitle") { type = NavType.StringType },
                navArgument("artistName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val albumTitle = backStackEntry.arguments?.getString("albumTitle").orEmpty()
            val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
            val browseId = backStackEntry.arguments?.getString("browseId")?.takeIf(String::isNotBlank)
            PredictiveBackScreen(onBack = { navController.popBackStack() }) {
                AlbumDetailScreen(
                    albumTitle = albumTitle,
                    artistName = artistName,
                    browseId = browseId,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name, id ->
                        navController.navigate(Screen.ArtistDetail.createRoute(name, id))
                    },
                    onOpenAlbum = { title, artist, id ->
                        navController.navigate(Screen.AlbumDetail.createRoute(title, artist, id))
                    },
                )
            }
        }
    }
}

@Composable
private fun LaunchGate() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.size(88.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("LastWave", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            ExpressiveLoadingIndicator(message = "Preparing your music")
        }
    }
}
