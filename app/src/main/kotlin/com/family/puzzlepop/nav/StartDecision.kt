package com.family.puzzlepop.nav

enum class Start { SETUP, LIBRARY, KID }

/**
 * Where the app opens. No PIN anywhere: PIN setup, except when a PIN demonstrably existed or a corrupt
 * settings file was detected AND the library has photos; then kid mode, so the library is never open
 * unprotected (a stage-1 upgrade, which never had a PIN, has neither marker and gets PIN setup).
 */
fun startDecision(
    pinPresent: Boolean,
    pinEverSet: Boolean,
    corruptionDetected: Boolean,
    hasPhotos: Boolean,
    kidModeActive: Boolean,
    alwaysKid: Boolean,
): Start = when {
    !pinPresent -> if ((pinEverSet || corruptionDetected) && hasPhotos) Start.KID else Start.SETUP
    kidModeActive || alwaysKid -> Start.KID
    else -> Start.LIBRARY
}
