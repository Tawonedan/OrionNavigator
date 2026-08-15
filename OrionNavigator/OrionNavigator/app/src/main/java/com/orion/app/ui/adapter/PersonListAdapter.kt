package com.orion.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orion.app.R
import com.orion.app.face.data.PersonRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersonListAdapter(
    private var persons: List<PersonRecord>,
    private val onDeleteClick: (PersonRecord) -> Unit,
) : RecyclerView.Adapter<PersonListAdapter.PersonViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    inner class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPersonName: TextView = itemView.findViewById(R.id.tvPersonName)
        val tvPersonDate: TextView = itemView.findViewById(R.id.tvPersonDate)
        val btnDeletePerson: ImageButton = itemView.findViewById(R.id.btnDeletePerson)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_card, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = persons[position]
        holder.tvPersonName.text = person.personName
        val dateStr = if (person.addTime > 0) {
            "Terdaftar pada " + dateFormat.format(Date(person.addTime))
        } else {
            "Terdaftar"
        }
        holder.tvPersonDate.text = dateStr

        holder.btnDeletePerson.setOnClickListener {
            onDeleteClick(person)
        }
    }

    override fun getItemCount(): Int = persons.size

    fun updateData(newPersons: List<PersonRecord>) {
        this.persons = newPersons
        notifyDataSetChanged()
    }
}
