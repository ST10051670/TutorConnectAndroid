package Views.student.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import Views.shared.adapters.ChatAdapter
import Views.shared.ChatDetailActivity
import models.Chat
import kotlin.coroutines.CoroutineContext

class MessagesFragment : Fragment(), CoroutineScope {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()

    private var chatListener: ListenerRegistration? = null
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_messages, container, false)

        recyclerMessages = view.findViewById(R.id.recyclerMessages)
        recyclerMessages.layoutManager = LinearLayoutManager(requireContext())

        chatAdapter = ChatAdapter(chatList, "Student") { chat ->
            openChatDetail(chat)
        }
        recyclerMessages.adapter = chatAdapter

        job = Job()

        listenToChatsRealtime()

        return view
    }

    /**
     * Real-time listener for student's chats
     */
    private fun listenToChatsRealtime() {
        if (currentUserId == null) return

        chatListener = db.collection("Chats")
            .whereEqualTo("StudentId", currentUserId)
            .orderBy("CreatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MessagesFragment", "❌ Real-time listener error", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    chatList.clear()
                    for (doc in snapshot.documents) {
                        val chat = doc.toObject(Chat::class.java)
                        if (chat != null) {
                            chat.ChatId = doc.id
                            chatList.add(chat)
                        }
                    }
                    chatAdapter.notifyDataSetChanged()
                    Log.d("MessagesFragment", "📡 Updated chat list in real-time (${chatList.size})")
                }
            }
    }

    /**
     * Open chat and mark it as read
     */
    private fun openChatDetail(chat: Chat) {
        val studentId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Mark chat as read in Firestore
        val chatRef = db.collection("Chats").document(chat.ChatId)
        chatRef.update("UnreadBy", null)
            .addOnSuccessListener {
                Log.d("MessagesFragment", "✅ Chat marked as read: ${chat.ChatId}")
                chat.UnreadBy = null
                chatAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("MessagesFragment", "❌ Failed to mark chat as read: ${e.message}")
            }

        // Fetch names & open chat
        db.collection("Students").document(studentId).get()
            .addOnSuccessListener { studentDoc ->
                val studentName =
                    "${studentDoc.getString("Name") ?: ""} ${studentDoc.getString("Surname") ?: ""}".trim()

                db.collection("Tutors").document(chat.TutorId).get()
                    .addOnSuccessListener { tutorDoc ->
                        val tutorName =
                            "${tutorDoc.getString("Name") ?: ""} ${tutorDoc.getString("Surname") ?: ""}".trim()

                        val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                            putExtra("ChatId", chat.ChatId)
                            putExtra("StudentId", chat.StudentId)
                            putExtra("TutorId", chat.TutorId)
                            putExtra("tutorName", tutorName)
                            putExtra("studentName", studentName)
                        }
                        startActivity(intent)
                    }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        chatListener?.remove()
    }
}
