package Views.student

import Views.shared.ChatDetailActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tutorconnect.R
import com.example.tutorconnect.Views.student.TutorTimeSlotsActivity
import com.example.tutorconnect.utils.loadProfileImage
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

class TutorProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""

    private lateinit var tutorId: String
    private lateinit var tutorName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_profile)

        // Back navigation
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // UI Components
        val shimmerLayout = findViewById<ShimmerFrameLayout>(R.id.shimmerProfileLayout)
        val profileScrollView = findViewById<ScrollView>(R.id.profileScrollView)
        val imgTutorProfile = findViewById<ImageView>(R.id.imgTutorProfile)
        val txtTutorName = findViewById<TextView>(R.id.txtTutorName)
        val txtTutorEmail = findViewById<TextView>(R.id.txtTutorEmail)
        val txtTutorPhone = findViewById<TextView>(R.id.txtTutorPhone)
        val txtTutorExpertise = findViewById<TextView>(R.id.txtTutorExpertise)
        val txtTutorQualifications = findViewById<TextView>(R.id.txtTutorQualifications)
        val txtTutorDescription = findViewById<TextView>(R.id.txtTutorDescription)
        val txtTutorRating = findViewById<TextView>(R.id.txtTutorRating)
        val reviewsContainer = findViewById<LinearLayout>(R.id.reviewsContainer)
        val btnBookNow = findViewById<Button>(R.id.btnBookNow)
        val btnChatWithMe = findViewById<Button>(R.id.btnChatWithMe)

        // Intent Data
        tutorId = intent.getStringExtra("tutorId") ?: ""
        tutorName = intent.getStringExtra("tutorName") ?: "Unknown Tutor"

        if (tutorId.isEmpty()) {
            Log.e("TutorProfileActivity", "❌ tutorId missing from Intent!")
            finish()
            return
        }

        // Basic info setup
        txtTutorName.text = tutorName
        txtTutorEmail.text = intent.getStringExtra("tutorEmail") ?: "Email not available"
        txtTutorPhone.text = intent.getStringExtra("tutorPhone") ?: "Phone not available"
        txtTutorExpertise.text = "Expertise: ${intent.getStringExtra("tutorExpertise") ?: "N/A"}"
        txtTutorQualifications.text = "Qualifications: ${intent.getStringExtra("tutorQualifications") ?: "N/A"}"

        // Start shimmer while loading
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        profileScrollView.visibility = View.GONE

        lifecycleScope.launch {
            loadTutorProfile(imgTutorProfile, txtTutorDescription, txtTutorRating, reviewsContainer)
            fadeTransition(shimmerLayout, profileScrollView)
        }

        // Book Now Button
        btnBookNow.setOnClickListener {
            val intent = Intent(this, TutorTimeSlotsActivity::class.java)
            intent.putExtra("tutorId", tutorId)
            intent.putExtra("tutorName", tutorName)
            startActivity(intent)
        }

        // Chat Button
        btnChatWithMe.setOnClickListener {
            lifecycleScope.launch { openOrCreateChat() }
        }
    }

    private fun fadeTransition(shimmerLayout: ShimmerFrameLayout, contentView: View) {
        shimmerLayout.animate().alpha(0f).setDuration(300).withEndAction {
            shimmerLayout.stopShimmer()
            shimmerLayout.visibility = View.GONE
            contentView.alpha = 0f
            contentView.visibility = View.VISIBLE
            contentView.animate().alpha(1f).setDuration(400).start()
        }.start()
    }

    /**
     * Load Tutor info, Average Rating, and up to 3 recent reviews
     */
    private suspend fun loadTutorProfile(
        imgTutorProfile: ImageView,
        txtTutorDescription: TextView,
        txtTutorRating: TextView,
        reviewsContainer: LinearLayout
    ) {
        try {
            coroutineScope {
                val tutorDeferred = async {
                    db.collection("Tutors")
                        .whereEqualTo("UserId", tutorId)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                }

                val profileDeferred = async {
                    db.collection("TutorProfiles")
                        .whereEqualTo("UserId", tutorId)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                }

                val tutorDoc = tutorDeferred.await()
                val profileDoc = profileDeferred.await()

                if (tutorDoc != null) {
                    val imageBase64 = tutorDoc.getString("ProfileImageBase64")
                    val description = profileDoc?.getString("Description") ?: "No description available."
                    val averageRating = profileDoc?.getDouble("AverageRating") ?: 0.0
                    val reviews = profileDoc?.get("Reviews") as? List<Map<String, Any>>
                    val reviewsCount = reviews?.size ?: 0

                    lifecycleScope.launch {
                        imgTutorProfile.loadProfileImage(imageBase64)
                    }

                    txtTutorDescription.text = description
                    txtTutorRating.text = "⭐ ${String.format("%.1f", averageRating)} ($reviewsCount reviews)"

                    // 🗒️ Display last 3 reviews
                    reviewsContainer.removeAllViews()
                    reviews?.takeLast(3)?.forEach { review ->
                        val reviewView = layoutInflater.inflate(R.layout.item_review, reviewsContainer, false)
                        val txtReviewer = reviewView.findViewById<TextView>(R.id.txtReviewer)
                        val txtComment = reviewView.findViewById<TextView>(R.id.txtComment)
                        val ratingBar = reviewView.findViewById<RatingBar>(R.id.ratingBarReview)

                        txtReviewer.text = review["studentName"]?.toString() ?: "Anonymous"
                        txtComment.text = review["comment"]?.toString() ?: ""
                        ratingBar.rating = (review["rating"] as? Double)?.toFloat() ?: 0f

                        reviewsContainer.addView(reviewView)
                    }

                    Log.d("TutorProfileActivity", "✅ Loaded Tutor: $tutorId — avg=$averageRating, reviews=${reviewsCount}")
                } else {
                    txtTutorDescription.text = "Tutor information unavailable."
                    imgTutorProfile.setImageResource(R.drawable.ic_person)
                    txtTutorRating.text = "⭐ 0.0 (0 reviews)"
                }
            }
        } catch (e: Exception) {
            Log.e("TutorProfileActivity", "❌ Error fetching tutor data: ${e.message}", e)
            withContext(Dispatchers.Main) {
                txtTutorDescription.text = "Error loading tutor details."
                txtTutorRating.text = "⭐ 0.0 (0 reviews)"
                imgTutorProfile.setImageResource(R.drawable.ic_person)
            }
        }
    }

    private suspend fun openOrCreateChat() {
        try {
            val querySnapshot = db.collection("Chats")
                .whereEqualTo("StudentId", currentUserId)
                .whereEqualTo("TutorId", tutorId)
                .get()
                .await()

            val chatId = if (!querySnapshot.isEmpty) {
                querySnapshot.documents.first().id
            } else {
                val newChatId = UUID.randomUUID().toString()
                val newChat = hashMapOf(
                    "ChatId" to newChatId,
                    "StudentId" to currentUserId,
                    "TutorId" to tutorId,
                    "TutorName" to tutorName,
                    "CreatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "Messages" to emptyList<Map<String, Any>>()
                )
                db.collection("Chats").document(newChatId).set(newChat).await()
                newChatId
            }

            val studentDoc = db.collection("Students").document(currentUserId).get().await()
            val studentName = listOfNotNull(
                studentDoc.getString("Name"),
                studentDoc.getString("Surname")
            ).joinToString(" ").ifBlank { "Student" }

            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("ChatId", chatId)
                putExtra("TutorId", tutorId)
                putExtra("StudentId", currentUserId)
                putExtra("tutorName", tutorName)
                putExtra("studentName", studentName)
            }
            startActivity(intent)

        } catch (e: Exception) {
            Log.e("TutorProfileActivity", "❌ Failed to open chat: ${e.message}", e)
        }
    }
}
