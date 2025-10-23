package com.example.tutorconnect.Views.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.SlotAdapter
import com.example.tutorconnect.services.BookingResult
import com.example.tutorconnect.services.BookingService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import models.Booking
import models.TimeSlot
import java.util.*

class TutorTimeSlotsActivity : AppCompatActivity() {

    private lateinit var recyclerSlots: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var refreshButton: FloatingActionButton

    private val bookingService = BookingService()
    private val auth = FirebaseAuth.getInstance()
    private var slotsListener: ListenerRegistration? = null

    private lateinit var tutorId: String
    private lateinit var tutorName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_time_slots)

        recyclerSlots = findViewById(R.id.recyclerSlots)
        progressBar = findViewById(R.id.progressBarSlots)
        refreshButton = findViewById(R.id.btnRefreshSlots)

        recyclerSlots.layoutManager = LinearLayoutManager(this)

        tutorId = intent.getStringExtra("tutorId") ?: ""
        tutorName = intent.getStringExtra("tutorName") ?: ""

        if (tutorId.isEmpty()) {
            Toast.makeText(this, "Invalid tutor profile.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("TutorTimeSlotsActivity", "Loading slots for tutorId: $tutorId")

        // Initial load
        loadSlots()

        // Refresh button manual reload
        refreshButton.setOnClickListener {
            Toast.makeText(this, "Refreshing available slots...", Toast.LENGTH_SHORT).show()
            loadSlots()
        }
    }

    // Real-time slot updates using Firestore listener
    private fun loadSlots() {
        progressBar.visibility = View.VISIBLE
        recyclerSlots.visibility = View.GONE

        val tutorRef = FirebaseFirestore.getInstance()
            .collection("TutorProfiles")
            .document(tutorId)

        // Remove any old listener before creating new one
        slotsListener?.remove()

        slotsListener = tutorRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("TutorTimeSlotsActivity", "Error listening to slots", error)
                Toast.makeText(this, "Failed to listen for slot updates.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val weeklyAvailability = snapshot.get("WeeklyAvailability") as? Map<String, Any> ?: emptyMap()
                val slots = mutableListOf<TimeSlot>()

                for ((day, slotsAny) in weeklyAvailability) {
                    val slotList = slotsAny as? List<Map<String, Any>> ?: continue
                    for (slot in slotList) {
                        val isAvailable = slot["IsAvailable"] as? Boolean ?: false
                        if (isAvailable) {
                            val hour = (slot["Hour"] as? Number)?.toInt() ?: continue
                            val time = String.format("%02d:00 - %02d:00", hour, hour + 1)
                            val isGroup = slot["IsGroup"] as? Boolean ?: false
                            val maxStudents = (slot["MaxStudents"] as? Long)?.toInt() ?: 1
                            val groupPrice = (slot["GroupPricePerHour"] as? Number)?.toDouble() ?: 0.0
                            val oneOnOnePrice = (slot["OneOnOnePricePerHour"] as? Number)?.toDouble() ?: 0.0

                            slots.add(
                                TimeSlot(
                                    tutorId = tutorId,
                                    day = day,
                                    time = time,
                                    sessionType = if (isGroup) "Group" else "One-on-One",
                                    isAvailable = true,
                                    maxStudents = maxStudents,
                                    groupPricePerHour = groupPrice,
                                    oneOnOnePricePerHour = oneOnOnePrice
                                )
                            )
                        }
                    }
                }

                // Update UI with live slots
                recyclerSlots.adapter = SlotAdapter(slots) { slot ->
                    bookSlot(slot)
                }

                progressBar.visibility = View.GONE
                recyclerSlots.visibility = if (slots.isEmpty()) View.GONE else View.VISIBLE

                if (slots.isEmpty()) {
                    Toast.makeText(this, "No available slots right now.", Toast.LENGTH_SHORT).show()
                }
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Tutor profile not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle student booking
    private fun bookSlot(slot: TimeSlot) {
        val studentId = auth.currentUser?.uid
        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "You must be logged in to book a session.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        recyclerSlots.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                var studentName = "Student"

                // Fetch student's full name from Firestore
                val studentDoc = db.collection("Students").document(studentId).get().await()

                if (studentDoc.exists()) {
                    val firstName = studentDoc.getString("Name") ?: ""
                    val lastName = studentDoc.getString("Surname") ?: ""
                    studentName = listOf(firstName, lastName).joinToString(" ").trim().ifBlank { "Student" }
                } else {
                    // fallback to display name or email if not found in Students collection
                    studentName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: auth.currentUser?.email
                                ?: "Student"
                }

                // Prepare booking object with correct student name
                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val hour = slot.time.substringBefore(":").toIntOrNull() ?: 0

                val booking = Booking(
                    tutorId = tutorId,
                    tutorName = tutorName,
                    studentId = studentId,
                    studentName = studentName,
                    day = slot.day,
                    date = currentDate,
                    hour = hour,
                    time = slot.time,
                    sessionType = slot.sessionType,
                    isGroup = slot.sessionType.equals("Group", ignoreCase = true),
                    isCancelled = false,
                    isCompleted = false,
                    status = "Pending"
                )

                //  Attempt booking transaction
                when (val result = bookingService.createBookingTransactional(booking)) {
                    is BookingResult.Success -> {
                        Toast.makeText(this@TutorTimeSlotsActivity, "✅ ${result.message}", Toast.LENGTH_SHORT).show()
                        Log.d("TutorTimeSlotsActivity", "Booking created successfully: ${slot.day} ${slot.time}")

                        startActivity(
                            Intent(this@TutorTimeSlotsActivity, StudentDashboard::class.java)
                                .apply { putExtra("navigateTo", "bookings") }
                        )
                        finish()
                    }

                    is BookingResult.Failure -> {
                        Toast.makeText(this@TutorTimeSlotsActivity, "⚠️ Booking failed: ${result.reason}", Toast.LENGTH_LONG).show()
                        Log.e("TutorTimeSlotsActivity", "Booking failed: ${result.reason}")
                        loadSlots()
                    }
                }

            } catch (e: Exception) {
                Log.e("TutorTimeSlotsActivity", "Error creating booking: ${e.message}", e)
                Toast.makeText(this@TutorTimeSlotsActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                recyclerSlots.visibility = View.VISIBLE
            }
        }
    }


    // Clean up Firestore listener on exit
    override fun onDestroy() {
        super.onDestroy()
        slotsListener?.remove()
    }
}
