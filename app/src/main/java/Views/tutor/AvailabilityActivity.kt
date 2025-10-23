package Views.tutor

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AvailabilityActivity : AppCompatActivity() {

    private lateinit var layoutContainer: LinearLayout
    private lateinit var btnSaveAvailability: Button
    private lateinit var btnBack: ImageButton
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val daysOfWeek = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val daySlotMap = mutableMapOf<String, MutableList<LinearLayout>>()
    private val TAG = "AvailabilityActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_availability)

        // Initialize Views
        layoutContainer = findViewById(R.id.layoutContainer)
        btnSaveAvailability = findViewById(R.id.btnSaveAvailability)
        btnBack = findViewById(R.id.btnBack)

        // Back Navigation
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Generate Availability Layout
        generateAvailabilityLayout()

        // Save Button Logic
        btnSaveAvailability.setOnClickListener {
            saveAvailability()
        }
    }


    private fun generateAvailabilityLayout() {
        if (layoutContainer.childCount > 0) {
            layoutContainer.removeAllViews()
        }

        daySlotMap.clear()

        for (day in daysOfWeek) {
            // Day Header (Expandable)
            val dayHeader = TextView(this).apply {
                text = "▶ $day"
                textSize = 18f
                setPadding(0, 16, 0, 8)
                setTextColor(resources.getColor(android.R.color.black, theme))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val slotsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = LinearLayout.GONE // collapsed initially
            }

            val dailySlots = mutableListOf<LinearLayout>()

            // Time slots for each day
            for (hour in 8..17) {
                val slotView = layoutInflater.inflate(R.layout.item_availability_slot, layoutContainer, false)

                val hourLabel = slotView.findViewById<TextView>(R.id.txtHour)
                val availableSwitch = slotView.findViewById<Switch>(R.id.switchAvailable)
                val groupCheckBox = slotView.findViewById<CheckBox>(R.id.checkGroup)
                val groupSizeInput = slotView.findViewById<EditText>(R.id.etGroupSize)

                hourLabel.text = String.format("%02d:00 - %02d:00", hour, hour + 1)

                groupCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    groupSizeInput.isEnabled = isChecked
                    if (!isChecked) groupSizeInput.text.clear()
                }

                slotsContainer.addView(slotView)
                dailySlots.add(slotView as LinearLayout)
            }

            // Expand/Collapse Behavior
            dayHeader.setOnClickListener {
                if (slotsContainer.visibility == LinearLayout.GONE) {
                    slotsContainer.visibility = LinearLayout.VISIBLE
                    dayHeader.text = "▼ $day"
                } else {
                    slotsContainer.visibility = LinearLayout.GONE
                    dayHeader.text = "▶ $day"
                }
            }

            layoutContainer.addView(dayHeader)
            layoutContainer.addView(slotsContainer)
            daySlotMap[day] = dailySlots
        }
    }

    /**
     * Saves the availability structure to Firestore.
     */
    private fun saveAvailability() {
        val tutorId = auth.currentUser?.uid ?: return
        val availabilityData = mutableMapOf<String, Any>()

        for ((day, slots) in daySlotMap) {
            val daySlots = mutableListOf<Map<String, Any>>()

            for (slotLayout in slots) {
                val hourLabel = slotLayout.findViewById<TextView>(R.id.txtHour)
                val availableSwitch = slotLayout.findViewById<Switch>(R.id.switchAvailable)
                val groupCheckBox = slotLayout.findViewById<CheckBox>(R.id.checkGroup)
                val groupSizeInput = slotLayout.findViewById<EditText>(R.id.etGroupSize)

                val hour = hourLabel.text.toString().substring(0, 2).toInt()
                daySlots.add(
                    mapOf(
                        "Hour" to hour,
                        "IsAvailable" to availableSwitch.isChecked,
                        "IsGroup" to groupCheckBox.isChecked,
                        "MaxStudents" to (groupSizeInput.text.toString().toIntOrNull() ?: 1),
                        "OneOnOnePricePerHour" to 550,
                        "GroupPricePerHour" to 0
                    )
                )
            }

            availabilityData[day] = daySlots
        }

        val tutorData = mapOf(
            "UserId" to tutorId,
            "WeeklyAvailability" to availabilityData
        )

        val docRef = db.collection("TutorProfiles").document(tutorId)
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                docRef.set(mapOf("UserId" to tutorId))
            }
            docRef.set(tutorData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Availability saved successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to save availability", e)
                    Toast.makeText(this, "❌ Failed to save availability.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
