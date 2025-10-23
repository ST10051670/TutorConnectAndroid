package Views.student.fragments

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
import com.example.tutorconnect.R
import Views.student.adapters.StudentBookingsAdapter
import com.example.tutorconnect.services.BookingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import models.Booking

class BookingsFragment : Fragment(), CoroutineScope {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private val bookingService = BookingService()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: StudentBookingsAdapter
    private val bookingsList = mutableListOf<Booking>()
    private val job = Job()
    override val coroutineContext = Dispatchers.Main + job

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_bookings, container, false)

        recyclerBookings = view.findViewById(R.id.recyclerBookings)
        txtEmpty = view.findViewById(R.id.txtEmptyBookings)
        progressBar = view.findViewById(R.id.progressBar)

        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())
        adapter = StudentBookingsAdapter(bookingsList)
        recyclerBookings.adapter = adapter

        fetchBookings()
        return view
    }

    private fun fetchBookings() {
        val studentId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        launch {
            val bookings = bookingService.getStudentBookings(studentId)
            progressBar.visibility = View.GONE

            bookingsList.clear()
            bookingsList.addAll(bookings.sortedByDescending { it.bookingDate })

            // Log what Firestore actually returned
            for (b in bookingsList) {
                Log.d(
                    "BookingDebug",
                    "Fetched Booking → id=${b.bookingId}, tutor=${b.tutorName}, " +
                            "isCompleted=${b.isCompleted}, isRated=${b.isRated}, rating=${b.rating}, comment=${b.comment}"
                )
            }

            if (bookingsList.isEmpty()) {
                txtEmpty.visibility = View.VISIBLE
                recyclerBookings.visibility = View.GONE
            } else {
                txtEmpty.visibility = View.GONE
                recyclerBookings.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
