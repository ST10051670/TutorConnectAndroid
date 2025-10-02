package com.example.tutorconnect.Views.student

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R

class StudentDashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val textView = findViewById<TextView>(R.id.tvDashboard)
        textView.text = "Welcome to the Student Dashboard"
    }
}
