package com.youme.memoria.onboarding

import android.Manifest
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.youme.memoria.Gallery.IndexingViewModel
import com.youme.memoria.Gallery.IndexingViewModelFactory
import com.youme.memoria.MainActivity
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.settings.FoldersManager.FolderPrefs
import com.youme.memoria.settings.FoldersManager.FoldersManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.getValue

class SetupScreen : AppCompatActivity() {

    private lateinit var repo: PhotoRepository
    private lateinit var folderPref : FolderPrefs
    private val indexingViewModel: IndexingViewModel by viewModels(){
        IndexingViewModelFactory(repo, this.applicationContext)
    }
    private val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PhotoRepository(this)
        folderPref = FolderPrefs(this)
        setContentView(R.layout.setup_screen_layout)
        requestGalleryPermission()
        setupFolderSelection()
        setupMediaAccess()
        setupFinal()
    }

    private fun setupFinal(){
        val continueButton = findViewById<Button>(R.id.button16)
        lifecycleScope.launch {
            combine(indexingViewModel.isPermissionGrandted,folderPref.selectedBuckets){hasMediaAccess,selectedFolders->
                hasMediaAccess && (selectedFolders.isNotEmpty())
            }.collectLatest {isGranted->
                continueButton.isEnabled =isGranted
            }
        }

        continueButton.setOnClickListener {
            lifecycleScope.launch {
                OnboardingPrefs.setOnboardingDone(this@SetupScreen, true)
                val intent = Intent(this@SetupScreen, MainActivity::class.java)
                intent.flags=Intent.FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }

        }
    }

    private fun setupMediaAccess(){
        val permissionButton = findViewById<MaterialButton>(R.id.perButton)
        lifecycleScope.launch {
            indexingViewModel.isPermissionGrandted.collectLatest { isGranted->
                permissionButton.isEnabled = !isGranted
                permissionButton.text = if (isGranted) "Permission Granted" else "Grant Media Permission"
                findViewById<MaterialButton>(R.id.SelFolder).isEnabled=isGranted

                indexingViewModel.getAvailableFolders()

            }
        }

        permissionButton.setOnClickListener {
            requestPermissionLauncher.launch(
                permission
            )
        }
    }

    private fun setupFolderSelection(){

        val foldersSelectionButton =findViewById<MaterialButton>(R.id.SelFolder)
        val selectedTextInto = findViewById<TextView>(R.id.textView31)





        foldersSelectionButton.setOnClickListener {
            FoldersManager().show(supportFragmentManager,"")
        }



        lifecycleScope.launch {
            indexingViewModel.folderPrefs.selectedBuckets.collectLatest {selectedFolders->
                selectedTextInto.text = "${selectedFolders.size} Selected"
            }
        }





    }
    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                indexingViewModel.setPermission(true)
            } else {
                Toast.makeText(
                    this@SetupScreen,
                    "Gallery permission denied",
                    Toast.LENGTH_SHORT
                ).show()
                indexingViewModel.setPermission(false)
            }
        }
    private fun requestGalleryPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this@SetupScreen,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                lifecycleScope.launch {
                    indexingViewModel.setPermission(true)
                }
            }
        }
    }


}