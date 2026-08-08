package com.notekeep.local.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A note-card container whose size is driven only by its text content, never by the
 * intrinsic resolution of a background image.
 *
 * A plain FrameLayout does not work for this: when a `match_parent`-height ImageView
 * sits inside a `wrap_content` FrameLayout (as happens for every card in a RecyclerView
 * list, where the incoming height spec is UNSPECIFIED), the ImageView is still measured
 * against its own intrinsic bitmap size in the first measure pass -- and that size feeds
 * into the parent's final height. A large photo can then inflate the whole card far
 * beyond what the text needs, cropping oddly and making note cards inconsistent sizes.
 *
 * Any direct child whose `tag` equals [TAG_BACKGROUND] is excluded from the sizing pass
 * and is instead measured *after* the card's size is resolved from its other children,
 * with an exact spec matching that resolved size. It only ever fills the card; it can
 * never change the card's shape or size.
 */
class NoteCardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val TAG_BACKGROUND = "note_card_background"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxWidth = 0
        var maxHeight = 0

        // Pass 1: measure every non-background child normally; these decide the card's size.
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE || child.tag == TAG_BACKGROUND) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            maxWidth = maxOf(maxWidth, child.measuredWidth + lp.leftMargin + lp.rightMargin)
            maxHeight = maxOf(maxHeight, child.measuredHeight + lp.topMargin + lp.bottomMargin)
        }

        maxWidth = maxOf(maxWidth, suggestedMinimumWidth)
        maxHeight = maxOf(maxHeight, suggestedMinimumHeight)

        val resolvedWidth = resolveSize(maxWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(maxHeight, heightMeasureSpec)

        // Pass 2: force every background-tagged child to exactly fill the resolved size.
        val exactWidthSpec = MeasureSpec.makeMeasureSpec(resolvedWidth, MeasureSpec.EXACTLY)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE || child.tag != TAG_BACKGROUND) continue
            child.measure(exactWidthSpec, exactHeightSpec)
        }

        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }
}
