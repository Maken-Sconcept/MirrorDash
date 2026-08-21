package com.sconcept.mirrordash.launcher

import android.content.Context
import com.sconcept.mirrordash.airplay.AirPlayEngine
import com.sconcept.mirrordash.gym.GymAdapterRegistry
import com.sconcept.mirrordash.gym.GymContentRepository
import com.sconcept.mirrordash.gym.GymRepository
import com.sconcept.mirrordash.gym.GymHealthConnectGateway
import com.sconcept.mirrordash.gym.GymSessionEngine
import com.sconcept.mirrordash.iptv.IptvRecordingEngine
import com.sconcept.mirrordash.iptv.IptvSessionCoordinator
import com.sconcept.mirrordash.settings.SettingsRepository
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieEngine

/**
 * Hand-rolled composition root (the project intentionally has no DI framework - see the plan's
 * "no DI framework" note, matching BerthierOptions' own approach of plain classes constructed
 * where needed). Holds the handful of app-scoped singletons that outlive any single screen.
 */
class AppContainer private constructor(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val gymRepository by lazy { GymRepository(settingsRepository) }
    val gymHealthConnectGateway by lazy { GymHealthConnectGateway(context) }
    val gymContentRepository by lazy { GymContentRepository(context, settingsRepository) }
    val gymAdapterRegistry by lazy { GymAdapterRegistry.createDefault(gymHealthConnectGateway) }
    val walkieTalkieEngine by lazy { WalkieTalkieEngine.get(context, settingsRepository) }
    val airPlayEngine by lazy { AirPlayEngine.get(context, settingsRepository) }
    val gymSessionEngine by lazy { GymSessionEngine.get(context, gymRepository, gymAdapterRegistry) }
    val iptvSessionCoordinator by lazy { IptvSessionCoordinator.get() }
    val iptvRecordingEngine by lazy { IptvRecordingEngine.get(context, settingsRepository, iptvSessionCoordinator) }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
