/*
 * Example ViewModel that fetches current time and an Activity that observes it.
 * Demonstrates updating the UI and simple error handling.
 */

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

// UI states exposed by the ViewModel
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val time: String) : UiState()
    data class Error(val message: String) : UiState()
}

// Simple provider abstraction
interface TimeProvider {
    @Throws(Exception::class)
    fun getCurrentTime(): String
}

// Default implementation using system clock. An optional failChancePercent can be used
// to simulate transient failures for demo/testing.
class SystemTimeProvider(private val failChancePercent: Int = 0) : TimeProvider {
    override fun getCurrentTime(): String {
        if (failChancePercent > 0 && Random.nextInt(100) < failChancePercent) {
            throw RuntimeException("Simulated time provider failure")
        }
        val now = LocalDateTime.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return now.format(fmt)
    }
}

// ViewModel that fetches time and exposes UiState via LiveData
class TimeViewModel(private val provider: TimeProvider = SystemTimeProvider()) : ViewModel() {
    private val _state = MutableLiveData<UiState>(UiState.Idle)
    val state: LiveData<UiState> = _state

    fun refreshTime() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val result = try {
                val time = withContext(Dispatchers.IO) { provider.getCurrentTime() }
                UiState.Success(time)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unknown error")
            }
            _state.value = result
        }
    }
}

// Small Activity demonstrating how to use the ViewModel and update the UI
class TimeActivity : AppCompatActivity() {

    private val vm: TimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val timeText = TextView(this).apply {
            text = "—"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val statusText = TextView(this).apply {
            text = "Idle"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val refreshBtn = Button(this).apply {
            text = "Get current time"
            setOnClickListener { vm.refreshTime() }
        }

        container.addView(timeText)
        container.addView(statusText)
        container.addView(progress)
        container.addView(refreshBtn)

        setContentView(container)

        vm.state.observe(this) { state ->
            when (state) {
                is UiState.Idle -> {
                    progress.visibility = View.GONE
                    statusText.text = "Idle"
                    refreshBtn.isEnabled = true
                }
                is UiState.Loading -> {
                    progress.visibility = View.VISIBLE
                    statusText.text = "Loading..."
                    refreshBtn.isEnabled = false
                }
                is UiState.Success -> {
                    progress.visibility = View.GONE
                    timeText.text = state.time
                    statusText.text = "Updated"
                    refreshBtn.isEnabled = true
                }
                is UiState.Error -> {
                    progress.visibility = View.GONE
                    statusText.text = "Error: ${state.message}"
                    refreshBtn.isEnabled = true
                }
            }
        }

        // Optionally fetch time on start
        vm.refreshTime()
    }
}
