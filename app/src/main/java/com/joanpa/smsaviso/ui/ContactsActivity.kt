package com.joanpa.smsaviso.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.data.GroupMember
import com.joanpa.smsaviso.data.Recipient
import com.joanpa.smsaviso.databinding.ActivityContactsBinding
import com.joanpa.smsaviso.util.ColombiaPhone
import com.joanpa.smsaviso.util.cleanPhone
import com.joanpa.smsaviso.util.phoneKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var b: ActivityContactsBinding
    private lateinit var adapter: ContactAdapter

    private val groupId: Long by lazy { intent.getLongExtra(EXTRA_GROUP_ID, -1L) }
    private val toGroup: Boolean get() = groupId > 0

    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadContacts() else {
            Toast.makeText(this, "Sin permiso de Contactos no se puede continuar", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(b.root)

        title = if (toGroup) "Agregar contactos al grupo" else "Elegir contactos"
        b.btnAdd.text = if (toGroup) "Agregar al grupo" else "Agregar a la cola"

        adapter = ContactAdapter { updateCount() }
        b.rvContacts.layoutManager = LinearLayoutManager(this)
        b.rvContacts.adapter = adapter

        b.etSearch.doAfterTextChanged { adapter.filter(it?.toString().orEmpty()) }
        b.cbSelectAll.setOnCheckedChangeListener { _, checked -> adapter.selectAllVisible(checked) }
        b.cbOnlyColombia.setOnCheckedChangeListener { _, _ -> loadContacts() }
        b.btnAdd.setOnClickListener { addSelected() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) loadContacts() else askPerm.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun updateCount() {
        b.tvSelCount.text = "${adapter.selectedKeys.size} elegidos"
    }

    private fun loadContacts() {
        val onlyColombia = b.cbOnlyColombia.isChecked
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { queryContacts(onlyColombia) }
            adapter.submit(rows)
            updateCount()
            if (rows.isEmpty()) {
                val msg = if (onlyColombia)
                    "Ningún contacto con celular de Colombia válido"
                else "No se encontraron contactos con número"
                Toast.makeText(this@ContactsActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun queryContacts(onlyColombia: Boolean): List<ContactRow> {
        val seen = HashSet<String>()
        val out = ArrayList<ContactRow>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { c ->
            val iName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val name = c.getString(iName)?.trim().orEmpty().ifBlank { "(sin nombre)" }
                val rawNum = c.getString(iNum)?.trim().orEmpty()
                if (rawNum.isEmpty()) continue

                val phone = if (onlyColombia) {
                    ColombiaPhone.normalizeMobile(rawNum) ?: continue
                } else {
                    rawNum.cleanPhone()
                }

                val dedupe = name + "|" + phone.phoneKey()
                if (seen.add(dedupe)) out.add(ContactRow(name, phone))
            }
        }
        return out
    }

    private fun addSelected() {
        val chosen = adapter.selectedRows()
        if (chosen.isEmpty()) {
            Toast.makeText(this, "Marca al menos un contacto", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(this@ContactsActivity)
                if (toGroup) {
                    val dao = db.groupDao()
                    val current = dao.memberPhones(groupId).map { it.phoneKey() }.toMutableSet()
                    val toInsert = chosen
                        .filter { current.add(it.phone.phoneKey()) }
                        .map { GroupMember(groupId = groupId, name = it.name, phone = it.phone) }
                    dao.insertMembers(toInsert)
                    toInsert.size
                } else {
                    val dao = db.recipientDao()
                    val current = dao.allPhones().map { it.phoneKey() }.toMutableSet()
                    val toInsert = chosen
                        .filter { current.add(it.phone.phoneKey()) }
                        .map { Recipient(name = it.name, phone = it.phone) }
                    dao.insertAll(toInsert)
                    toInsert.size
                }
            }
            val where = if (toGroup) "al grupo" else "a la cola"
            Toast.makeText(
                this@ContactsActivity,
                "Agregados $added $where (duplicados omitidos)",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "group_id"
    }
}
