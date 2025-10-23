package models

import com.google.firebase.Timestamp

data class Booking(
    var bookingId: String = "",
    var tutorId: String = "",
    var tutorName: String = "",
    var studentId: String = "",
    var studentName: String = "",
    var day: String = "",
    var date: String = "",
    var hour: Int = 0,
    var endHour: Int = 0,
    var time: String = "", // e.g. "15:00 - 16:00"
    var sessionType: String = "",
    var isGroup: Boolean = false,
    var isCancelled: Boolean = false,
    var isCompleted: Boolean = false,
    var status: String = "Pending",
    var amountEarned: Double? = 0.0,
    var pricePaid: Double? = 0.0,
    var bookingDate: Timestamp? = null,
    var loggedAt: Timestamp? = null,

    var rating: Double? = null,
    var comment: String? = null,
    var isRated: Boolean = false,

    var studentAttended: Boolean = false,
    var attendanceTimestamp: Timestamp? = null,
    var completionTimestamp: Timestamp? = null,

    var updatedAt: Timestamp? = null,
    var createdAt: Timestamp? = null
)
