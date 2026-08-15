package com.orion.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.orion.app.R
import com.orion.app.face.data.PersonRecord
import com.orion.app.face.domain.FaceRecognitionHelper
import com.orion.app.ui.adapter.PersonListAdapter
import kotlinx.coroutines.launch

class ManageFacesActivity : AppCompatActivity() {

    private lateinit var btnBack: View
    private lateinit var tvCount: TextView
    private lateinit var rvPersons: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnAddPerson: MaterialButton

    private lateinit var faceHelper: FaceRecognitionHelper
    private lateinit var adapter: PersonListAdapter

    private val addFaceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // PersonDB is observed via Flow in loadPersons(), but we can also trigger a re-check
    }

    companion object {
        const val EXTRA_OPEN_ADD_DIALOG = "EXTRA_OPEN_ADD_DIALOG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_faces)

        faceHelper = FaceRecognitionHelper(this)

        initViews()
        setupRecyclerView()
        loadPersons()

        if (intent.getBooleanExtra(EXTRA_OPEN_ADD_DIALOG, false)) {
            openAddFaceScreen()
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvCount = findViewById(R.id.tvCount)
        rvPersons = findViewById(R.id.rvPersons)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnAddPerson = findViewById(R.id.btnAddPerson)

        btnBack.setOnClickListener {
            finish()
        }

        btnAddPerson.setOnClickListener {
            openAddFaceScreen()
        }
    }

    private fun openAddFaceScreen() {
        val intent = Intent(this, AddFaceActivity::class.java)
        addFaceLauncher.launch(intent)
    }

    private fun setupRecyclerView() {
        adapter = PersonListAdapter(emptyList()) { person ->
            confirmDeletePerson(person)
        }
        rvPersons.layoutManager = LinearLayoutManager(this)
        rvPersons.adapter = adapter
    }

    private fun loadPersons() {
        lifecycleScope.launch {
            faceHelper.personDB.getAll().collect { persons ->
                updateUi(persons)
            }
        }
    }

    private fun updateUi(persons: List<PersonRecord>) {
        adapter.updateData(persons)
        tvCount.text = "${persons.size} Wajah Terdaftar"
        if (persons.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvPersons.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvPersons.visibility = View.VISIBLE
        }
    }

    private fun confirmDeletePerson(person: PersonRecord) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Wajah")
            .setMessage("Apakah Anda yakin ingin menghapus data wajah \"${person.personName}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    faceHelper.deletePerson(person.personID)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
