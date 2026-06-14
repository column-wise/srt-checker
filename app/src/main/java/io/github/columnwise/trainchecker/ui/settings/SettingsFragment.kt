package io.github.columnwise.trainchecker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.columnwise.trainchecker.databinding.FragmentSettingsBinding
import io.github.columnwise.trainchecker.service.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.etSrtId.setText(vm.srtId.value)
        binding.etSrtPw.setText(vm.srtPw.value)
        binding.etKtxId.setText(vm.ktxId.value)
        binding.etKtxPw.setText(vm.ktxPw.value)
        binding.etInterval.setText(vm.pollInterval.value.toString())

        binding.btnSave.setOnClickListener {
            val interval = binding.etInterval.text.toString().toIntOrNull() ?: 15
            vm.save(
                srtId = binding.etSrtId.text.toString(),
                srtPw = binding.etSrtPw.text.toString(),
                ktxId = binding.etKtxId.text.toString(),
                ktxPw = binding.etKtxPw.text.toString(),
                interval = interval,
            )
            Snackbar.make(view, "저장됨", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnClearLog.setOnClickListener { AppLogger.clear() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.logs.collect { lines ->
                    val text = if (lines.isEmpty()) "(로그 없음)" else lines.joinToString("\n")
                    binding.tvLog.text = text
                    // 최신 로그로 자동 스크롤
                    binding.logScrollView.post {
                        binding.logScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
