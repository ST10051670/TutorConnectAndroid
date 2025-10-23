package com.example.tutorconnect.Views.tutor.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import Views.shared.adapters.ChatAdapter
import Views.shared.ChatDetailActivity
import models.Chat

class TutorInboxFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val chatList = mutableListOf<Chat>()

    private var chatListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_inbox, container, false)
        recyclerView = view.findViewById(R.id.recyclerTutorChats)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ChatAdapter(chatList, "Tutor") { chat ->
            openChatDetail(chat)
        }
        recyclerView.adapter = adapter

        listenToChatsRealtime()
        return view
    }

    /**
     * Real-time listener for the current tutor's chats
     */
    private fun listenToChatsRealtime() {
        val tutorId = auth.currentUser?.uid ?: return

        chatListener = db.collection("Chats")
            .whereEqualTo("TutorId", tutorId)
            .orderBy("CreatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("TutorInboxFragment", "❌ Error loading chats", error)
                    Toast.makeText(requireContext(), "Failed to load chats", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                chatList.clear()
                snapshots?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java)
                    if (chat != null) {
                        chat.ChatId = doc.id
                        chatList.add(chat)
                    }
                }
                adapter.notifyDataSetChanged()
                Log.d("TutorInboxFragment", "📡 Updated chat list in real-time (${chatList.size})")
            }
    }

    /**
     * Opens ChatDetailActivity and marks the chat as read
     */
    private fun openChatDetail(chat: Chat) {
        val tutorId = auth.currentUser?.uid ?: return

        // Mark chat as read for this tutor
        db.collection("Chats").document(chat.ChatId)
            .update("UnreadBy", null)
            .addOnSuccessListener {
                Log.d("TutorInboxFragment", "✅ Marked chat as read: ${chat.ChatId}")
                chat.UnreadBy = null
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Log.e("TutorInboxFragment", "⚠️ Failed to mark chat as read", it)
            }

        // Fetch both tutor & student names for the chat header
        db.collection("Students").document(chat.StudentId).get()
            .addOnSuccessListener { studentDoc ->
                val studentName = listOfNotNull(
                    studentDoc.getString("Name"),
                    studentDoc.getString("Surname")
                ).joinToString(" ").ifBlank { "Student" }

                db.collection("Tutors").document(tutorId).get()
                    .addOnSuccessListener { tutorDoc ->
                        val tutorName = listOfNotNull(
                            tutorDoc.getString("Name"),
                            tutorDoc.getString("Surname")
                        ).joinToString(" ").ifBlank { "Tutor" }

                        // Launch chat screen
                        val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                            putExtra("ChatId", chat.ChatId)
                            putExtra("TutorId", tutorId)
                            putExtra("StudentId", chat.StudentId)
                            putExtra("tutorName", tutorName)
                            putExtra("studentName", studentName)
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener {
                        Log.e("TutorInboxFragment", "Failed to fetch tutor name", it)
                    }
            }
            .addOnFailureListener {
                Log.e("TutorInboxFragment", "Failed to fetch student name", it)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatListener?.remove()
    }
}
