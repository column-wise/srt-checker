package io.github.columnwise.trainchecker.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
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
import kotlinx.coroutines.launch
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
        b.actvSeatType.setAdapter(noFilterAdapter(seatTypes.map { it.first }))
        b.actvSeatType.setText("상관없음", false)

        setupTimeDropdowns()
        setupDatePicker()
        setupClearErrors()

        b.btnSwap.setOnClickListener {
            val dep = b.actvDep.text.toString()
            val arr = b.actvArr.text.toString()
            b.actvDep.setText(arr, false)
            b.actvArr.setText(dep, false)
            b.tilDep.error = null
            b.tilArr.error = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.topRoutes().collect { routes ->
                    b.chipGroupRoutes.removeAllViews()
                    if (routes.isEmpty()) {
                        b.chipGroupRoutes.visibility = View.GONE
                    } else {
                        b.chipGroupRoutes.visibility = View.VISIBLE
                        routes.forEach { (dep, arr) ->
                            val chip = Chip(requireContext()).apply {
                                text = "$dep → $arr"
                                isClickable = true
                                setOnClickListener {
                                    b.actvDep.setText(dep, false)
                                    b.actvArr.setText(arr, false)
                                    b.tilDep.error = null
                                    b.tilArr.error = null
                                }
                            }
                            b.chipGroupRoutes.addView(chip)
                        }
                    }
                }
            }
        }

        b.btnStart.setOnClickListener { onStartClicked(seatTypes) }
    }

    private fun setupTimeDropdowns() {
        val hours = (0..23).map { h -> "%02d:00".format(h) to "%02d00".format(h) }
        val hourLabels = hours.map { it.first }
        b.actvTimeFrom.setAdapter(noFilterAdapter(hourLabels))
        b.actvTimeFrom.setText("00:00", false)

        val toOptions = listOf("제한 없음" to "") + hours
        b.actvTimeTo.setAdapter(noFilterAdapter(toOptions.map { it.first }))
        b.actvTimeTo.setText("제한 없음", false)

        b.actvTimeFrom.setOnItemClickListener { _, _, _, _ -> b.tilTimeFrom.error = null }
    }

    private fun setupDatePicker() {
        val today = Calendar.getInstance()
        val fmtVal = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val fmtDisplay = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
        selectedDate = fmtVal.format(today.time)
        b.etDate.setText(fmtDisplay.format(today.time))

        val openPicker = View.OnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build()
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("날짜 선택")
                .setCalendarConstraints(constraints)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
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

        val timeToLabel = b.actvTimeTo.text.toString().trim()
        if (timeToLabel != "제한 없음" && timeFromLabel.isNotEmpty() && timeToLabel.isNotEmpty()) {
            val fromHour = timeFromLabel.substringBefore(":").toIntOrNull() ?: 0
            val toHour = timeToLabel.substringBefore(":").toIntOrNull() ?: 0
            if (fromHour >= toHour) {
                b.tilTimeFrom.error = "종료 시간보다 앞서야 합니다"
                if (firstError == null) firstError = b.actvTimeFrom
            }
        }

        if (firstError != null) { firstError.requestFocus(); return }

        if (creds.srtId.isEmpty() || creds.srtPw.isEmpty()) {
            Snackbar.make(requireView(), "설정 탭에서 SRT 아이디/비밀번호를 먼저 입력하세요", Snackbar.LENGTH_LONG).show()
            return
        }

        val timeFrom = timeFromLabel.replace(":", "")
        val timeTo = if (timeToLabel == "제한 없음") "" else timeToLabel.replace(":", "")

        val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
        val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
        vm.startWatch(TrainType.SRT, dep, arr, selectedDate, timeFrom, timeTo, seatType,
            onAlreadyWatching = {
                Snackbar.make(requireView(), "이미 감시 중입니다. 감시 목록에서 중지 후 다시 시도하세요", Snackbar.LENGTH_LONG).show()
            },
            onDuplicate = {
                Snackbar.make(requireView(), "이미 같은 조건으로 감시 중입니다", Snackbar.LENGTH_LONG).show()
            },
            onJobId = { jobId ->
                requireContext().startForegroundService(Intent(requireContext(), TicketWatcherService::class.java).apply {
                    action = TicketWatcherService.ACTION_START
                    putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                })
            }
        )
    }

    private fun noFilterAdapter(items: List<String>) =
        object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, items) {
            private val noFilter = object : Filter() {
                override fun performFiltering(c: CharSequence?) = FilterResults().apply { values = items; count = items.size }
                override fun publishResults(c: CharSequence?, r: FilterResults?) { notifyDataSetChanged() }
            }
            override fun getFilter() = noFilter
        }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
