package com.lastwave.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.ui.common.ArtworkImage
import com.lastwave.app.ui.common.ExpressiveGroup
import com.lastwave.app.ui.common.ExpressiveGroupTrackRow
import com.lastwave.app.ui.common.ExpressiveHeader
import com.lastwave.app.ui.common.ExpressiveLoadingIndicator
import com.lastwave.app.ui.common.adaptiveContentWidth
import com.lastwave.app.ui.common.safeDrawingBottomPadding
import com.lastwave.app.ui.common.safeHorizontalContentPadding
import com.lastwave.app.ui.player.LocalMiniPlayerScrollClearance

private val FriendsContainerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Pushed screen for viewing and selecting friends.
 * Every pushed screen in this app uses this Scaffold-less ExpressiveHeader + Column pattern.
 *
 * Shares HomeViewModel with the Home tab (scoped to MainShell's back stack entry).
 * Switching profiles here is immediately reflected on Home once you navigate back.
 */
@Composable
fun FriendsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayerScrollClearance: Dp = LocalMiniPlayerScrollClearance.current,
    onOpenFriendProfile: (username: String, displayName: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openFriendsSheet()
    }

    val friends = uiState.sortedFriends
    val pinnedFriends = uiState.pinnedFriends
    val isLoading = uiState.isLoadingFriends
    val isViewingFriend = uiState.isViewingFriend

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 760.dp),
        ) {
            ExpressiveHeader(
                title = "Friends",
                subtitle = if (friends.isNotEmpty()) "Long-press a friend to pin them to the top" else null,
                onBack = onBack,
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(FriendsContainerShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeHorizontalContentPadding()
                        .padding(horizontal = 12.dp)
                        .padding(
                            top = 12.dp,
                            bottom = 24.dp + miniPlayerScrollClearance + safeDrawingBottomPadding(),
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator(message = "Loading friends")
                        }
                        friends.isEmpty() -> Text(
                            "No friends found on this Last.fm account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        else -> {
                            val rowCount = friends.size + if (isViewingFriend) 1 else 0
                            ExpressiveGroup(rowCount = rowCount) { index, position ->
                                if (isViewingFriend && index == 0) {
                                    ExpressiveGroupTrackRow(
                                        title = "Back to my profile",
                                        subtitle = "View your own data again",
                                        position = position,
                                        onClick = {
                                            viewModel.returnToOwnProfile()
                                            onBack()
                                        },
                                        leading = {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AccountCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        },
                                    )
                                } else {
                                    val friend = friends[index - if (isViewingFriend) 1 else 0]
                                    val isPinned = friend.name in pinnedFriends
                                    ExpressiveGroupTrackRow(
                                        title = friend.displayName,
                                        subtitle = "@${friend.name}",
                                        position = position,
                                        onClick = {
                                            onOpenFriendProfile(friend.name, friend.displayName, friend.avatarUrl)
                                        },
                                        onLongClick = { viewModel.toggleFriendPinned(friend.name) },
                                        leading = {
                                            if (!friend.avatarUrl.isNullOrBlank()) {
                                                ArtworkImage(
                                                    name = "friend",
                                                    artist = friend.name,
                                                    embeddedUrl = friend.avatarUrl,
                                                    fallbackIcon = Icons.Filled.AccountCircle,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape),
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AccountCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        },
                                        trailing = if (isPinned) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
