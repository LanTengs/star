package com.lanteng.star.app.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Account
import com.lanteng.star.app.data.Store
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AccountsActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val adapter = AccountAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        empty = findViewById(R.id.tv_empty)
        list = findViewById<RecyclerView>(R.id.list_accounts)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        findViewById<FloatingActionButton>(R.id.fab_add_account).setOnClickListener {
            promptForName(null)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(Store.accounts.toList())
        empty.visibility = if (Store.accounts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun promptForName(existing: Account?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.account_name)
            setText(existing?.name.orEmpty())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.accounts_add else R.string.action_edit)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (existing == null) Store.addAccount(name)
                else Store.renameAccount(existing.id, name)
                refresh()
            }
            .show()
    }

    private fun confirmDelete(account: Account) {
        if (Store.badgeCountFor(account.id) > 0) {
            Toast.makeText(this, R.string.account_delete_blocked, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(account.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                Store.deleteAccount(account.id)
                refresh()
            }
            .show()
    }

    private inner class AccountAdapter : RecyclerView.Adapter<AccountAdapter.Holder>() {

        private val items = mutableListOf<Account>()

        fun submit(accounts: List<Account>) {
            items.clear()
            items.addAll(accounts)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_account, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.tv_name)
            private val count: TextView = view.findViewById(R.id.tv_count)
            private val rename: ImageButton = view.findViewById(R.id.btn_rename)
            private val delete: ImageButton = view.findViewById(R.id.btn_delete)

            fun bind(account: Account) {
                name.text = account.name
                count.text = Store.badgeCountFor(account.id).toString()
                rename.setOnClickListener { promptForName(account) }
                delete.setOnClickListener { confirmDelete(account) }
            }
        }
    }
}
