package com.example.identica.ui.registry

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.identica.R
import com.example.identica.data.local.entity.Admin
import com.example.identica.data.local.entity.RegisteredPerson
import com.example.identica.ui.common.BaseSecureActivity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistryActivity : BaseSecureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registry)
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val persons = app.repository.getPersons()
        val admins = app.repository.getAdmins()
        findViewById<TextView>(R.id.registryCountText).text = "${persons.size} REGISTERED IDENTITIES"
        findViewById<TextView>(R.id.adminCountText).text = "${admins.size} SYSTEM OPERATORS"
        val personList = findViewById<LinearLayout>(R.id.personRegistryList)
        val adminList = findViewById<LinearLayout>(R.id.adminRegistryList)
        personList.removeAllViews(); adminList.removeAllViews()
        persons.forEach { personList.addView(personCard(it)) }
        admins.forEach { adminList.addView(adminCard(it)) }
        if (persons.isEmpty()) personList.addView(emptyText("NO REGISTERED IDENTITIES"))
        if (admins.isEmpty()) adminList.addView(emptyText("NO SYSTEM OPERATORS"))
    }

    private fun personCard(person: RegisteredPerson): LinearLayout = cardContainer().apply {
        addView(photo(person.photoPath))
        addView(details(person.fullName, "Age ${person.age} | ${person.sex.uppercase()}\nID ${person.nationalIdMasked}\n${person.documentDetails}"))
    }

    private fun adminCard(admin: Admin): LinearLayout = cardContainer().apply {
        addView(initialAvatar(admin.username))
        addView(details(admin.username.uppercase(), "SYSTEM OPERATOR\nREGISTERED ${date(admin.createdAt)}"))
    }

    private fun cardContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12,12,12,12)
        background = GradientDrawable().apply { setColor(Color.rgb(12,18,12)); setStroke(1, Color.rgb(0,255,65)); cornerRadius = 10f }
        layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,0,12) }
    }

    private fun photo(path: String) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(88,88).apply { rightMargin = 14 }
        scaleType = ImageView.ScaleType.CENTER_CROP
        val file = File(path); if (file.exists()) setImageBitmap(BitmapFactory.decodeFile(path)) else setImageResource(R.mipmap.ic_launcher_foreground)
        contentDescription = "Identity portrait"
    }

    private fun initialAvatar(name: String) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(88,88).apply { rightMargin = 14 }
        gravity = Gravity.CENTER; text = name.take(2).uppercase(); textSize = 24f; setTextColor(Color.rgb(0,255,255))
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(10,35,30)); setStroke(2, Color.rgb(0,255,255)) }
    }

    private fun details(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@RegistryActivity).apply { text=title; textSize=18f; setTextColor(Color.rgb(0,255,65)); typeface=Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) })
        addView(TextView(this@RegistryActivity).apply { text=body; textSize=12f; setTextColor(Color.rgb(156,204,156)); typeface=Typeface.MONOSPACE })
    }

    private fun emptyText(text: String) = TextView(this).apply { this.text=text; setTextColor(Color.rgb(156,204,156)); setPadding(8, 8, 8, 8); textSize=14f }
    private fun date(value: Long) = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(value))
}
