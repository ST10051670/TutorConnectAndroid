package Views.tutor.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import Views.tutor.adapters.TutorBookingAdapter
import com.example.tutorconnect.R
import com.example.tutorconnect.services.BookingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.*
import models.Booking

class TutorBookingsFragment : Fragment(), CoroutineScope {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val bookingService = BookingService()
    private val bookingsList = mutableListOf<Booking>()
    private lateinit var adapter: TutorBookingAdapter

    private val job = Job()
    override val coroutineContext = Dispatchers.Main + job

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_bookings, container, false)

        recyclerBookings = view.findViewById(R.id.recyclerBookingsTutor)
        txtEmpty = view.findViewById(R.id.txtEmptyTutorBookings)
        progressBar = view.findViewById(R.id.progressBarTutor)

        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())
        adapter = TutorBookingAdapter(bookingsList) { booking, action ->
            handleBookingAction(booking, action)
        }
        recyclerBookings.adapter = adapter

        fetchBookings()
        return view
    }

    /**
     * Fetch all bookings for the logged-in tutor (via BookingService)
     */
    private fun fetchBookings() {
        val tutorId = auth.currentUser?.uid ?: return
        Log.d("TutorBookingsFragment", "👀 Fetching bookings for tutorId: $tutorId")

        progressBar.visibility = View.VISIBLE

        launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    bookingService.getTutorBookings(tutorId)
                }

                progressBar.visibility = View.GONE
                bookingsList.clear()
                bookingsList.addAll(result)

                if (bookingsList.isEmpty()) {
                    recyclerBookings.visibility = View.GONE
                    txtEmpty.visibility = View.VISIBLE
                    txtEmpty.text = "No bookings available."
                } else {
                    recyclerBookings.visibility = View.VISIBLE
                    txtEmpty.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                }

                Log.d("TutorBookingsFragment", "✅ Loaded ${bookingsList.size} bookings.")

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                txtEmpty.visibility = View.VISIBLE
                txtEmpty.text = "Failed to load bookings."
                Log.e("TutorBookingsFragment", "❌ Error fetching bookings: ${e.message}", e)
            }
        }
    }

    /**
     * Handle tutor actions: Confirm, Cancel, Complete
     */
    private fun handleBookingAction(booking: Booking, action: String) {
        val (newStatus, isCancelled, isCompleted) = when (action) {
            "confirm" -> Triple("Confirmed", false, false)
            "cancel" -> Triple("Cancelled", true, false)
            "complete" -> Triple("Completed", false, true)
            else -> return
        }

        Log.d("TutorBookingsFragment", "⚙️ Updating booking ${booking.bookingId} → $newStatus")

        launch {
            val success = withContext(Dispatchers.IO) {
                bookingService.updateBookingStatus(booking.bookingId, newStatus)
            }

            if (success) {
                booking.status = newStatus
                booking.isCancelled = isCancelled
                booking.isCompleted = isCompleted
                booking.updatedAt = com.google.firebase.Timestamp.now()

                adapter.notifyItemChanged(bookingsList.indexOf(booking))
                Log.d("TutorBookingsFragment", "✅ Booking ${booking.bookingId} updated to $newStatus")
            } else {
                Log.e("TutorBookingsFragment", "❌ Failed to update booking ${booking.bookingId}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
