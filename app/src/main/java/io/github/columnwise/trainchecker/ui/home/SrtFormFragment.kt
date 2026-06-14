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
import io.github.columnwise.trainchecker.data.api.srt.SRT_STATION_CODE
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
class SrtFormFragment : Fragment() {
    @Inject lateinit var creds: CredentialStore

    private var _b: FragmentSrtFormBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels({ requireParentFragment() })

    private var selectedDate: String = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSrtFormBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val stations = SRT_STATION_CODE.keys.toList()
        b.actvDep.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stations))
        b.actvArr.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stations))

        val seatTypes = listOf("일반실" to SeatType.GENERAL, "특실" to SeatType.SPECIAL, "상관없음" to SeatType.ANY)
        b.actvSeatType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, seatTypes.map { it.first }))
        b.actvSeatType.setText("일반실", false)

        setupTimeDropdowns()
        setupDatePicker()
        setupClearErrors()

        b.btnStart.setOnClickListener { onStartClicked(seatTypes) }
    }

    private fun setupTimeDropdowns() {
        val hours = (0..23).map { h -> "%02d:00".format(h) to "%02d00".format(h) }
        val hourLabels = hours.map { it.first }
        b.actvTimeFrom.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, hourLabels))
        b.actvTimeFrom.setText("00:00", false)

        val toOptions = listOf("제한 없음" to "") + hours
        b.actvTimeTo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, toOptions.map { it.first }))
        b.actvTimeTo.setText("제한 없음", false)

        b.actvTimeFrom.setOnItemClickListener { _, _, _, _ -> b.tilTimeFrom.error = null }
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
                val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                selectedDate = fmt.format(cal.time)
                val display = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                b.etDate.setText(display.format(cal.time))
                b.tilDate.error = null
            }
            picker.show(parentFragmentManager, "date_picker")
        }
        b.etDate.setOnClickListener(openPicker)
        b.tilDate.setEndIconOnClickListener(openPicker)
    }

    private fun setupClearErrors() {
        b.actvDep.setOnItemClickListener { _, _, _, _ -> b.tilDep.error = null }
        b.actvArr.setOnItemClickListener { _, _, _, _ -> b.tilArr.error = null }
    }

    private fun onStartClicked(seatTypes: List<Pair<String, SeatType>>) {
        val dep = b.actvDep.text.toString().trim()
        val arr = b.actvArr.text.toString().trim()
        val timeFromLabel = b.actvTimeFrom.text.toString().trim()

        var firstError: View? = null

        if (dep.isEmpty() || dep !in SRT_STATION_CODE) {
            b.tilDep.error = "출발역을 선택하세요"
            if (firstError == null) firstError = b.actvDep
        } else b.tilDep.error = null

        if (arr.isEmpty() || arr !in SRT_STATION_CODE) {
            b.tilArr.error = "도착역을 선택하세요"
            if (firstError == null) firstError = b.actvArr
        } else b.tilArr.error = null

        if (selectedDate.isEmpty()) {
            b.tilDate.error = "날짜를 선택하세요"
            if (firstError == null) firstError = b.etDate
        } else b.tilDate.error = null

        if (timeFromLabel.isEmpty()) {
            b.tilTimeFrom.error = "시작 시간을 선택하세요"
            if (firstError == null) firstError = b.actvTimeFrom
        } else b.tilTimeFrom.error = null

        if (firstError != null) { firstError.requestFocus(); return }

        if (creds.srtId.isEmpty() || creds.srtPw.isEmpty()) {
            Snackbar.make(requireView(), "설정 탭에서 SRT 아이디/비밀번호를 먼저 입력하세요", Snackbar.LENGTH_LONG).show()
            return
        }

        // "HH:mm" → "HHmm"
        val timeFrom = timeFromLabel.replace(":", "")
        val timeToLabel = b.actvTimeTo.text.toString()
        val timeTo = if (timeToLabel == "제한 없음") "" else timeToLabel.replace(":", "")

        val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
        val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
        vm.startWatch(TrainType.SRT, dep, arr, selectedDate, timeFrom, timeTo, seatType) { jobId ->
            requireContext().startForegroundService(Intent(requireContext(), TicketWatcherService::class.java).apply {
                action = TicketWatcherService.ACTION_START
                putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
            })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
