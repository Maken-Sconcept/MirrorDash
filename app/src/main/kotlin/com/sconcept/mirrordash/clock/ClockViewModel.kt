package com.sconcept.mirrordash.clock

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.CLOCK_BACKGROUND_MODE_PHOTORAMA
import com.sconcept.mirrordash.settings.SettingsRepository
import com.sconcept.mirrordash.settings.isPhotoramaScheduleActive
import com.sconcept.mirrordash.ui.theme.DEFAULT_CLOCK_FONT_SIZE_SP
import com.sconcept.mirrordash.weather.WEATHER_ICON_STYLE_ANIMATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ClockAppearance(
    val fontSizeSp: Int = DEFAULT_CLOCK_FONT_SIZE_SP,
    val fontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val styleId: String = CLOCK_STYLE_BIG_SMALL,
    val textColor: Color = Color.White,
    val accentColor: Color = Color(0xFFE8A659),
    val background: ClockBackground = ClockBackground.SolidColor(Color.Black),
    val clockAnchor: OverlayAnchor = OverlayAnchor(0.06f, 0.82f),
    val clockRotationDegrees: Float = 0f,
    val weatherWidgets: List<WeatherWidget> = emptyList(),
    /** Weather is a shared data source for style info-lines and any added weather widgets. */
    val weatherEnabled: Boolean = true,
    val weatherIconStyle: String = WEATHER_ICON_STYLE_ANIMATED,
    val downloadedClockFonts: List<DownloadedClockFont> = emptyList(),
    val textWidgets: List<CustomTextWidget> = emptyList(),
    val iconWidgets: List<IconWidget> = emptyList(),
    val calendarWidgets: List<CalendarWidget> = emptyList(),
    val tasksWidgets: List<TasksWidget> = emptyList(),
    val stocksWidgets: List<StocksWidget> = emptyList(),
    val newsWidgets: List<NewsWidget> = emptyList(),
    val showTime: Boolean = true,
    val showWidgets: Boolean = true,
    val showPhotoramaImageCount: Boolean = false,
    val photoramaImageCountFontSizeSp: Int = 24,
    val photoramaImageCountFontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val photoramaImageCountColor: Color = Color(0xFFF5F3EF),
    val photoramaImageCountAnchor: OverlayAnchor = OverlayAnchor(0.5f, 0.94f),
    val photoramaImageCountRotationDegrees: Float = 0f,
)

/**
 * Owns Clock appearance *and* the drag-to-position anchors for the clock/weather clusters
 * (brought back at the user's request after the initial fixed-layout design - see
 * ClockLayoutHelper's doc comment for how this avoids BerthierOptions' original bug: exactly
 * one stored anchor per element, exactly one default, defined once in [MirrorDashSettings]).
 */
class ClockViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {
    private val tasksFileRepository = TasksFileRepository(application)
    private val taskFileCache = mutableMapOf<String, List<TaskItem>>()

    // Re-emits once a minute so a Photorama schedule's start/end boundary flips the background
    // live, without requiring any other state to change first - settings alone wouldn't fire
    // again on their own just because the clock ticked past 22:00.
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val now = Calendar.getInstance()
            val msToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
            delay(msToNextMinute.coerceAtLeast(1000L))
        }
    }

    val appearance: StateFlow<ClockAppearance> = combine(settingsRepository.settings, minuteTicker) { settings, _ -> settings }
        .mapLatest {
            val photoramaActive = it.clockBackgroundMode == CLOCK_BACKGROUND_MODE_PHOTORAMA && isPhotoramaScheduleActive(it)
            val taskWidgets = resolveTasksWidgets(it.tasksWidgets)
            ClockAppearance(
                fontSizeSp = it.clockFontSizeSp,
                fontId = it.clockFontId,
                styleId = it.clockStyleId,
                textColor = Color(it.clockTextColorArgb),
                accentColor = Color(it.clockAccentColorArgb),
                background = if (photoramaActive) {
                    ClockBackground.Photorama
                } else {
                    ClockBackground.SolidColor(Color(it.clockBackgroundColorArgb))
                },
                clockAnchor = OverlayAnchor(it.clockAnchorX, it.clockAnchorY),
                clockRotationDegrees = it.clockRotationDegrees,
                weatherWidgets = it.weatherWidgets,
                weatherEnabled = it.weatherEnabled,
                weatherIconStyle = it.weatherIconStyle,
                downloadedClockFonts = it.downloadedClockFonts,
                textWidgets = it.customTextWidgets,
                iconWidgets = it.iconWidgets,
                calendarWidgets = it.calendarWidgets,
                tasksWidgets = taskWidgets,
                stocksWidgets = it.stocksWidgets,
                newsWidgets = it.newsWidgets,
                showTime = it.clockShowTime,
                showWidgets = it.clockShowWidgets,
                showPhotoramaImageCount = it.photoramaShowImageCount,
                photoramaImageCountFontSizeSp = it.photoramaImageCountFontSizeSp,
                photoramaImageCountFontId = it.photoramaImageCountFontId,
                photoramaImageCountColor = Color(it.photoramaImageCountColorArgb),
                photoramaImageCountAnchor = OverlayAnchor(it.photoramaImageCountAnchorX, it.photoramaImageCountAnchorY),
                photoramaImageCountRotationDegrees = it.photoramaImageCountRotationDegrees,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClockAppearance())

    fun setFontSize(sp: Int) {
        viewModelScope.launch { settingsRepository.update { clockFontSizeSp = sp } }
    }

    fun setTextColor(color: Color) {
        viewModelScope.launch { settingsRepository.update { clockTextColorArgb = color.toArgb() } }
    }

    fun setBackgroundColor(color: Color) {
        viewModelScope.launch { settingsRepository.update { clockBackgroundColorArgb = color.toArgb() } }
    }

    fun setClockAnchor(anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                clockAnchorX = clamped.x
                clockAnchorY = clamped.y
            }
        }
    }

    fun setClockRotation(degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch { settingsRepository.update { clockRotationDegrees = normalized } }
    }

    /** Kept entirely separate from [setClockAnchor] - dragging the clock on the hidden Night
     * Clock tab must never move the daytime Clock page's own layout. */
    fun setNightClockAnchor(anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                nightClockAnchorX = clamped.x
                nightClockAnchorY = clamped.y
            }
        }
    }

    fun setNightClockWeatherAnchor(anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                nightClockWeatherAnchorX = clamped.x
                nightClockWeatherAnchorY = clamped.y
            }
        }
    }

    fun setWeatherWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        val current = appearance.value.weatherWidgets
        viewModelScope.launch {
            settingsRepository.update {
                weatherWidgets = current.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setWeatherWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        val current = appearance.value.weatherWidgets
        viewModelScope.launch {
            settingsRepository.update {
                weatherWidgets = current.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    /** Pinch-to-resize on the Clock page (see [com.sconcept.mirrordash.clock.DraggableAnchor])
     * lands here already clamped to the same 70-140 range Settings' own scale slider uses. */
    fun setWeatherWidgetScale(id: String, scalePercent: Int) {
        val current = appearance.value.weatherWidgets
        viewModelScope.launch {
            settingsRepository.update {
                weatherWidgets = current.map { if (it.id == id) it.copy(scalePercent = scalePercent) else it }
            }
        }
    }

    /** Mirrors SettingsViewModel's own removeXxxWidget functions (the ones its widget-list
     * editors use) - duplicated here rather than reused directly so the trash icon that appears
     * mid-drag on the Clock page (see [DraggableAnchor]'s `onRemove`) can call straight through
     * `clockViewModel::xxx`, the same wiring convention every other ClockScreen callback already
     * uses, instead of threading SettingsViewModel into ClockScreen just for this. */
    fun removeWeatherWidget(id: String) {
        val current = appearance.value.weatherWidgets
        viewModelScope.launch { settingsRepository.update { weatherWidgets = current.filterNot { it.id == id } } }
    }

    fun removeTextWidget(id: String) {
        val current = appearance.value.textWidgets
        viewModelScope.launch { settingsRepository.update { customTextWidgets = current.filterNot { it.id == id } } }
    }

    fun removeIconWidget(id: String) {
        val current = appearance.value.iconWidgets
        viewModelScope.launch { settingsRepository.update { iconWidgets = current.filterNot { it.id == id } } }
    }

    fun removeCalendarWidget(id: String) {
        val current = appearance.value.calendarWidgets
        viewModelScope.launch { settingsRepository.update { calendarWidgets = current.filterNot { it.id == id } } }
    }

    fun removeTasksWidget(id: String) {
        val current = appearance.value.tasksWidgets
        viewModelScope.launch { settingsRepository.update { tasksWidgets = current.filterNot { it.id == id } } }
    }

    fun removeStocksWidget(id: String) {
        val current = appearance.value.stocksWidgets
        viewModelScope.launch { settingsRepository.update { stocksWidgets = current.filterNot { it.id == id } } }
    }

    fun removeNewsWidget(id: String) {
        val current = appearance.value.newsWidgets
        viewModelScope.launch { settingsRepository.update { newsWidgets = current.filterNot { it.id == id } } }
    }

    /** The main clock digits aren't list-backed like the widgets above - there's only ever one,
     * so "remove" here means the same thing the existing Settings > Clock > "Show clock" switch
     * already does ([com.sconcept.mirrordash.settings.SettingsViewModel.setClockShowTime]) -
     * this just gives the Clock page's own trash icon a `clockViewModel::xxx` call to reach it
     * without threading SettingsViewModel in. Re-enabling it is still only reachable from that
     * Settings switch - there's no on-canvas affordance for an element that isn't there anymore. */
    fun hideClockTime() {
        viewModelScope.launch { settingsRepository.update { clockShowTime = false } }
    }

    /** Same "not list-backed" reasoning as [hideClockTime] - the image count overlay's own on/off
     * switch lives in Photorama settings ([com.sconcept.mirrordash.settings.SettingsViewModel.setPhotoramaShowImageCount]),
     * this just gives its on-canvas trash icon a way to reach the same flag. */
    fun hidePhotoramaImageCount() {
        viewModelScope.launch { settingsRepository.update { photoramaShowImageCount = false } }
    }

    fun setPhotoramaImageCountAnchor(anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                photoramaImageCountAnchorX = clamped.x
                photoramaImageCountAnchorY = clamped.y
            }
        }
    }

    fun setPhotoramaImageCountRotation(degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch { settingsRepository.update { photoramaImageCountRotationDegrees = normalized } }
    }

    fun setPhotoramaImageCountFontSize(sp: Int) {
        viewModelScope.launch { settingsRepository.update { photoramaImageCountFontSizeSp = sp } }
    }

    /** Called while dragging a text widget on the Clock page itself - Settings owns everything
     * else about a widget (text/size/color/font/animation/add/remove), but position is set by
     * dragging in place, same as the clock/weather clusters. */
    fun setTextWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                customTextWidgets = customTextWidgets.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setTextWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch {
            settingsRepository.update {
                customTextWidgets = customTextWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    fun setTextWidgetFontSize(id: String, sp: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                customTextWidgets = customTextWidgets.map { if (it.id == id) it.copy(fontSizeSp = sp) else it }
            }
        }
    }

    fun setIconWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch { settingsRepository.update {
            iconWidgets = iconWidgets.map { if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it }
        } }
    }

    fun setIconWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch { settingsRepository.update {
            iconWidgets = iconWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
        } }
    }

    fun setIconWidgetSize(id: String, sp: Int) {
        viewModelScope.launch { settingsRepository.update {
            iconWidgets = iconWidgets.map { if (it.id == id) it.copy(sizeSp = sp) else it }
        } }
    }

    fun setCalendarWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                calendarWidgets = calendarWidgets.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setCalendarWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch {
            settingsRepository.update {
                calendarWidgets = calendarWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    fun setCalendarWidgetFontSize(id: String, sp: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                calendarWidgets = calendarWidgets.map { if (it.id == id) it.copy(fontSizeSp = sp) else it }
            }
        }
    }

    fun setTasksWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                tasksWidgets = tasksWidgets.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setTasksWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch {
            settingsRepository.update {
                tasksWidgets = tasksWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    fun setTasksWidgetFontSize(id: String, sp: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                tasksWidgets = tasksWidgets.map { if (it.id == id) it.copy(fontSizeSp = sp) else it }
            }
        }
    }

    fun setStocksWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                stocksWidgets = stocksWidgets.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setStocksWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch {
            settingsRepository.update {
                stocksWidgets = stocksWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    fun setStocksWidgetFontSize(id: String, sp: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                stocksWidgets = stocksWidgets.map { if (it.id == id) it.copy(fontSizeSp = sp) else it }
            }
        }
    }

    fun setNewsWidgetAnchor(id: String, anchor: OverlayAnchor) {
        val clamped = ClockLayoutHelper.clamp(anchor)
        viewModelScope.launch {
            settingsRepository.update {
                newsWidgets = newsWidgets.map {
                    if (it.id == id) it.copy(anchorX = clamped.x, anchorY = clamped.y) else it
                }
            }
        }
    }

    fun setNewsWidgetRotation(id: String, degrees: Float) {
        val normalized = normalizeRotationDegrees(degrees)
        viewModelScope.launch {
            settingsRepository.update {
                newsWidgets = newsWidgets.map { if (it.id == id) it.copy(rotationDegrees = normalized) else it }
            }
        }
    }

    fun setNewsWidgetFontSize(id: String, sp: Int) {
        viewModelScope.launch {
            settingsRepository.update {
                newsWidgets = newsWidgets.map { if (it.id == id) it.copy(fontSizeSp = sp) else it }
            }
        }
    }

    /** A plain tap directly on a Tasks widget on the Clock page toggles that item - the widget's
     * own [DraggableAnchor] only reacts to long-press-drag, so this never fights with placement. */
    fun setTaskItemCompleted(widgetId: String, itemId: String, completed: Boolean) {
        viewModelScope.launch {
            settingsRepository.update {
                tasksWidgets = tasksWidgets.map { widget ->
                    if (widget.id != widgetId) return@map widget
                    if (!widget.isFileBacked) {
                        widget.copy(
                            items = widget.items.map {
                                if (it.id == itemId) it.withCompleted(completed) else it.normalized()
                            },
                        )
                    } else {
                        val existing = widget.items.firstOrNull { it.id == itemId } ?: TaskItem(id = itemId)
                        widget.copy(
                            items = widget.items.filterNot { it.id == itemId } + existing.withCompleted(completed),
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveTasksWidgets(widgets: List<TasksWidget>): List<TasksWidget> {
        if (widgets.none { it.isFileBacked }) return widgets.map(::normalizeWidget)

        val share = runCatching { settingsRepository.smbShareWithPassword() }.getOrNull()
        return widgets.map { widget ->
            if (!widget.isFileBacked) return@map normalizeWidget(widget)

            val loaded = runCatching {
                require(share?.isConfigured == true) { "No NAS connection configured for task CSV import." }
                tasksFileRepository.loadCsv(share, widget.csvFilePath)
            }.getOrElse {
                taskFileCache[widget.id] ?: emptyList()
            }

            val merged = mergeImportedItems(loaded, widget.items)
            if (merged.isNotEmpty()) taskFileCache[widget.id] = merged
            widget.copy(items = merged)
        }
    }

    private fun normalizeWidget(widget: TasksWidget): TasksWidget = widget.copy(items = widget.items.map(TaskItem::normalized))

    private fun mergeImportedItems(imported: List<TaskItem>, storedItems: List<TaskItem>): List<TaskItem> {
        if (imported.isEmpty()) return emptyList()
        val overridesById = storedItems.associateBy { it.id }
        return imported.map { importedItem ->
            val override = overridesById[importedItem.id]
            if (override == null) {
                importedItem.normalized()
            } else {
                importedItem.withCompleted(override.isDone).normalized()
            }
        }
    }

    fun resetLayout() {
        viewModelScope.launch {
            settingsRepository.update {
                clockAnchorX = com.sconcept.mirrordash.settings.DEFAULT_CLOCK_ANCHOR_X
                clockAnchorY = com.sconcept.mirrordash.settings.DEFAULT_CLOCK_ANCHOR_Y
                weatherAnchorX = com.sconcept.mirrordash.settings.DEFAULT_WEATHER_ANCHOR_X
                weatherAnchorY = com.sconcept.mirrordash.settings.DEFAULT_WEATHER_ANCHOR_Y
            }
        }
    }

    companion object {
        fun factory(application: Application, settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    ClockViewModel(application, settingsRepository) as T
            }
    }
}
