package com.youme.memoria.Album

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.youme.memoria.Gallery.IndexingViewModel
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.search.SearchViewModuel
import kotlin.getValue


class CreateAlbumBottomFrag : BottomSheetDialogFragment(R.layout.create_album_bottomfrag) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet


    private val viewModuel: AlbumViewModel by activityViewModels()

    private var title : String = ""
    private var startMode: Int = 0
    private var autoUpdate : Boolean=true


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTitle()
        setupStartMode()
        setupAutoUpdate()


        val submitButton =requireView().findViewById<MaterialButton>(R.id.button9)
        val cancelButton = requireView().findViewById<MaterialButton>(R.id.button10)
        submitButton.setOnClickListener {
            if(title.isNotEmpty()){
               viewModuel.createAlbum(title,startMode,autoUpdate)
                dismiss()
            }

        }
        cancelButton.setOnClickListener {
            dismiss()
        }



    }
    private fun setupTitle(){
        val editText = requireView().findViewById<TextInputEditText>(R.id.title_textedit)
        editText.doAfterTextChanged {text->
            title = text.toString()
        }

    }
    private fun setupStartMode(){
        val tablayout = requireView().findViewById<TabLayout>(R.id.startIndixTable)
        tablayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    startMode=tablayout.selectedTabPosition
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )
    }
    private fun setupAutoUpdate(){
        val switch = requireView().findViewById<MaterialSwitch>(R.id.autoUpdateBt)
        switch.setOnClickListener {
            autoUpdate = switch.isChecked
        }
    }
}