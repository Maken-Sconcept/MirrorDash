package com.sconcept.mirrordash.launcher

import android.content.Context
import com.sconcept.mirrordash.airplay.AirPlayEngine
import com.sconcept.mirrordash.iptv.IptvRecordingEngine
import com.sconcept.mirrordash.iptv.IptvSessionCoordinator
import com.sconcept.mirrordash.mirrordrop.MirrorDropEngine
import com.sconcept.mirrordash.mirrordrop.MirrorDropPhotoboothRepositorySource
import com.sconcept.mirrordash.photobooth.PhotoboothRepository
import com.sconcept.mirrordash.settings.SettingsRepository
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieEngine
import com.sconcept.mirrordash.wedding.seating.WeddingSeatingRepository

/**
 * Hand-rolled composition root (the project intentionally has no DI framework - see the plan's
 * "no DI framework" note, matching BerthierOptions' own approach of plain classes constructed
 * where needed). Holds the handful of app-scoped singletons that outlive any single screen.
 */
class AppContainer private constructor(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val walkieTalkieEngine by lazy { WalkieTalkieEngine.get(context, settingsRepository) }
    val airPlayEngine by lazy { AirPlayEngine.get(context, settingsRepository) }
    val iptvSessionCoordinator by lazy { IptvSessionCoordinator.get() }
    val iptvRecordingEngine by lazy { IptvRecordingEngine.get(context, settingsRepository, iptvSessionCoordinator) }
    val photoboothRepository by lazy { PhotoboothRepository(context) }
    val mirrorDropEngine by lazy {
        MirrorDropEngine.get(context, settingsRepository, MirrorDropPhotoboothRepositorySource(photoboothRepository))
    }
    val weddingSeatingRepository by lazy { WeddingSeatingRepository(context) }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
