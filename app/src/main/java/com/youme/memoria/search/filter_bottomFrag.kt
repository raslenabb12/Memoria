package com.youme.memoria.search

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.LayoutDirection
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.youme.memoria.Gallery.FolderInfo
import com.youme.memoria.PhotoRepository
import com.youme.memoria.Preset
import com.youme.memoria.R
import com.youme.memoria.SearchFilters
import com.youme.memoria.settings.FoldersManager.FoldersAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone.getTimeZone

class FilterBottomFrag(): BottomSheetDialogFragment(R.layout.filter_layout) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet

    private lateinit var repo : PhotoRepository
    private val viewModuel: SearchViewModuel by activityViewModels()

    private lateinit var searchFilters: SearchFilters
    private var selectedFolders = mutableListOf<String>()
    private var startDateFilter : Long? = null
    private var preset  : Preset= Preset.BALANCED
    private var endDateFilter : Long?  = null


    private lateinit var Adapter : FoldersAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = PhotoRepository(requireContext())



        lifecycleScope.launch {
            viewModuel.searchfilters.collectLatest {
                selectedFolders = it.folders.toMutableList()
                startDateFilter = it.startDate
                endDateFilter  =  it.endDate
                searchFilters = it
                preset = it.confidence
                setupUi()
            }
        }

        view.findViewById<Button>(R.id.button7).setOnClickListener {
            lifecycleScope.launch {
                selectedFolders=mutableListOf()
                startDateFilter=null
                endDateFilter=null
                setupUi()
            }
        }
    }
    private fun setupUi(){
        lifecycleScope.launch {
            setupFolders()
            //camerasList()
            dateFilter()
            setupConfidenceSlider()
            setupSubmit()
        }
    }
    private fun setupSubmit(){
        val applyBt = requireView().findViewById<Button>(R.id.button6)

        applyBt.setOnClickListener {
            val searchFilters = searchFilters.copy(folders = selectedFolders, startDate = startDateFilter, endDate = endDateFilter, confidence =  preset)
            viewModuel.setSearchFilters(searchFilters)
            dismiss()
        }
    }
    private fun dateFilter(){
        val startDate  = requireView().findViewById<View>(R.id.startDate)
        val endDate = requireView().findViewById<View>(R.id.endDate)

        val startDateText = requireView().findViewById<TextInputEditText>(R.id.edit1).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
        }
        val endDateText = requireView().findViewById<TextInputEditText>(R.id.edit2).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
        }

        startDateText.setText(returnFormatedDate(startDateFilter))
        endDateText.setText(returnFormatedDate(endDateFilter))


        startDate.setOnLongClickListener {
            startDateFilter=null
            startDateText.setText("mm/dd/yyyy")
            true
        }
        endDate.setOnLongClickListener {
            endDateFilter=null
            endDateText.setText("mm/dd/yyyy")
            true
        }


        startDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select start date")
                .setTheme(R.style.ThemeOverlay_Memoria_DatePicker)
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                startDateFilter = selection
                startDateText.setText(returnFormatedDate(selection))
            }
            datePicker.show(parentFragmentManager, "START_DATE_PICKER")
        }
        endDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select end date")
                .setTheme(R.style.ThemeOverlay_Memoria_DatePicker)
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                endDateFilter = selection
                endDateText.setText(returnFormatedDate(selection))
            }

            datePicker.show(parentFragmentManager, "END_DATE_PICKER")
        }


    }
    private fun setupConfidenceSlider(){
        val presetGroup = requireView().findViewById<ChipGroup>(R.id.presetGroup)
        presetGroup.children.forEach {
            val chip = it as Chip
            val value = when (chip.id) {
                R.id.chipLoose -> Preset.LOOSE
                R.id.chipBalanced -> Preset.BALANCED
                R.id.chipStrict -> Preset.STRICT
                else -> 0 }
            if (value == preset){
                chip.isChecked=true
            }
        }

        presetGroup.setOnCheckedStateChangeListener { group, ids  ->
            val value = when (ids.firstOrNull()) {
                R.id.chipLoose -> Preset.LOOSE
                R.id.chipBalanced -> Preset.BALANCED
                R.id.chipStrict -> Preset.STRICT
                else -> return@setOnCheckedStateChangeListener
            }
            preset = value
        }


    }
    private fun returnFormatedDate(timeInMillis: Long?): String {
        if (timeInMillis == null) return "mm/dd/yyyy"
        val calendar = Calendar.getInstance(getTimeZone("UTC"))
        calendar.timeInMillis = timeInMillis
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)
        return "$month/$day/$year"
    }
//    private suspend fun camerasList(){
//        val allCameras  = repo.getCameraList()
//        val camerasContainer = requireView().findViewById<ChipGroup>(R.id.camera_container)
//
//        camerasContainer.removeAllViews()
//
//        allCameras.forEach { camera->
//            val chip = Chip(requireContext()).apply {
//                text=camera
//                isCheckable=true
//            }
//            camerasContainer.addView(chip)
//
//
//        }
//    }
    private suspend fun setupFolders(){

        val folders = repo.getFoldersList().map { FolderInfo(it.folderPath,it.folderPath.substringBeforeLast("/").substringAfterLast("/"),it.folderPath,it.count.toInt()) }
        Adapter = FoldersAdapter()

        Adapter.submitData(folders)

        Adapter.setSelected(selectedFolders.toSet())

        val recyclerView = requireView().findViewById<RecyclerView>(R.id.rec)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL,false)
            adapter = Adapter
        }

        lifecycleScope.launch {
            Adapter.selectedFoldersFlow.collectLatest { selected->
                selectedFolders = selected.toMutableList()
            }
        }

    }
}