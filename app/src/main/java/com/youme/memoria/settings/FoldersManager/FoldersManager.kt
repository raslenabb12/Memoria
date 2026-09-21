package com.youme.memoria.settings.FoldersManager

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.youme.memoria.Gallery.IndexingViewModel
import com.youme.memoria.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FoldersManager : BottomSheetDialogFragment(R.layout.folders_manager_layout) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet
    private val indexingViewModel: IndexingViewModel by activityViewModels()
    private lateinit var Adapter : FoldersAdapter

    @SuppressLint("StringFormatInvalid")
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        bottomSheet.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            behavior.skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val folderPref = indexingViewModel.folderPrefs
        val saveButton = requireView().findViewById<MaterialButton>(R.id.button17)
        Adapter= FoldersAdapter(emptyList())
        setupRecyclerView()

        lifecycleScope.launch {
            folderPref.selectedBuckets.collectLatest {selectedBuckets->
                Adapter.setSelected(selectedBuckets)

            }
        }
        saveButton.setOnClickListener {
            lifecycleScope.launch {
                folderPref.setSelectedBuckets(Adapter.selectedFoldersFlow.value)
                dismiss()
            }
        }



        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    folderPref.selectedBuckets,
                    Adapter.selectedFoldersFlow
                ) { savedInDatastore, currentInAdapter ->
                    savedInDatastore != currentInAdapter
                }.collectLatest { isDifferent ->
                    saveButton.isVisible = isDifferent



                }

            }
        }


    }

    private fun setupRecyclerView(){
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter=Adapter
        }
        lifecycleScope.launch {
            indexingViewModel.folders.collectLatest {folders->
                Adapter.submitData(folders)

            }
        }


    }
}