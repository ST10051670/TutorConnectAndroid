package com.example.tutorconnect.Views.admin

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R

class AdminDashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        val textView = findViewById<TextView>(R.id.tvDashboard)
        textView.text = "Welcome to the Admin Dashboard"
    }
}
