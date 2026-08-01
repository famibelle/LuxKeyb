package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Panneau emoji exhaustif (v10.1.0) : onglets de catégories en haut, pages
 * défilables latéralement (ViewPager2) en dessous, chaque page étant une
 * grille verticale virtualisée (RecyclerView) pour rester fluide même avec
 * ~1900 emojis chargés au total — sans virtualisation, ~1900 Button créés
 * d'un coup aurait un vrai coût mémoire/jank sur les téléphones bas de
 * gamme visés par ce projet (cf. commentaires Samsung ULTRA ailleurs dans
 * le code).
 *
 * Le swipe horizontal change de catégorie (ViewPager2) ; le swipe vertical
 * défile dans la catégorie courante (comportement par défaut d'un
 * RecyclerView/GridLayoutManager) : les deux gestes sont orthogonaux et
 * coexistent sans code de désambiguïsation supplémentaire.
 */
class EmojiPickerView(
    context: Context,
    private val accentHandler: AccentHandler?
) : LinearLayout(context) {

    companion object {
        private const val GRID_COLUMNS = 10
        private const val CELL_HEIGHT_DP = 44
        private const val VISIBLE_ROWS = 3
        private const val TAB_HEIGHT_DP = 40
        private const val EMOJI_TEXT_SIZE_SP = 20f
    }

    var onEmojiSelected: ((String) -> Unit)? = null

    private val emojiData = EmojiData.load(context)
    private val tabViews = mutableListOf<Button>()
    private lateinit var viewPager: ViewPager2

    init {
        orientation = VERTICAL
        accentHandler?.loadEmojiSkinTones(emojiData.skinTones)
        addView(createTabRow())
        addView(createPager())
    }

    private fun createTabRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(TAB_HEIGHT_DP))

            emojiData.categories.forEachIndexed { index, category ->
                val tab = Button(context).apply {
                    text = category.icon
                    isAllCaps = false
                    textSize = 16f
                    elevation = 0f
                    stateListAnimator = null
                    background = null
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener { viewPager.setCurrentItem(index, true) }
                }
                tabViews.add(tab)
                addView(tab)
            }
        }
    }

    private fun createPager(): ViewPager2 {
        viewPager = ViewPager2(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(VISIBLE_ROWS * CELL_HEIGHT_DP)
            )
            adapter = CategoryPagerAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabHighlight(position)
                }
            })
        }
        updateTabHighlight(0)
        return viewPager
    }

    private fun updateTabHighlight(selectedIndex: Int) {
        tabViews.forEachIndexed { index, tab ->
            tab.background = if (index == selectedIndex) {
                GradientDrawable().apply {
                    setColor(Color.parseColor("#E0F2E9"))
                    cornerRadius = dpToPx(6).toFloat()
                }
            } else {
                null
            }
        }
    }

    private inner class CategoryPagerAdapter : RecyclerView.Adapter<CategoryPagerAdapter.PageHolder>() {

        inner class PageHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val recyclerView = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = GridLayoutManager(parent.context, GRID_COLUMNS)
                setHasFixedSize(true)
            }
            return PageHolder(recyclerView)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.recyclerView.adapter = EmojiGridAdapter(emojiData.categories[position].emojis)
        }

        override fun getItemCount() = emojiData.categories.size
    }

    private inner class EmojiGridAdapter(
        private val emojis: List<String>
    ) : RecyclerView.Adapter<EmojiGridAdapter.EmojiHolder>() {

        inner class EmojiHolder(val button: Button) : RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiHolder {
            val button = Button(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(CELL_HEIGHT_DP)
                )
                isAllCaps = false
                textSize = EMOJI_TEXT_SIZE_SP
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.NORMAL)
                background = null
                elevation = 0f
                stateListAnimator = null
                setPadding(0, 0, 0, 0)
            }
            return EmojiHolder(button)
        }

        override fun onBindViewHolder(holder: EmojiHolder, position: Int) {
            val emoji = emojis[position]
            holder.button.text = emoji

            holder.button.setOnClickListener {
                onEmojiSelected?.invoke(emoji)
            }

            // Même double-délai (timeout natif Android + LONG_PRESS_DELAY
            // d'AccentHandler) que les autres touches à appui long du
            // clavier : cohérence de timing avec le reste de l'app.
            holder.button.setOnLongClickListener {
                if (accentHandler?.hasAccents(emoji) == true) {
                    accentHandler.startLongPressTimer(emoji, holder.button)
                }
                true
            }
            holder.button.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    accentHandler?.cancelLongPress()
                }
                false
            }
        }

        override fun getItemCount() = emojis.size
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
