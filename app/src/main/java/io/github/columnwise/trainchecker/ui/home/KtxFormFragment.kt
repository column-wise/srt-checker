package io.github.columnwise.trainchecker.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import io.github.columnwise.trainchecker.data.model.SeatType
import io.github.columnwise.trainchecker.data.model.TrainType
import io.github.columnwise.trainchecker.data.prefs.CredentialStore
import io.github.columnwise.trainchecker.databinding.FragmentSrtFormBinding
import io.github.columnwise.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class KtxFormFragment : Fragment() {
    @Inject lateinit var creds: CredentialStore

    private var _b: FragmentSrtFormBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels({ requireParentFragment() })

    private var selectedDate: String = ""  // YYYYMMDD

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSrtFormBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val seatTypes = listOf("일반실" to SeatType.GENERAL, "특실" to SeatType.SPECIAL, "상관없음" to SeatType.ANY)
        b.actvSeatType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, seatTypes.map { it.first }))
        b.actvSeatType.setText("일반실", false)

        setupDatePicker()
        setupClearErrors()

        b.btnStart.setOnClickListener { onStartClicked(seatTypes) }
    }

    private fun setupDatePicker() {
        val openPicker = View.OnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build()
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("날짜 선택")
                .setCalendarConstraints(constraints)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = millis
                val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                selectedDate = fmt.format(cal.time)
                val display = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                b.etDate.setText(display.format(cal.time))
                b.tilDate.error = null
            }
            picker.show(parentFragmentManager, "date_picker_ktx")
        }
        b.etDate.setOnClickListener(openPicker)
        b.tilDate.setEndIconOnClickListener(openPicker)
    }

    private fun setupClearErrors() {
        b.actvDep.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { b.tilDep.error = null }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        b.actvArr.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { b.tilArr.error = null }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        b.etTimeFrom.setOnFocusChangeListener { _, _ -> b.tilTimeFrom.error = null }
    }

    private fun onStartClicked(seatTypes: List<Pair<String, SeatType>>) {
        val dep = b.actvDep.text.toString().trim()
        val arr = b.actvArr.text.toString().trim()
        val timeFrom = b.etTimeFrom.text.toString().trim()

        var firstError: View? = null

        if (dep.isEmpty()) {
            b.tilDep.error = "출발역을 입력하세요"
            if (firstError == null) firstError = b.actvDep
        } else b.tilDep.error = null

        if (arr.isEmpty()) {
            b.tilArr.error = "도착역을 입력하세요"
            if (firstError == null) firstError = b.actvArr
        } else b.tilArr.error = null

        if (selectedDate.isEmpty()) {
            b.tilDate.error = "날짜를 선택하세요"
            if (firstError == null) firstError = b.etDate
        } else b.tilDate.error = null

        if (timeFrom.length != 4) {
            b.tilTimeFrom.error = "출발 시간 4자리 (예: 0800)"
            if (firstError == null) firstError = b.etTimeFrom
        } else b.tilTimeFrom.error = null

        if (firstError != null) {
            firstError.requestFocus()
            return
        }

        if (creds.ktxId.isEmpty() || creds.ktxPw.isEmpty()) {
            Snackbar.make(requireView(), "설정 탭에서 KTX 아이디/비밀번호를 먼저 입력하세요", Snackbar.LENGTH_LONG).show()
            return
        }

        val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
        val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
        vm.startWatch(TrainType.KTX, dep, arr, selectedDate, timeFrom, b.etTimeTo.text.toString(), seatType) { jobId ->
            val svcIntent = Intent(requireContext(), TicketWatcherService::class.java).apply {
                action = TicketWatcherService.ACTION_START
                putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
            }
            requireContext().startForegroundService(svcIntent)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
