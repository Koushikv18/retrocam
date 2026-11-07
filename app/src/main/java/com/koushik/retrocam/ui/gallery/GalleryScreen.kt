package com.koushik.retrocam.ui.gallery

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koushik.retrocam.data.model.MediaItem
import com.koushik.retrocam.ui.theme.*
import com.koushik.retrocam.util.MediaStoreHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun refreshMedia() {
        coroutineScope.launch {
            val items = MediaStoreHelper.queryGalleryMedia(context)
            android.util.Log.i("GalleryScreen", "Loaded ${items.size} media items")
            mediaItems = items
        }
    }

    LaunchedEffect(Unit) {
        refreshMedia()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PHOTOS",
                        color = RetroWhite,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = RetroWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = RetroBlack
                )
            )

            if (mediaItems.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = RetroDarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO PHOTOS YET",
                            color = RetroGray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1.0f)
                                .clickable { selectedItem = item }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (item.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(RetroBlack.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Video",
                                        tint = RetroWhite,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Viewer
        selectedItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RetroBlack)
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(RetroAmber.copy(alpha = 0.9f), CircleShape)
                            .clickable {
                                val playIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(item.uri, "video/*")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(android.content.Intent.createChooser(playIntent, "Play Video"))
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No video player found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = RetroBlack,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // File name footer
                Text(
                    text = item.displayName,
                    color = RetroGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .background(RetroBlack.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Top bar in viewer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedItem = null },
                        modifier = Modifier.background(RetroDarkSurface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = RetroWhite
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Edit / Re-apply look button
                        if (!item.isVideo) {
                            IconButton(
                                onClick = { onNavigateToEdit(item.uri) },
                                modifier = Modifier.background(RetroDarkSurface.copy(alpha = 0.8f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = "Edit Look",
                                    tint = RetroAmber
                                )
                            }
                        }

                        // Share button
                        IconButton(
                            onClick = { MediaStoreHelper.shareMedia(context, item.uri, item.isVideo) },
                            modifier = Modifier.background(RetroDarkSurface.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = RetroWhite
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.background(RetroDarkSurface.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = RetroRed
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Photo?", color = RetroWhite, fontFamily = FontFamily.Monospace) },
                text = { Text("This will permanently remove the file from your device.", color = RetroGray) },
                containerColor = RetroDarkSurface,
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedItem?.let { item ->
                                coroutineScope.launch {
                                    MediaStoreHelper.deleteMedia(context, item.uri)
                                    selectedItem = null
                                    showDeleteDialog = false
                                    refreshMedia()
                                }
                            }
                        }
                    ) {
                        Text("DELETE", color = RetroRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL", color = RetroWhite)
                    }
                }
            )
        }
    }
}

