package dev.omalauncher

/** Android can reuse user IDs, but a removed profile's serial never targets its replacement. */
internal fun sameAppProfile(profile: Long?, currentSerial: Long) = currentSerial >= 0 && (profile == null || profile == currentSerial)
