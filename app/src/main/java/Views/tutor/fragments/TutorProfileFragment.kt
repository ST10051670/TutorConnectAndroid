package com.example.tutorconnect.Views.tutor

import Views.tutor.AvailabilityActivity
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.tutorconnect.R
import com.example.tutorconnect.ui.Login
import com.example.tutorconnect.utils.encodeImageToBase64
import com.example.tutorconnect.utils.loadProfileImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TutorProfileFragment : Fragment() {

    private lateinit var imgProfile: ImageView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtPhone: TextView
    private lateinit var txtExpertise: TextView
    private lateinit var txtQualifications: TextView
    private lateinit var btnChangeImage: Button
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button
    private lateinit var btnLogHours: Button
    private lateinit var btnManageSlots: Button // ✅ Restored

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedImageUri: Uri? = null
    private var base64Image: String? = null
    private var imageChanged = false

    companion object {
        private const val PICK_IMAGE_REQUEST = 2001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // UI Components
        imgProfile = view.findViewById(R.id.imgTutorProfile)
        txtName = view.findViewById(R.id.txtTutorName)
        txtEmail = view.findViewById(R.id.txtTutorEmail)
        txtPhone = view.findViewById(R.id.txtTutorPhone)
        txtExpertise = view.findViewById(R.id.txtTutorExpertise)
        txtQualifications = view.findViewById(R.id.txtTutorQualifications)
        btnChangeImage = view.findViewById(R.id.btnChangeImage)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnLogHours = view.findViewById(R.id.btnLogHours)
        btnManageSlots = view.findViewById(R.id.btnManageSlots)

        loadTutorProfile()

        btnChangeImage.setOnClickListener { openImagePicker() }
        imgProfile.setOnClickListener { openImagePicker() }

        // Save updated image
        btnSave.setOnClickListener { saveTutorProfile() }

        // Manage Weekly Slots
        btnManageSlots.setOnClickListener {
            val intent = Intent(requireContext(), AvailabilityActivity::class.java)
            startActivity(intent)
        }

        // Show Tutor Summary (hours + ratings)
        btnLogHours.setOnClickListener {
            lifecycleScope.launch {
                showTutorSummary()
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            selectedImageUri?.let {
                lifecycleScope.launch {
                    try {
                        base64Image = encodeImageToBase64(requireContext(), it)
                        imgProfile.loadProfileImage(base64Image)
                        imageChanged = true
                    } catch (e: Exception) {
                        Log.e("TutorProfileFragment", "Error encoding image: ${e.message}")
                        Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadTutorProfile() {
        val tutorId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val doc = db.collection("Tutors").document(tutorId).get().await()
                if (doc.exists()) {
                    val name = doc.getString("Name") ?: ""
                    val surname = doc.getString("Surname") ?: ""
                    val email = doc.getString("Email") ?: ""
                    val phone = doc.getString("PhoneNumber") ?: ""
                    val expertise = doc.getString("Expertise") ?: ""
                    val qualifications = doc.getString("Qualifications") ?: ""
                    val image = doc.getString("ProfileImageBase64")

                    txtName.text = "$name $surname"
                    txtEmail.text = "Email: $email"
                    txtPhone.text = "Phone: $phone"
                    txtExpertise.text = "Expertise: $expertise"
                    txtQualifications.text = "Qualifications: $qualifications"

                    if (!image.isNullOrEmpty()) {
                        imgProfile.loadProfileImage(image)
                        base64Image = image
                    } else {
                        imgProfile.setImageResource(R.drawable.ic_person)
                    }
                }
            } catch (e: Exception) {
                Log.e("TutorProfileFragment", "Error loading profile", e)
                Toast.makeText(requireContext(), "Error loading profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTutorProfile() {
        val tutorId = auth.currentUser?.uid ?: return
        if (!imageChanged) {
            Toast.makeText(requireContext(), "No new image selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                db.collection("Tutors").document(tutorId)
                    .update("ProfileImageBase64", base64Image)
                    .await()

                imageChanged = false
                Toast.makeText(requireContext(), "✅ Profile picture updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("TutorProfileFragment", "Error updating image", e)
                Toast.makeText(requireContext(), "❌ Failed to update image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun showTutorSummary() {
        val tutorId = auth.currentUser?.uid ?: return
        try {
            val profileDoc = db.collection("TutorProfiles").document(tutorId).get().await()
            val totalHours = profileDoc.getDouble("TotalHoursLogged") ?: 0.0
            val averageRating = profileDoc.getDouble("AverageRating") ?: 0.0

            val completedBookings = db.collection("Bookings")
                .whereEqualTo("TutorId", tutorId)
                .whereEqualTo("IsCompleted", true)
                .get().await()
                .size()

            val summary = """
                🕒 Total Hours Logged: ${String.format("%.1f", totalHours)}
                ✅ Completed Sessions: $completedBookings
                ⭐ Average Rating: ${String.format("%.1f", averageRating)}
            """.trimIndent()

            requireActivity().runOnUiThread {
                AlertDialog.Builder(requireContext())
                    .setTitle("Tutor Summary")
                    .setMessage(summary)
                    .setPositiveButton("OK", null)
                    .show()
            }

        } catch (e: Exception) {
            Log.e("TutorProfileFragment", "❌ Error fetching summary: ${e.message}", e)
            Toast.makeText(requireContext(), "Failed to load summary", Toast.LENGTH_SHORT).show()
        }
    }
}
