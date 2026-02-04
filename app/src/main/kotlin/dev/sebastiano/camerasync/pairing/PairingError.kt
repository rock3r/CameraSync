package dev.sebastiano.camerasync.pairing

/** Errors that can occur during pairing. */
enum class PairingError {
    /** Camera rejected the pairing request. */
    REJECTED,

    /** Connection timed out. */
    TIMEOUT,

    /** Unknown error. */
    UNKNOWN,
}
