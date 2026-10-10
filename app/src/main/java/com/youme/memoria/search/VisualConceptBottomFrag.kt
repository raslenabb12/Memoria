package com.youme.memoria.search

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.youme.memoria.R

class VisualConceptBottomFrag : BottomSheetDialogFragment(R.layout.visual_concept_builder_layout) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        view.findViewById<Button>(R.id.cancel).setOnClickListener {
            dismiss()
        }

        setupAddToFind()
        setupAddToExc()


    }

    private fun setupAddToFind(){
        val addFindChip  = requireView().findViewById<Chip>(R.id.addFind)
        val chipGroup = requireView().findViewById<ChipGroup>(R.id.findGroup)
        addFindChip.setOnClickListener {
            addAlert("Add Tag"){tag->
                chipGroup.addView(
                    Chip(requireContext()).apply {
                        text = tag
                    },0
                )
            }
        }

    }
    private fun setupAddToExc(){
        val addFindChip  = requireView().findViewById<Chip>(R.id.addEXC)
        val chipGroup = requireView().findViewById<ChipGroup>(R.id.ExcGroup)
        addFindChip.setOnClickListener {
            addAlert("Add Tag"){tag->
                chipGroup.addView(
                    Chip(requireContext()).apply {
                        text = tag
                    },0
                )
            }
        }

    }

    private fun addAlert(title: String,onClicked : (String)-> Unit){
        val inputLayout = TextInputLayout(requireContext()).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(24, 0, 24, 0)
        }
        val textInput = TextInputEditText(requireContext()).apply {
            hint = "ex: Beach, sun, green..."
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }

        inputLayout.addView(textInput)
        MaterialAlertDialogBuilder(requireContext(),R.style.MyAlertDialogTheme)
            .setView(inputLayout)
            .setTitle(title)
            .setPositiveButton("Add"){_,_->
                val tag= textInput.text.toString()
                if (tag.isNotEmpty()) onClicked(tag)

            }
            .setNeutralButton("Cancel"){_,_-> }
            .show()

    }


}