package models

import com.google.firebase.Timestamp

data class Chat(
    var ChatId: String = "",
    var StudentId: String = "",
    var TutorId: String = "",
    var Messages: MutableList<Message> = mutableListOf(),
    var CreatedAt: Timestamp = Timestamp.now(),
    var LastReadByStudent: Timestamp? = null,
    var UnreadBy: Any? = null,

    var TutorName: String? = null,
    var TutorImageBase64: String? = null
)
