package com.demo.butler_voice_app.ui

// ── Auth result codes ─────────────────────────────────────────────────────────
const val RESULT_MANUAL_LOGIN  = 200
const val RESULT_USE_VOICE     = 201
const val RESULT_GOOGLE_AUTH   = 202

// ── Payment result codes ──────────────────────────────────────────────────────
const val RESULT_PAY_CARD      = 100
const val RESULT_PAY_QR        = 101
const val RESULT_PAY_CANCEL    = 102
const val RESULT_CARD_SAVED    = 103

// ── QR result codes ───────────────────────────────────────────────────────────
const val RESULT_QR_PAID       = 110
const val RESULT_QR_CANCEL     = 111

// ── Auth extras ───────────────────────────────────────────────────────────────
const val EXTRA_EMAIL          = "email"
const val EXTRA_PASSWORD       = "password"
const val EXTRA_NAME           = "extra_name"
const val EXTRA_IS_NEW_USER    = "is_new_user"
const val EXTRA_GOOGLE_TOKEN   = "extra_google_token"
const val EXTRA_GOOGLE_EMAIL   = "extra_google_email"
const val EXTRA_GOOGLE_NAME    = "extra_google_name"

// ── Payment extras ────────────────────────────────────────────────────────────
const val EXTRA_ORDER_TOTAL    = "order_total"
const val EXTRA_ORDER_SUMMARY  = "order_summary"