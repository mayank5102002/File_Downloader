package com.example.filedownloader.ui.main

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filedownloader.R
import com.example.filedownloader.databinding.FragmentMainBinding
import com.example.filedownloader.models.DownloadModel
import com.example.filedownloader.models.DownloadStatus
import com.example.filedownloader.models.DownloadsAdapter
import com.example.filedownloader.models.NetworkPreference
import com.example.filedownloader.services.DownloadService
import com.example.filedownloader.utils.SharedPreferencesUtil
import com.google.android.material.slider.Slider
import java.util.*

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val PICK_FOLDER_REQUEST_CODE = 123

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: FragmentMainBinding

    private var serviceBound = MutableLiveData(false)
    private var downloadService: DownloadService? = null

    private lateinit var adapter : DownloadsAdapter
    private lateinit var layoutManager : LinearLayoutManager
    private lateinit var recyclerView : RecyclerView
    private lateinit var dialog : Dialog
    private lateinit var settingsDialog: Dialog

    //Connection to the service
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            val binder = service as DownloadService.MyBinder
            downloadService = binder.getService()
            serviceBound.postValue(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound.postValue(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater)

        init()

        return binding.root
    }

    //Init
    private fun init(){
        viewModel = MainViewModel.getInstance()
        dialog = Dialog(requireContext())
        settingsDialog = Dialog(requireContext())

        initAdapter()
        showDefaultViews()
        startService()
        initObservers()
        initListeners()
    }

    //Initialising adapter
    private fun initAdapter() {
        recyclerView = binding.downloadsRecyclerView
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        adapter = DownloadsAdapter(getDataList(viewModel.showableDownloads.value!!), createDownloadClickListener(), requireContext())
        recyclerView.adapter = adapter
    }

    //Creating click listener for adapter
    private fun createDownloadClickListener() : DownloadsAdapter.OnItemClickListener{
        return object : DownloadsAdapter.OnItemClickListener {

            //On cancel button click
            override fun onCancelButtonClick(position: Int) {
                val download = adapter.dataList[position]
                downloadService?.cancelDownload(download.id)
            }

            //On download item click
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onViewClick(position: Int) {
                Log.i("TAG", "onViewClick: $position")
                val download = adapter.dataList[position]
                if(download.status == DownloadStatus.COMPLETED){
                    openFile(requireContext(), download.folderUri, download.fileName, download.fileExtension)
                } else if(download.status == DownloadStatus.FAILED){
                    restartDownload(download.id)
                } else if(download.status == DownloadStatus.PAUSED){
                    resumeDownload(download.id)
                } else if(download.status == DownloadStatus.DOWNLOADING){
                    pauseDownload(download.id)
                } else if(download.status == DownloadStatus.PENDING){
                    cancelDownload(download.id)
                }
            }
        }
    }

    //Opening file with default app
    fun openFile(context: Context, folderUri: Uri, fileName: String, fileExtension: String) {
        val fileUri = Uri.withAppendedPath(folderUri, fileName)

        val mimeType = getMimeType(fileExtension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No handler for this type of file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(fileExtension: String): String? {
        return if (fileExtension.isNotEmpty()) {
            val mimeTypeMap = MimeTypeMap.getSingleton()
            mimeTypeMap.getMimeTypeFromExtension(fileExtension.lowercase(Locale.ROOT))
        } else {
            null
        }
    }

    //Getting data list from hashmap
    private fun getDataList(data : HashMap<String, DownloadModel>) : List<DownloadModel> {
        val list = mutableListOf<DownloadModel>()
        for (item in data) {
            list.add(item.value)
        }
        return list
    }

    //Starting download service
    private fun startService(){
        val serviceIntent = Intent(requireContext(), DownloadService::class.java)
        requireContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        // Start the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    //Initialising observers
    private fun initObservers(){
        viewModel.downloadListReady.observe(viewLifecycleOwner) { res ->
            if(res){
                adapter.dataList = getDataList(viewModel.showableDownloads.value!!).sortedBy { it.time }.reversed()
                adapter.notifyDataSetChanged()
                if(viewModel.showableDownloads.value!!.size == 0){
                    showDefaultViews()
                }else{
                    removeDefaultViews()
                }
                initDownloadsListener()
            }
        }
        viewModel.fileDetails.observe(viewLifecycleOwner) {
            if(it != null){
                dialog.dismiss()
                showFileDetailsDialogBox()
            }
        }
        viewModel.downloadTaskPartialsReady.observe(viewLifecycleOwner) {
            if(it){
                viewModel.filterDownloads()
            }
        }
    }

    //Initialising downloads listener
    private fun initDownloadsListener(){
        viewModel.showableDownloads.observe(viewLifecycleOwner) { res ->
//            Log.i("MainFragment", "initDownloadsListener: ${it.size}")
            adapter.dataList = getDataList(res).sortedBy { it.time }.reversed()
            adapter.notifyDataSetChanged()
            if(res.size == 0){
                showDefaultViews()
            }else{
                removeDefaultViews()
            }
        }
    }

    //Initialising listners
    private fun initListeners(){
        binding.menuButton.setOnClickListener {
            showPopupMenu(it)
        }
        binding.addLinkButton.setOnClickListener {
            showAddDownloadDialog()
        }
    }

    //Showing settings dialog box
    private fun showSettingsDialogBox(){
        settingsDialog = Dialog(requireContext())
        settingsDialog.setContentView(R.layout.dialog_settings)

        val window = settingsDialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.verticalMargin = 0f

        val closeButton : Button = settingsDialog.findViewById(R.id.closeButton)
        val updateButton : Button = settingsDialog.findViewById(R.id.updateButton)
        val slider : Slider = settingsDialog.findViewById(R.id.slider)

        slider.value = SharedPreferencesUtil.getMaxParallelDownloads(requireContext()).toFloat()

        //Close button for settings
        closeButton.setOnClickListener {
            settingsDialog.dismiss()
        }

        //Update button for settings
        updateButton.setOnClickListener {
            SharedPreferencesUtil.setMaxParallelDownloads(requireContext(), slider.value.toInt())
            settingsDialog.dismiss()
        }

        settingsDialog.show()
    }

    //Showing file details dialog box
    private fun showAddDownloadDialog() {
        dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_add_download)

        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.verticalMargin = 0f

        // Find views in the custom dialog layout
        val linkEditText: EditText = dialog.findViewById(R.id.linkEditText)
        val addButton: Button = dialog.findViewById(R.id.addButton)
        val cancelButton: Button = dialog.findViewById(R.id.cancelButton)
        val consLayout : ConstraintLayout = dialog.findViewById(R.id.dialogConstraintLayout)
        val progressll : LinearLayout = dialog.findViewById(R.id.dialogProgressLinearLayout)
        progressll.visibility = View.GONE

        // Set a click listener for the "Add" button for adding a download
        addButton.setOnClickListener {
            // Handle the "Add" button click
            val downloadLink = linkEditText.text.toString()

            if (downloadLink.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a link", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressll.visibility = View.VISIBLE
            consLayout.visibility = View.GONE
            viewModel.getFileDetails(downloadLink)
        }

        // Set a click listener for the "Cancel" button for cancelling new download
        cancelButton.setOnClickListener {
            dialog.dismiss() // Cancel button is pressed, dismiss the dialog
        }

        linkEditText.requestFocus()

        dialog.show()
    }

    //Show file details dialog box
    private fun showFileDetailsDialogBox(){
        dialog.setContentView(R.layout.dialog_file_details)

        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.verticalMargin = 0f

        // Find views in the custom dialog layout
        val addButton: Button = dialog.findViewById(R.id.addButton)
        val cancelButton: Button = dialog.findViewById(R.id.cancelButton)
        val fileNameEditText: EditText = dialog.findViewById(R.id.fileNameEditText)
        val fileExtension : TextView = dialog.findViewById(R.id.extensiontv)
        val fileSize : TextView = dialog.findViewById(R.id.sizetv)
        val downloadOverWifiCheckBox = dialog.findViewById<CheckBox>(R.id.wifiCheckBox)
        val destinationFolderButton = dialog.findViewById<Button>(R.id.destinationFolderButton)

        fileNameEditText.setText(viewModel.fileDetails.value!!.first)
        fileExtension.text = viewModel.fileDetails.value!!.second!!.uppercase()
        if(viewModel.fileDetails.value!!.third != null) {
            fileSize.text = viewModel.fileDetails.value!!.third.toString()
        }

        if(viewModel.destinationFolderSelected.value == true) {
            destinationFolderButton.text = viewModel.destinationFolder.value!!.path?.dropWhile { it != ':' }?.drop(1)
        }

        destinationFolderButton.setOnClickListener {
            openFolderPicker()
        }

        // Set a click listener for the "Add" button for adding a download
        addButton.setOnClickListener {
            // Handle the "Add" button click
            val fileName = fileNameEditText.text.toString()
            val networkPrefence : NetworkPreference = if(downloadOverWifiCheckBox.isChecked){
                NetworkPreference.WIFI_ONLY
            }else{
                NetworkPreference.WIFI_AND_CELLULAR
            }

            if (fileName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if(viewModel.destinationFolderSelected.value == false){
                Toast.makeText(requireContext(), "Please select a destination folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addDownload(viewModel.fileUrl, fileName, viewModel.fileDetails.value!!.second!!,
                viewModel.destinationFolder.value!!, networkPrefence, createAddDownloadListener())
        }

        // Set a click listener for the "Cancel" button for cancelling new download
        cancelButton.setOnClickListener {
            viewModel.afterFileDetailsUpdated()
            dialog.dismiss() // Cancel button is pressed, dismiss the dialog
        }

        dialog.show()
    }

    interface AddDownloadListener{
        fun successfullyAdded()
        fun failedToAdd(message : String)
    }

    //Create add download listener
    private fun createAddDownloadListener() : AddDownloadListener{
        return object : AddDownloadListener{

            //Download added successfully
            override fun successfullyAdded() {
                dialog.dismiss()
                viewModel.afterFileDetailsUpdated()
                showSuccessfulAddedDialogBox()
            }

            //Download failed to add
            override fun failedToAdd(message: String) {
                viewModel.afterFileDetailsUpdated()
                dialog.dismiss()
                showFailedToAddDialogBox(message)
            }
        }
    }

    //Show successful added dialog box
    private fun showSuccessfulAddedDialogBox(){
        dialog.setContentView(R.layout.dialog_success_add)

        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.verticalMargin = 0f

        // Find views in the custom dialog layout
        val doneButton: Button = dialog.findViewById(R.id.addButton)

        doneButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    //Show failed to add dialog box
    private fun showFailedToAddDialogBox(message: String){
        dialog.setContentView(R.layout.dialog_failed_add)

        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.verticalMargin = 0f

        // Find views in the custom dialog layout
        val closeButton: Button = dialog.findViewById(R.id.closeButton)
        val backbutton: Button = dialog.findViewById(R.id.backButton)
        val messageTextView : TextView = dialog.findViewById(R.id.errorTextView)
        messageTextView.text = message

        closeButton.setOnClickListener{
            dialog.dismiss()
        }

        backbutton.setOnClickListener{
            dialog.dismiss()
            viewModel.getFileDetails(viewModel.fileUrl)
        }

        dialog.show()
    }

    //Open folder picker
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE)
    }

    //Handle folder picker result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val folderUri = data?.data
            // Process the selected folder URI
            if (folderUri != null) {
                // Grant persistable permission to the folder
                requireActivity().contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Handle the selected folder URI here
                // Example: display the folder path
                val folderPath = folderUri.path
                viewModel.setDestinationFolder(folderUri)
                dialog.findViewById<Button>(R.id.destinationFolderButton).text = folderPath?.dropWhile { it != ':' }?.drop(1)
            }
        }
    }

    //Show popup menu
    private fun showPopupMenu(view : View){
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.settings_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.pause_all_button -> {
                    // Pause all downloads
                    pauseAllDownloads()
                    true
                }
                R.id.resume_all_button -> {
                    // Resume all downloads
                    resumeAllDownloads()
                    true
                }
                R.id.settings_button -> {
                    // Show settings dialog box
                    showSettingsDialogBox()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    //Remove default views
    private fun removeDefaultViews(){
        binding.noDownloadsImageView.visibility = View.GONE
        binding.noDownloadsTextView.visibility = View.GONE
    }

    //Show default views
    private fun showDefaultViews(){
        binding.noDownloadsImageView.visibility = View.VISIBLE
        binding.noDownloadsTextView.visibility = View.VISIBLE
    }

    //On start
    override fun onStart() {
        Log.i("DownloadService", "onStart")
        super.onStart()
        // Bind to the DownloadService
        val intent = Intent(requireContext(), DownloadService::class.java)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    //On stop
    override fun onStop() {
        Log.i("DownloadService", "onStop")
        super.onStop()
        // Unbind from the DownloadService
        if (serviceBound.value == true) {
            requireContext().unbindService(connection)
            serviceBound.postValue(false)
        }
    }

    // Example usage: Pause a download
    private fun pauseDownload(downloadId: String) {
        if (serviceBound.value!!) {
            downloadService?.pauseDownload(downloadId)
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Resume a download
    private fun resumeDownload(downloadId: String) {
        if (serviceBound.value!!) {
            downloadService?.resumeDownload(downloadId)
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Pause all downloads
    private fun pauseAllDownloads() {
        if (serviceBound.value!!) {
            downloadService?.pauseAllDownloads()
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Resume all downloads
    private fun resumeAllDownloads() {
        if (serviceBound.value!!) {
            downloadService?.resumeAllDownloads()
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Cancel a download
    private fun cancelDownload(downloadId: String) {
        if (serviceBound.value!!) {
            downloadService?.cancelDownload(downloadId)
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Cancel all downloads
    private fun cancelAllDownloads() {
        if (serviceBound.value!!) {
            downloadService?.cancelAllDownloads()
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Add a new download
    private fun addDownload(url: String, fileName: String, fileExtension: String, folderUri : Uri, networkPrefence : NetworkPreference, listener: AddDownloadListener) {
        if (serviceBound.value!!) {
            downloadService?.addDownload(url, fileName, fileExtension, folderUri, networkPrefence, listener)
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

    // Example usage: Restart a download
    @RequiresApi(Build.VERSION_CODES.O)
    private fun restartDownload(downloadId: String) {
        if (serviceBound.value!!) {
            downloadService?.restartDownload(downloadId)
        } else {
            Log.i("DownloadService", "Service not bound")
        }
    }

}