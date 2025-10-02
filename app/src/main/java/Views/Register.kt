package com.example.tutorconnect.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R
import com.example.tutorconnect.models.Register
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Register : AppCompatActivity() {

    private lateinit var nameEt: EditText
    private lateinit var surnameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var roleSpinner: Spinner
    private lateinit var qualificationsEt: EditText
    private lateinit var expertiseEt: EditText
    private lateinit var registerBtn: Button
    private lateinit var errorTv: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameEt = findViewById(R.id.etName)
        surnameEt = findViewById(R.id.etSurname)
        phoneEt = findViewById(R.id.etPhone)
        emailEt = findViewById(R.id.etEmail)
        passwordEt = findViewById(R.id.etPassword)
        roleSpinner = findViewById(R.id.spinnerRole)
        qualificationsEt = findViewById(R.id.etQualifications)
        expertiseEt = findViewById(R.id.etExpertise)
        registerBtn = findViewById(R.id.btnRegister)
        errorTv = findViewById(R.id.tvError)

        // Show/hide tutor-specific fields
        roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val role = roleSpinner.selectedItem.toString()
                if (role == "Tutor") {
                    qualificationsEt.visibility = View.VISIBLE
                    expertiseEt.visibility = View.VISIBLE
                } else {
                    qualificationsEt.visibility = View.GONE
                    expertiseEt.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        registerBtn.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val user = Register(
            name = nameEt.text.toString().trim(),
            surname = surnameEt.text.toString().trim(),
            phoneNumber = phoneEt.text.toString().trim(),
            email = emailEt.text.toString().trim(),
            password = passwordEt.text.toString(),
            role = roleSpinner.selectedItem.toString(),
            qualifications = qualificationsEt.text.toString().takeIf { it.isNotEmpty() },
            expertise = expertiseEt.text.toString().takeIf { it.isNotEmpty() }
        )

        if (user.email.isBlank() || user.password.isBlank()) {
            errorTv.text = "Email and password are required."
            return
        }

        // 1️⃣ Create user in Firebase Authentication
        auth.createUserWithEmailAndPassword(user.email, user.password)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val uid = authTask.result?.user?.uid
                    if (uid != null) {
                        user.userId = uid
                        saveUserToFirestore(user)
                    }
                } else {
                    errorTv.text = "Registration failed: ${authTask.exception?.message}"
                }
            }
    }

    private fun saveUserToFirestore(user: Register) {
        val collection = if (user.role == "Tutor") "Tutors" else "Students"

        val userMap = hashMapOf(
            "UserId" to user.userId,
            "Name" to user.name,
            "Surname" to user.surname,
            "Email" to user.email,
            "PhoneNumber" to user.phoneNumber,
            "Role" to user.role,
            "Qualifications" to user.qualifications,
            "Expertise" to user.expertise
        )

        db.collection(collection).document(user.userId!!)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Registered successfully!", Toast.LENGTH_LONG).show()
                navigateToDashboard(user.role)
            }
            .addOnFailureListener { e ->
                errorTv.text = "Firestore error: ${e.message}"
            }
    }

    private fun navigateToDashboard(role: String) {
        val intent = when (role) {
            "Tutor" -> Intent(this, com.example.tutorconnect.Views.tutor.TutorDashboard::class.java)
            "Student" -> Intent(this, com.example.tutorconnect.Views.student.StudentDashboard::class.java)
            "Admin" -> Intent(this, com.example.tutorconnect.Views.admin.AdminDashboard::class.java)
            else -> null
        }

        intent?.let {
            startActivity(it)
            finish()
        }
    }
}
