package com.lanteng.star.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.util.Dates
import com.google.android.material.appbar.MaterialToolbar

/** Local-only aggregates. Nothing is uploaded or compared against a server. */
class UsageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val badges = Store.badges
        findViewById<TextView>(R.id.tv_total).text = badges.size.toString()
        findViewById<TextView>(R.id.tv_with_tag).text =
            getString(R.string.usage_with_tag) + ": " + badges.count { it.tag != null }

        render(
            R.id.container_account,
            Store.accounts.map { it.name to Store.badgeCountFor(it.id) },
        )
        render(
            R.id.container_category,
            badges.groupingBy { it.category.ifBlank { "\u2014" } }
                .eachCount()
                .toList()
                .sortedByDescending { it.second },
        )
        render(
            R.id.container_year,
            badges.mapNotNull { it.acquiredAt }
                .groupingBy { Dates.yearOf(it).toString() }
                .eachCount()
                .toList()
                .sortedByDescending { it.first },
        )
    }

    private fun render(containerId: Int, rows: List<Pair<String, Int>>) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()

        if (rows.isEmpty()) {
            container.addView(
                LayoutInflater.from(this).inflate(R.layout.item_stat, container, false)
                    .apply {
                        findViewById<TextView>(R.id.tv_label).text = "\u2014"
                        findViewById<TextView>(R.id.tv_value).text = "0"
                    },
            )
            return
        }

        rows.forEach { (label, value) ->
            val row: View = LayoutInflater.from(this)
                .inflate(R.layout.item_stat, container, false)
            row.findViewById<TextView>(R.id.tv_label).text = label
            row.findViewById<TextView>(R.id.tv_value).text = value.toString()
            container.addView(row)
        }
    }
}
