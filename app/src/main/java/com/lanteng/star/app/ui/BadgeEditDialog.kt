package com.lanteng.star.app.ui

import android.app.DatePickerDialog
import android.text.InputType
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Badge
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.util.Dates
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * Create/edit form for one badge. Categories can be extended inline, which is
 * why there is no separate category-management screen.
 */
object BadgeEditDialog {

    private const val NEW_CATEGORY = "\uFF0B \u65B0\u5EFA\u5206\u7C7B\u2026"

    fun show(activity: AppCompatActivity, existing: Badge?, onSaved: (Badge) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_badge_edit, null)

        val nameInput = view.findViewById<TextInputEditText>(R.id.et_name)
        val notesInput = view.findViewById<TextInputEditText>(R.id.et_notes)
        val categorySpinner = view.findViewById<Spinner>(R.id.spinner_category)
        val accountSpinner = view.findViewById<Spinner>(R.id.spinner_account)
        val dateButton = view.findViewById<Button>(R.id.btn_date)

        // ---- accounts: placeholder + one entry per account
        val accountIds = mutableListOf<String?>(null)
        val accountLabels = mutableListOf(activity.getString(R.string.badge_account) + ": \u2014")
        Store.accounts.forEach {
            accountIds.add(it.id)
            accountLabels.add(it.name)
        }
        accountSpinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            accountLabels,
        )
        existing?.accountId?.let { id ->
            val index = accountIds.indexOf(id)
            if (index >= 0) accountSpinner.setSelection(index)
        }

        // ---- categories: existing set + an inline "new category" escape hatch
        val categoryValues = mutableListOf<String>()
        val categoryLabels = mutableListOf<String>()
        Store.categories.forEach {
            categoryValues.add(it)
            categoryLabels.add(it)
        }
        val newCategoryIndex = categoryLabels.size
        categoryLabels.add(NEW_CATEGORY)
        categorySpinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            categoryLabels,
        )
        existing?.category?.let { category ->
            val index = categoryValues.indexOf(category)
            if (index >= 0) categorySpinner.setSelection(index)
        }

        var chosenCategory: String = existing?.category ?: categoryValues.firstOrNull().orEmpty()
        categorySpinner.onItemSelectedListener = SimpleItemSelected { position ->
            if (position == newCategoryIndex) {
                promptForCategory(activity) { typed ->
                    chosenCategory = typed
                }
            } else if (position in categoryValues.indices) {
                chosenCategory = categoryValues[position]
            }
        }

        // ---- acquisition date
        var acquiredAt: Long? = existing?.acquiredAt
        fun renderDate() {
            val text = Dates.day(acquiredAt)
            dateButton.text = text.ifEmpty { activity.getString(R.string.badge_acquired_at) }
        }
        renderDate()
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            acquiredAt?.let { calendar.timeInMillis = it }
            DatePickerDialog(
                activity,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    picked.set(Calendar.MILLISECOND, 0)
                    acquiredAt = picked.timeInMillis
                    renderDate()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        // ---- prefill
        nameInput.setText(existing?.name.orEmpty())
        notesInput.setText(existing?.notes.orEmpty())

        MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.fab_add else R.string.action_edit)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val badge = existing?.copy() ?: Badge(
                    id = Store.newId(),
                    name = "",
                    category = "",
                    accountId = "",
                    acquiredAt = null,
                    notes = "",
                    imageUri = null,
                    tag = null,
                )
                badge.name = nameInput.text?.toString()?.trim().orEmpty()
                badge.notes = notesInput.text?.toString()?.trim().orEmpty()
                badge.category = chosenCategory
                badge.accountId = accountIds[accountSpinner.selectedItemPosition] ?: ""
                badge.acquiredAt = acquiredAt
                Store.upsert(badge)
                onSaved(badge)
            }
            .show()
    }

    private fun promptForCategory(activity: AppCompatActivity, onTyped: (String) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = activity.getString(R.string.badge_category)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.category_title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                onTyped(input.text.toString().trim())
            }
            .show()
    }

    /** Small helper so callers do not have to implement the whole listener. */
    class SimpleItemSelected(private val onSelected: (Int) -> Unit) :
        android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: android.view.View?,
            position: Int,
            id: Long,
        ) = onSelected(position)

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
