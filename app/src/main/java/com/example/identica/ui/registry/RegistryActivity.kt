package com.example.identica.ui.registry

import android.graphics.BitmapFactory
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
        personList.removeAllViews()
        adminList.removeAllViews()
        persons.forEach { personList.addView(personCard(it)) }
        admins.forEach { adminList.addView(adminCard(it)) }
        if (persons.isEmpty()) personList.addView(emptyText("NO REGISTERED IDENTITIES"))
        if (admins.isEmpty()) adminList.addView(emptyText("NO SYSTEM OPERATORS"))
    }

    private fun personCard(person: RegisteredPerson): LinearLayout = cardContainer().apply {
        addView(photo(person.photoPath))
        addView(
            details(
                person.fullName,
                "Age ${person.age}  ·  ${person.sex}\nID ${person.nationalIdMasked}\n${person.documentDetails}"
            )
        )
    }

    private fun adminCard(admin: Admin): LinearLayout = cardContainer().apply {
        addView(initialAvatar(admin.username))
        addView(details(admin.username, "System operator\nRegistered ${date(admin.createdAt)}"))
    }

    private fun cardContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = getDrawable(R.drawable.bg_neon_panel)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
    }

    private fun photo(path: String) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply {
            rightMargin = dp(14)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
        val file = File(path)
        if (file.exists()) {
            setImageBitmap(BitmapFactory.decodeFile(path))
        } else {
            setImageResource(R.mipmap.ic_launcher_foreground)
        }
        contentDescription = "Identity portrait"
    }

    private fun initialAvatar(name: String) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply {
            rightMargin = dp(14)
        }
        gravity = Gravity.CENTER
        text = name.take(2).uppercase()
        setTextAppearance(R.style.Identica_Title)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(getColor(R.color.matrix_charcoal_light))
            setStroke(dp(1), getColor(R.color.panel_border))
        }
    }

    private fun details(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        addView(TextView(this@RegistryActivity).apply {
            text = title
            setTextAppearance(R.style.Identica_Title)
        })
        addView(TextView(this@RegistryActivity).apply {
            text = body
            setTextAppearance(R.style.Identica_Body)
            setPadding(0, dp(5), 0, 0)
        })
    }

    private fun emptyText(text: String) = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.Identica_Body)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun date(value: Long) =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(value))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
