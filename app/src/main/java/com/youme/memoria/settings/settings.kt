package com.youme.memoria.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.youme.memoria.BuildConfig
import com.youme.memoria.Gallery.IndexingViewModel
import com.youme.memoria.Gallery.IndexingViewModelFactory
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class settings : Fragment(R.layout.settings_layout) {
    private lateinit var repo: PhotoRepository
    private val indexingViewModel: IndexingViewModel by activityViewModels {
        IndexingViewModelFactory(repo, requireContext().applicationContext)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo= PhotoRepository(requireContext())
        setupIndexingUi()
        setupDBSize()
        setUpAppVersion()
        openRepo()
    }
    private fun setupDBSize(){
        val dBSizeText = requireView().findViewById<TextView>(R.id.db_size)
        indexingViewModel.getFormattedDatabaseSize("photo_db")
        lifecycleScope.launch {
            indexingViewModel.dbSize.collectLatest {
                dBSizeText.text =it
            }
        }


    }

    private fun openRepo(){
        val repoContainer = requireView().findViewById<LinearLayout>(R.id.repo)
        repoContainer.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/raslenabb12/Memoria".toUri())
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "No browser found to open the link", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setUpAppVersion(){
        val versionName: String = BuildConfig.VERSION_NAME
        val versionText = requireView().findViewById<TextView>(R.id.version)
        versionText.text="Version $versionName"
    }
    private fun setupIndexingUi(){
        val progressLog = requireView().findViewById<TextView>(R.id.textView7)
        val progressBar = requireView().findViewById<LinearProgressIndicator>(R.id.progressBar2)
        val statusLog = requireView().findViewById<TextView>(R.id.textView6)
        val etaLog = requireView().findViewById<TextView>(R.id.textView5)

        val pauseBt = requireView().findViewById<MaterialButton>(R.id.button3)
        val resumeBt = requireView().findViewById<MaterialButton>(R.id.button4)
        lifecycleScope.launch {
            indexingViewModel.state.collectLatest {state ->

                when(state){
                    is IndexingViewModel.IndexingState.Ready ->{
                        statusLog.text="Status: Ready"
                        progressLog.text="${state.processed}/${state.total}"
                        progressBar.max=state.total
                        progressBar.progress=state.processed

                        etaLog.text ="${(state.processed*100)/state.total}%"

                        resumeBt.apply {
                            isEnabled=true
                            setOnClickListener {
                                progressLog.text="Loading.."
                                indexingViewModel.startIndexing()
                            }
                        }
                        pauseBt.isEnabled=false
                    }
                    is IndexingViewModel.IndexingState.Running->{
                        statusLog.text="Status: Running"
                        progressLog.text="${state.processed}/${state.total}"
                        progressBar.max=state.total
                        progressBar.progress=state.processed

                        etaLog.apply {
                            isVisible=true
                            text = "${(state.processed*100)/state.total}%  ETA: ${"%.1f".format(state.etaMinutes)}Min"
                        }

                        pauseBt.apply {
                            isEnabled=true
                            setOnClickListener {
                                indexingViewModel.pause()
                            }
                        }
                        resumeBt.isEnabled=false
                    }
                    is IndexingViewModel.IndexingState.Idle->{
                        statusLog.text = "Status: Idle"
                    }
                    is IndexingViewModel.IndexingState.Completed ->{
                        pauseBt.isEnabled=false
                        resumeBt.isEnabled=false
                        etaLog.isVisible=false
                        statusLog.text="Status: Completed"
                        progressLog.text="${state.total}/${state.total}"
                    }
                }
            }
        }

    }
}