package com.joanpa.smsaviso.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.data.GroupWithCount
import com.joanpa.smsaviso.data.Recipient
import com.joanpa.smsaviso.data.SmsGroup
import com.joanpa.smsaviso.databinding.ActivityGroupsBinding
import com.joanpa.smsaviso.util.phoneKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupsActivity : AppCompatActivity() {

    private lateinit var b: ActivityGroupsBinding
    private lateinit var adapter: GroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = GroupAdapter { showGroupActions(it) }
        b.rvGroups.layoutManager = LinearLayoutManager(this)
        b.rvGroups.adapter = adapter

        b.btnNewGroup.setOnClickListener { promptNewGroup() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(this@GroupsActivity).groupDao().observeGroupsWithCount()
                    .collectLatest { list ->
                        adapter.submit(list)
                        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun editText(hint: String, preset: String = ""): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(preset)
            setSingleLine()
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

    private fun promptNewGroup() {
        val input = editText("Nombre del grupo")
        AlertDialog.Builder(this)
            .setTitle("Nuevo grupo")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val id = withContext(Dispatchers.IO) {
                        AppDatabase.get(this@GroupsActivity).groupDao()
                            .insertGroup(SmsGroup(name = name))
                    }
                    askAddContactsNow(id)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun askAddContactsNow(groupId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Grupo creado")
            .setMessage("¿Agregar contactos al grupo ahora?")
            .setPositiveButton("Sí") { _, _ -> openContactPicker(groupId) }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun openContactPicker(groupId: Long) {
        startActivity(
            Intent(this, ContactsActivity::class.java)
                .putExtra(ContactsActivity.EXTRA_GROUP_ID, groupId)
        )
    }

    private fun showGroupActions(g: GroupWithCount) {
        val options = arrayOf(
            "Cargar en la cola de envío",
            "Agregar contactos",
            "Vaciar contactos del grupo",
            "Renombrar grupo",
            "Eliminar grupo"
        )
        AlertDialog.Builder(this)
            .setTitle(g.group.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> loadIntoQueue(g)
                    1 -> openContactPicker(g.group.id)
                    2 -> confirmClearMembers(g)
                    3 -> promptRename(g)
                    4 -> confirmDelete(g)
                }
            }
            .show()
    }

    private fun loadIntoQueue(g: GroupWithCount) {
        lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(this@GroupsActivity)
                val existing = db.recipientDao().allPhones().map { it.phoneKey() }.toMutableSet()
                val members = db.groupDao().membersOf(g.group.id)
                val toInsert = members
                    .filter { existing.add(it.phone.phoneKey()) }
                    .map { Recipient(name = it.name, phone = it.phone) }
                db.recipientDao().insertAll(toInsert)
                toInsert.size
            }
            Toast.makeText(
                this@GroupsActivity,
                "Agregados $added a la cola (duplicados omitidos)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmClearMembers(g: GroupWithCount) {
        AlertDialog.Builder(this)
            .setTitle("Vaciar “${g.group.name}”")
            .setMessage("Se quitan todos los contactos del grupo. El grupo no se borra.")
            .setPositiveButton("Vaciar") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(this@GroupsActivity).groupDao().clearMembers(g.group.id)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptRename(g: GroupWithCount) {
        val input = editText("Nombre del grupo", g.group.name)
        AlertDialog.Builder(this)
            .setTitle("Renombrar grupo")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(this@GroupsActivity).groupDao().renameGroup(g.group.id, name)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDelete(g: GroupWithCount) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar “${g.group.name}”")
            .setMessage("Se borra el grupo y su lista de contactos. No afecta a la cola de envío ni al historial.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(this@GroupsActivity).groupDao().deleteGroup(g.group.id)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
