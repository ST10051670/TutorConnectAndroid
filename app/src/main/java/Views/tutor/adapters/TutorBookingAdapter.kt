package Views.tutor.adapters

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

class TutorBookingAdapter(
    private val bookings: MutableList<Booking>,
    private val onActionClick: (Booking, String) -> Unit
) : RecyclerView.Adapter<TutorBookingAdapter.BookingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtDateTime: TextView = view.findViewById(R.id.txtDateTime)
        val txtSessionType: TextView = view.findViewById(R.id.txtSessionType)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val btnConfirm: Button = view.findViewById(R.id.btnConfirm)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
        val btnComplete: Button = view.findViewById(R.id.btnComplete)
        val actionLayout: ViewGroup = view.findViewById(R.id.actionLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_tutor, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.txtName.text = "Student: ${booking.studentName}"
        holder.txtDateTime.text = "${booking.day}, ${booking.time}"
        holder.txtSessionType.text = "Type: ${booking.sessionType}"
        holder.txtStatus.text = "Status: ${booking.status}"

        // Debug log to verify attendance
        Log.d("BookingDebug", "🧾 Booking ${booking.bookingId} | Attended=${booking.studentAttended} | Status=${booking.status}")

        // Hide buttons if cancelled or completed
        if (booking.isCancelled || booking.isCompleted) {
            holder.actionLayout.visibility = View.GONE
        } else {
            holder.actionLayout.visibility = View.VISIBLE
        }

        // Confirm booking
        holder.btnConfirm.setOnClickListener {
            updateBookingStatus(booking, "Confirmed", holder)
        }

        // Cancel booking
        holder.btnCancel.setOnClickListener {
            updateBookingStatus(booking, "Cancelled", holder)
        }

        // Mark complete
        holder.btnComplete.setOnClickListener {
            if (booking.studentAttended) {
                completeSession(booking, holder)
            } else {
                Toast.makeText(
                    holder.itemView.context,
                    "⚠️ Student hasn't marked attendance yet!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateBookingStatus(booking: Booking, newStatus: String, holder: BookingViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("Bookings").document(booking.bookingId)
                    .update(
                        "Status", newStatus,
                        "UpdatedAt", FieldValue.serverTimestamp()
                    ).await()

                booking.status = newStatus

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: $newStatus"
                    holder.actionLayout.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun completeSession(booking: Booking, holder: BookingViewHolder) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.collection("Bookings").document(booking.bookingId)
                    .update(
                        "IsCompleted", true,
                        "Status", "Completed",
                        "CompletionTimestamp", FieldValue.serverTimestamp(),
                        "UpdatedAt", FieldValue.serverTimestamp()
                    ).await()

                db.collection("TutorProfiles").document(booking.tutorId)
                    .update("TotalHoursLogged", FieldValue.increment(1.0))
                    .await()

                booking.isCompleted = true
                booking.status = "Completed"

                CoroutineScope(Dispatchers.Main).launch {
                    holder.txtStatus.text = "Status: Completed"
                    holder.actionLayout.visibility = View.GONE
                    Toast.makeText(holder.itemView.context, "✅ Session marked complete!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int = bookings.size

    // Allows live list refresh
    fun updateBookings(newBookings: List<Booking>) {
        bookings.clear()
        bookings.addAll(newBookings)
        notifyDataSetChanged()
    }
}
