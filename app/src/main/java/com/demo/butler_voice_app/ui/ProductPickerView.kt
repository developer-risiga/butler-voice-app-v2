package com.demo.butler_voice_app.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.demo.butler_voice_app.api.ProductRecommendation


class ProductPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val cards = mutableListOf<View>()
    private var recs = listOf<ProductRecommendation>()
    private var onSelected: ((ProductRecommendation) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setPadding(8, 8, 8, 8)
    }

    /**
     * Show the 3 recommendation cards.
     * Call this after SmartProductRepository.getTopRecommendations() returns.
     */
    fun showRecommendations(
        recommendations: List<ProductRecommendation>,
        onSelected: (ProductRecommendation) -> Unit
    ) {
        this.recs = recommendations
        this.onSelected = onSelected
        removeAllViews()
        cards.clear()

        recommendations.forEachIndexed { index, rec ->
            val card = buildCard(rec, isBest = index == 0)
            addView(card)
            cards.add(card)
        }

        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(250).start()
    }

    /**
     * Call this when STT returns a confirmation word.
     * Returns true if a match was found and selected.
     */
    fun matchVoice(spokenWord: String): Boolean {
        val lower = spokenWord.lowercase()
        val match = recs.firstOrNull {
            it.productName.lowercase().contains(lower) ||
                    it.storeName.lowercase().contains(lower)
        } ?: return false

        onSelected?.invoke(match)
        hide()
        return true
    }

    fun hide() {
        animate().alpha(0f).setDuration(200).withEndAction {
            visibility = View.GONE
            removeAllViews()
            cards.clear()
        }.start()
    }

    // ── Card builder ─────────────────────────────────────────────

    private fun buildCard(rec: ProductRecommendation, isBest: Boolean): View {
        // Build card programmatically — no XML needed
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelected?.invoke(rec) }
        }

        // Best value badge
        if (isBest) {
            card.addView(TextView(context).apply {
                text = "Best value"
                textSize = 10f
                setPadding(0, 0, 0, 4)
                setTextColor(0xFF0F6E56.toInt())
            })
        }

        // Product name
        card.addView(TextView(context).apply {
            text = rec.productName
            textSize = 12f
            setTextColor(0xFF212121.toInt())
            maxLines = 2
        })

        // Unit
        card.addView(TextView(context).apply {
            text = rec.unit
            textSize = 10f
            setTextColor(0xFF757575.toInt())
            setPadding(0, 2, 0, 4)
        })

        // Price — big and bold
        card.addView(TextView(context).apply {
            text = rec.priceLabel
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1976D2.toInt())
        })

        // Store + distance
        card.addView(TextView(context).apply {
            text = "${rec.storeName}\n${rec.distanceLabel}"
            textSize = 10f
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(0, 4, 0, 6)
        })

        // Voice hint
        card.addView(TextView(context).apply {
            text = "Say \"${rec.voiceShortcut}\""
            textSize = 10f
            setTextColor(0xFF534AB7.toInt())
            setTypeface(null, android.graphics.Typeface.ITALIC)
        })

        return card
    }
}