package Views.student.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.TutorAdapter
import Views.student.TutorProfileActivity
import com.google.firebase.firestore.FirebaseFirestore
import models.Tutor

class TutorsFragment : Fragment() {

    private lateinit var recyclerTutors: RecyclerView
    private lateinit var shimmerContainer: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private val tutorsList = mutableListOf<Tutor>()
    private lateinit var adapter: TutorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutors, container, false)

        recyclerTutors = view.findViewById(R.id.recyclerTutors)
        shimmerContainer = view.findViewById(R.id.shimmerContainer)

        recyclerTutors.layoutManager = LinearLayoutManager(requireContext())
        adapter = TutorAdapter(tutorsList) { selectedTutor ->
            openTutorProfile(selectedTutor)
        }
        recyclerTutors.adapter = adapter

        // Start shimmer loading
        showShimmer(true, animated = false)
        fetchTutors()

        return view
    }

    private fun fetchTutors() {
        db.collection("Tutors").get()
            .addOnSuccessListener { tutorDocs ->
                tutorsList.clear()
                for (doc in tutorDocs.documents) {
                    val rawImage = doc.getString("ProfileImageBase64") ?: ""
                    val tutor = Tutor(
                        UserId = doc.getString("UserId") ?: "",
                        Name = doc.getString("Name") ?: "",
                        Surname = doc.getString("Surname") ?: "",
                        Email = doc.getString("Email") ?: "",
                        PhoneNumber = doc.getString("PhoneNumber") ?: "",
                        Expertise = doc.getString("Expertise") ?: "",
                        Qualifications = doc.getString("Qualifications") ?: "",
                        ProfileImageBase64 = rawImage,
                        Description = doc.getString("Description") ?: "No description provided.",
                        AverageRating = doc.getDouble("AverageRating") ?: 0.0
                    )
                    tutorsList.add(tutor)
                }

                adapter.notifyDataSetChanged()
                showShimmer(false, animated = true)
            }
            .addOnFailureListener {
                Log.e("TutorsFragment", "❌ Failed to fetch tutors: ${it.message}", it)
                showShimmer(false, animated = true)
            }
    }

    private fun openTutorProfile(tutor: Tutor) {
        val intent = Intent(requireContext(), TutorProfileActivity::class.java).apply {
            putExtra("tutorId", tutor.UserId)
            putExtra("tutorName", "${tutor.Name} ${tutor.Surname}")
            putExtra("tutorEmail", tutor.Email)
            putExtra("tutorPhone", tutor.PhoneNumber)
            putExtra("tutorExpertise", tutor.Expertise)
            putExtra("tutorQualifications", tutor.Qualifications)
            putExtra("tutorDescription", tutor.Description)
            putExtra("tutorRating", tutor.AverageRating)
        }
        startActivity(intent)
    }

    private fun showShimmer(show: Boolean, animated: Boolean) {
        if (show) {
            shimmerContainer.visibility = View.VISIBLE
            recyclerTutors.visibility = View.GONE
        } else {
            if (animated) {
                // Fade out shimmer
                val fadeOut = AlphaAnimation(1f, 0f).apply {
                    duration = 300
                    fillAfter = true
                }
                shimmerContainer.startAnimation(fadeOut)

                // Fade in recycler
                recyclerTutors.alpha = 0f
                recyclerTutors.visibility = View.VISIBLE
                recyclerTutors.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .start()

                shimmerContainer.postDelayed({
                    shimmerContainer.visibility = View.GONE
                }, 350)
            } else {
                shimmerContainer.visibility = View.GONE
                recyclerTutors.visibility = View.VISIBLE
            }
        }
    }
}
