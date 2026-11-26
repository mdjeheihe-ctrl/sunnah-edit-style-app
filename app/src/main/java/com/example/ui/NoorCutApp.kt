package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.StudioTab
import com.example.ui.components.ArchitectureSheet
import com.example.ui.components.AudioTab
import com.example.ui.components.CaptionsTab
import com.example.ui.components.ExportTab
import com.example.ui.components.TimelineTab
import com.example.ui.components.VideoPreviewPane
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.SunnahGoldContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoorCutApp(
    viewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isAnalyzingVideo by viewModel.isAnalyzingVideo.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val timelinePositionMs by viewModel.timelinePositionMs.collectAsStateWithLifecycle()

    var showArchitectureSheet by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onVideoSelected(uri)
            }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onCustomAudioSelected(uri)
            }
        }
    )

    val launchVideoPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val launchAudioPicker = {
        audioPickerLauncher.launch(arrayOf("audio/*"))
    }

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioObsidian,
                    titleContentColor = TextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NoorCut",
                            style = MaterialTheme.typography.headlineSmall,
                            color = SunnahGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = StudioSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioOutline)
                        ) {
                            Text(
                                text = "SUNNAH STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (project.sourceVideoUri != null) {
                        IconButton(
                            onClick = launchVideoPicker,
                            modifier = Modifier.testTag("top_replace_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = "Change raw video",
                                tint = SunnahGold
                            )
                        }
                    }
                    IconButton(
                        onClick = { showArchitectureSheet = true },
                        modifier = Modifier.testTag("architecture_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Architecture,
                            contentDescription = "View pipeline architecture",
                            tint = TextSecondary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = StudioSurface,
                tonalElevation = 0.dp
            ) {
                StudioTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        StudioTab.TIMELINE -> Icons.Filled.ContentCut
                        StudioTab.CAPTIONS -> Icons.Filled.Subtitles
                        StudioTab.AUDIO -> Icons.Filled.AudioFile
                        StudioTab.EXPORT -> Icons.Filled.MovieCreation
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SunnahGold,
                            selectedTextColor = SunnahGold,
                            indicatorColor = SunnahGoldContainer,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name}")
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            val isWide = maxWidth >= 680.dp
            val activeSegOffset = viewModel.activeSegmentAt(timelinePositionMs)

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        VideoPreviewPane(
                            project = project,
                            videoPlayer = viewModel.videoPlayer,
                            isPlaying = isPlaying,
                            isAnalyzingVideo = isAnalyzingVideo,
                            timelinePositionMs = timelinePositionMs,
                            activeSegmentAndOffset = activeSegOffset,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekTimeline = viewModel::seekToTimelineMs,
                            onPickVideo = launchVideoPicker
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    ) {
                        StudioActiveTabContent(
                            selectedTab = selectedTab,
                            project = project,
                            aiState = aiState,
                            exportState = exportState,
                            timelinePositionMs = timelinePositionMs,
                            viewModel = viewModel,
                            onPickCustomAudio = launchAudioPicker,
                            onShareFile = { path ->
                                val intent = viewModel.buildShareIntent(path)
                                if (intent != null) {
                                    context.startActivity(
                                        android.content.Intent.createChooser(intent, "Share Sunnah Edit")
                                    )
                                } else {
                                    Toast.makeText(context, "Export file not found.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VideoPreviewPane(
                        project = project,
                        videoPlayer = viewModel.videoPlayer,
                        isPlaying = isPlaying,
                        isAnalyzingVideo = isAnalyzingVideo,
                        timelinePositionMs = timelinePositionMs,
                        activeSegmentAndOffset = activeSegOffset,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSeekTimeline = viewModel::seekToTimelineMs,
                        onPickVideo = launchVideoPicker
                    )

                    StudioActiveTabContent(
                        selectedTab = selectedTab,
                        project = project,
                        aiState = aiState,
                        exportState = exportState,
                        timelinePositionMs = timelinePositionMs,
                        viewModel = viewModel,
                        onPickCustomAudio = launchAudioPicker,
                        onShareFile = { path ->
                            val intent = viewModel.buildShareIntent(path)
                            if (intent != null) {
                                context.startActivity(
                                    android.content.Intent.createChooser(intent, "Share Sunnah Edit")
                                )
                            } else {
                                Toast.makeText(context, "Export file not found.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showArchitectureSheet) {
        ArchitectureSheet(onDismiss = { showArchitectureSheet = false })
    }
}

@Composable
private fun StudioActiveTabContent(
    selectedTab: StudioTab,
    project: com.example.model.EditProject,
    aiState: com.example.model.AiAnalysisState,
    exportState: com.example.model.ExportState,
    timelinePositionMs: Long,
    viewModel: StudioViewModel,
    onPickCustomAudio: () -> Unit,
    onShareFile: (String) -> Unit
) {
    when (selectedTab) {
        StudioTab.TIMELINE -> TimelineTab(
            project = project,
            aiState = aiState,
            onTopicPromptChange = viewModel::updateTopicPrompt,
            onRunAiDirector = viewModel::runAiDirector,
            onRunSmartAutoTrim = viewModel::runSmartAutoTrim,
            onToggleSegment = viewModel::toggleSegment,
            onUpdateBounds = viewModel::updateSegmentBounds,
            onUpdateCue = viewModel::updateSegmentCue,
            onMoveSegment = viewModel::moveSegment
        )

        StudioTab.CAPTIONS -> CaptionsTab(
            project = project,
            currentTimelineMs = timelinePositionMs,
            onAddCaption = viewModel::addCaption,
            onUpdateCaption = viewModel::updateCaption,
            onDeleteCaption = viewModel::deleteCaption
        )

        StudioTab.AUDIO -> AudioTab(
            audioMix = project.audioMix,
            onPickCustomAudio = onPickCustomAudio,
            onUseBuiltInAmbient = viewModel::selectBuiltInAmbient,
            onUpdateAudioMix = viewModel::updateAudioMix
        )

        StudioTab.EXPORT -> ExportTab(
            project = project,
            exportState = exportState,
            onSelectQuality = viewModel::updateExportQuality,
            onStartExport = viewModel::startExport,
            onCancelExport = viewModel::cancelExport,
            onShareFile = onShareFile
        )
    }
}
