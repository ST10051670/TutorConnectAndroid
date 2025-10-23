package Views.student.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import models.Booking

class StudentBookingsAdapter(private val bookings: MutableList<Booking>)
    : RecyclerView.Adapter<StudentBookingsAdapter.BookingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtDateTime: TextView = view.findViewById(R.id.txtDateTime)
        val txtSessionType: TextView = view.findViewById(R.id.txtSessionType)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val layoutRating: LinearLayout = view.findViewById(R.id.layoutRating)
        val ratingBar: RatingBar = view.findViewById(R.id.ratingBar)
        val edtComment: EditText = view.findViewById(R.id.edtComment)
        val btnSubmit: Button = view.findViewById(R.id.btnSubmitRating)
        val btnAttendance: Button = view.findViewById(R.id.btnAttendance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_student, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.txtName.text = "Tutor: ${booking.tutorName}"
        holder.txtDateTime.text = "${booking.day}, ${booking.time}"
        holder.txtSessionType.text = "Type: ${booking.sessionType}"
        holder.txtStatus.text = "Status: ${booking.status}"

        // Attendance button visibility
        if (booking.status == "Confirmed" && !booking.studentAttended) {
            holder.btnAttendance.visibility = View.VISIBLE
        } else {
            holder.btnAttendance.visibility = View.GONE
        }

        // Rating layout visibility
        if (booking.isCompleted && !booking.isRated) {
            holder.layoutRating.visibility = View.VISIBLE
        } else {
            holder.layoutRating.visibility = View.GONE
        }

        // Attendance button action
        holder.btnAttendance.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                markAttendance(booking, holder)
            }
        }

        // Submit rating
        holder.btnSubmit.setOnClickListener {
            val rating = holder.ratingBar.rating.toDouble()
            val comment = holder.edtComment.text.toString().trim()

            if (rating == 0.0) {
                Toast.makeText(holder.itemView.context, "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                submitRating(booking, rating, comment, holder)
            }
        }
    }

    private suspend fun markAttendance(booking: Booking, holder: BookingViewHolder) {
        try {
            db.collection("Bookings").document(booking.bookingId)
                .update(
                    "StudentAttended", true,
                    "AttendanceTimestamp", FieldValue.serverTimestamp(),
                    "Status", "Attended",
                    "UpdatedAt", FieldValue.serverTimestamp()
                ).await()

            booking.studentAttended = true
            booking.status = "Attended"

            CoroutineScope(Dispatchers.Main).launch {
                holder.btnAttendance.visibility = View.GONE
                holder.txtStatus.text = "Status: Attended"
                Toast.makeText(holder.itemView.context, "✅ Attendance marked", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("StudentBookingsAdapter", "❌ Error marking attendance: ${e.message}", e)
        }
    }

    private suspend fun submitRating(booking: Booking, rating: Double, comment: String, holder: BookingViewHolder) {
        try {
            val updates = mapOf(
                "rating" to rating,
                "comment" to comment,
                "isRated" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("Bookings").document(booking.bookingId).update(updates).await()

            booking.isRated = true
            booking.rating = rating

            CoroutineScope(Dispatchers.Main).launch {
                holder.layoutRating.visibility = View.GONE
                holder.txtStatus.text = "Status: Completed (Rated ⭐$rating)"
            }

        } catch (e: Exception) {
            Log.e("BookingDebug", "❌ Error submitting rating: ${e.message}", e)
        }
    }

    override fun getItemCount() = bookings.size
}
