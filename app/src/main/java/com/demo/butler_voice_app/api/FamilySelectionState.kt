package com.demo.butler_voice_app.api

// Tracks where we are in the "who is speaking" selection flow
sealed class FamilySelectionState {
    object Idle           : FamilySelectionState()
    object Asking         : FamilySelectionState() // Butler asked "kaun bol raha hai?"
    object Listening      : FamilySelectionState() // waiting for name
    data class Confirmed(val member: FamilyMember) : FamilySelectionState()
    object NotRecognised  : FamilySelectionState() // said something unrecognised
}