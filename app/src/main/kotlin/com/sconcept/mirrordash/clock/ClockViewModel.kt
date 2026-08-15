package com.sconcept.mirrordash.clock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.CLOCK_BACKGROUND_MODE_PHOTORAMA
import com.sconcept.mirrordash.settings.SettingsRepository
import com.sconcept.mirrordash.ui.theme.DEFAULT_CLOCK_FONT_SIZE_SP
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClockAppearance(
    val fontSizeSp: Int = DEFAULT_CLOCK_FONT_SIZE_SP,
    val textColor: Color = Color.White,
    val background: ClockBackground = ClockBackground.SolidColor(Color.Black),
    val clockAnchor: OverlayAnchor = OverlayAnchor(0.06f, 0.82f),
    val weatherWidgets: List<WeatherWidget> = emptyList(),
    val textWidgets: List<CustomTextWidget> = emptyList(),
)

/**
 * Owns Clock appearance *and* the drag-to-position anchors for the clock/weather clusters
 * (brought back at the user's request after the initial fixed-layout design - see
 * ClockLayoutHelper's doc comment for how this avoids BerthierOptions' original bug: exactly
 * one stored anchor per element, exactly one default, defined once in [MirrorDashSettings]).
 */
class ClockViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val appearance: StateFlow<ClockAppearance> = settingsRepository.settings
        .map {
            ClockAppearance(
                fontSizeSp = it.clockFontSizeSp,
                textColor = Color(it.clockTextColorArgb),
                background = if (it.clockBackgroundMode == CLOCK_BACKGROUND_MODE_PHOTORAMA) {
                    ClockBackground.Photorama
                } else {
                    ClockBackground.SolidColor(Color(it.clockBackgroundColorArgb))
                },
                clockAnchor = OverlayAnchor(it.clockAnchorX, it.clockAnchorY),
                weatherWidgets = it.weatherWidgets,
                textWidgets = it.customTextWidgets,
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
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ClockViewModel(settingsRepository) as T
            }
    }
}
