package com.lanteng.star.app.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Badge
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.launch.BadgeLauncher
import com.lanteng.star.app.util.Dates
import com.lanteng.star.app.util.Prefs

class BadgeAdapter(
    private val prefs: Prefs,
    private val onClick: (Badge) -> Unit,
    private val onTrigger: (Badge) -> Unit,
) : RecyclerView.Adapter<BadgeAdapter.Holder>() {

    private val items = mutableListOf<Badge>()

    fun submit(list: List<Badge>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.badge_image)
        private val name: TextView = view.findViewById(R.id.tv_name)
        private val meta: TextView = view.findViewById(R.id.tv_meta)
        private val date: TextView = view.findViewById(R.id.tv_date)
        private val tagMark: ImageView = view.findViewById(R.id.iv_tag)
        private val cooldown: TextView = view.findViewById(R.id.tv_cooldown)
        private val trigger: ImageButton = view.findViewById(R.id.btn_trigger)

        fun bind(badge: Badge) {
            val context = itemView.context

            name.text = badge.name.ifBlank { context.getString(R.string.badge_name) }

            val parts = mutableListOf<String>()
            if (badge.category.isNotBlank()) parts.add(badge.category)
            val account = Store.accountName(badge.accountId)
            if (account.isNotBlank()) parts.add(account)
            meta.text = parts.joinToString(" \u00B7 ")
            meta.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE

            val acquired = Dates.day(badge.acquiredAt)
            date.text = acquired
            date.visibility = if (acquired.isEmpty()) View.GONE else View.VISIBLE

            tagMark.visibility = if (badge.tag != null) View.VISIBLE else View.GONE

            // --- delivery state
            val hasLink = !badge.badgeUrl.isNullOrBlank()
            val remaining = BadgeLauncher.cooldownRemainingMs(badge, prefs)
            val effect = BadgeLauncher.effectRemainingMs(badge, prefs)
            val ready = remaining == 0L

            trigger.visibility = if (hasLink) View.VISIBLE else View.GONE
            trigger.isEnabled = ready
            trigger.alpha = if (ready) 1f else 0.35f
            trigger.setOnClickListener { if (ready) onTrigger(badge) }

            if (!hasLink) {
                cooldown.visibility = View.GONE
            } else {
                cooldown.visibility = View.VISIBLE
                cooldown.text = when {
                    effect > 0 -> context.getString(
                        R.string.effect_active,
                        BadgeLauncher.format(effect),
                    )
                    remaining > 0 -> context.getString(
                        R.string.cooldown_waiting,
                        BadgeLauncher.format(remaining),
                    )
                    else -> context.getString(R.string.cooldown_ready)
                }
            }

            val uri = badge.imageUri
            if (uri.isNullOrBlank()) {
                image.setImageResource(R.drawable.ic_badge_placeholder)
            } else {
                runCatching { image.setImageURI(Uri.parse(uri)) }
                    .onFailure { image.setImageResource(R.drawable.ic_badge_placeholder) }
            }

            itemView.setOnClickListener { onClick(badge) }
        }
    }
}
