package com.sconcept.mirrordash.wedding

const val DEFAULT_WEDDING_IDLE_TIMEOUT_SECONDS = 120

data class WeddingProfile(
    val partnerOne: String = "",
    val partnerTwo: String = "",
    val dateText: String = "",
    val location: String = "",
    val welcomeMessage: String = "Welcome to our wedding",
    val idleTimeoutSeconds: Int = DEFAULT_WEDDING_IDLE_TIMEOUT_SECONDS,
) {
    val coupleDisplayName: String
        get() = listOf(partnerOne.trim(), partnerTwo.trim())
            .filter(String::isNotEmpty)
            .joinToString(" & ")
}
