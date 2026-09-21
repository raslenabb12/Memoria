package com.youme.memoria.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.youme.memoria.settings.FoldersManager.FoldersManager

class settings : Fragment(R.layout.settings_layout) {
    private lateinit var repo: PhotoRepository
    private val indexingViewModel: IndexingViewModel by activityViewModels {
        IndexingViewModelFactory(repo, requireContext().applicationContext)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo= PhotoRepository(requireContext())
        setupIndexingUi()
        setupUI()
        setUpAppVersion()
        openRepo()
        setupFolders()
    }
    private fun setupUI(){
        val dBSizeText = requireView().findViewById<TextView>(R.id.db_size)
        val batteryStatusChip = requireView().findViewById<Chip>(R.id.chip)
        val alertText = requireView().findViewById<TextView>(R.id.textView4)
        indexingViewModel.getFormattedDatabaseSize("photo_db")
        lifecycleScope.launch {
            indexingViewModel.dbSize.collectLatest {
                dBSizeText.text =it
            }
        }
        lifecycleScope.launch {
            indexingViewModel.batteryTemp.collectLatest {temp->
                batteryStatusChip.apply {
                    text =  "$temp°C"

                      when{
                        temp<35f->{
                            chipIconTint= ColorStateList.valueOf(Color.GREEN)
                            alertText.isVisible = false
                        }

                        temp<42f->{
                            val color=ColorStateList.valueOf("#FFA500".toColorInt())
                            chipIconTint = color
                            alertText.apply {
                                text = "Running a bit slower to keep your phone cool"
                                isVisible=true
                                setTextColor(color)
                            }
                        }

                        else->{
                            val color = ColorStateList.valueOf(Color.RED)
                            chipIconTint = color
                            alertText.apply {
                                text = "Paused — your device is warm. Indexing will resume once it cools down."
                                isVisible=true
                                setTextColor(color)
                            }
                        }
                    }
                }
            }
        }


    }

    private fun openRepo(){
        val repoContainer = requireView().findViewById<MaterialCardView>(R.id.repo)
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
        versionText.text=versionName
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

                try {
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
                            val old= " ETA: ${"%.1f".format(state.etaMinutes)}Min"

                            etaLog.apply {
                                isVisible=true
                                text = "${(state.processed*100)/state.total}%  ETA: ${formatEta(state.etaMinutes.toLong())}"
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

                            progressBar.apply {
                                max = state.total
                                progress = state.total
                            }
                        }
                    }
                } catch (e: Exception) {
                   // no done yet
                }
            }
        }

    }
    private fun setupFolders(){

        val manageFoldersButton = requireView().findViewById<MaterialCardView>(R.id.manageFoldersButt)
        val foldersTitle = requireView().findViewById<TextView>(R.id.foldersTitle)

        manageFoldersButton.setOnClickListener {
            FoldersManager().show(parentFragmentManager,"")
        }


        lifecycleScope.launch {
            indexingViewModel.folders.collectLatest { folders->

                foldersTitle.text = "Manage Folders (${folders.size})"

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            indexingViewModel.folderPrefs.selectedBuckets.collectLatest { selectedFolders->
                indexingViewModel.pause()
                indexingViewModel.scanGallery()

                view?.let {
                    it.findViewById<TextView>(R.id.textView33).text = "${selectedFolders.size} selected"
                }
            }
        }
    }
    private fun formatEta(etaMs: Long): String {
        if (etaMs <= 0) return "< 1s"
        val totalSeconds = etaMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}