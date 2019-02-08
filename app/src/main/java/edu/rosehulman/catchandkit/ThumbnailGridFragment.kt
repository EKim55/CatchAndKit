package edu.rosehulman.catchandkit

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.R.attr.data
import android.app.Activity
import android.content.DialogInterface
import android.widget.EditText



private const val ARG_COLUMNS = "ARG_COLUMNS"
class ThumbnailGridFragment : Fragment() {
    private var listener: OnThumbnailListener? = null
    private lateinit var adapter: ThumbnailAdapter
    private val RC_TAKE_PICTURE = 1
    private val RC_CHOOSE_PICTURE = 2

    private var currentPhotoPath = ""
    lateinit var rootView: RecyclerView

    private var columns = 3

    private var mActivity: Activity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columns = it.getInt(ARG_COLUMNS)
        }
        currentPhotoPath = savedInstanceState?.getString(Constants.KEY_URL, "") ?: ""
        adapter = ThumbnailAdapter(context!!, listener)
        mActivity = activity!!
        activity!!.fab.setOnClickListener {
            var builder = AlertDialog.Builder(context!!)
            builder.setTitle("Choose a photo source")
            builder.setMessage("Would you like to take a new picture?\nOr choose an existing one?")
            builder.setPositiveButton("Take Picture") { dialogInterface, i ->
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(context!!.packageManager) != null) {
                    startActivityForResult(takePictureIntent, Constants.RC_TAKE_PICTURE);
                }

            }
            builder.setNegativeButton("Choose Picture") { dialogInterface, i ->
                val choosePictureIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                if (choosePictureIntent.resolveActivity(context!!.packageManager) != null) {
                    startActivityForResult(choosePictureIntent, Constants.RC_CHOOSE_PICTURE)
                }
            }
            builder.create().show()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(columns: Int) =
            ThumbnailGridFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMNS, columns)
                }
            }
    }



    private fun sendCapturedPhotoToAdapter(name: String, data: Intent) {
        val bitmap = data.extras!!.get("data") as Bitmap
        val location = MediaStore.Images.Media.insertImage(context!!.contentResolver, bitmap, name, null)
        adapter.addPicture(name, location, bitmap)
    }

    private fun sendGalleryPhotoToAdapter(name: String, data: Intent?) {
        if (data != null && data.data != null) {
            val uri = data.data
            val location = uri!!.toString()
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(mActivity!!.contentResolver, uri)
                adapter.addPicture(name, location, bitmap)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        currentPhotoPath = savedInstanceState?.getString(Constants.KEY_URL, "") ?: ""
//        adapter = ThumbnailAdapter(context!!, listener)
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_thumbnail_grid, container, false) as RecyclerView
        setAdapterWithColumns(columns)
        (activity as MainActivity).fab.setOnClickListener {
            showPictureDialog()
        }
        return rootView
    }

    fun setAdapterWithColumns(columns: Int = 3) {
        rootView.adapter = adapter
        rootView.layoutManager = GridLayoutManager(context, columns)
        rootView.setHasFixedSize(true)
    }

    private fun showPictureDialog() {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("Choose a photo source")
        builder.setMessage("Would you like to take a new picture?\nOr choose an existing one?")
        builder.setPositiveButton("Take Picture") { _, _ ->
            launchCameraIntent()
        }

        builder.setNegativeButton("Choose Picture") { _, _ ->
            launchChooseIntent()
        }
        builder.create().show()
    }

    // Everything camera- and storage-related is from https://developer.android.com/training/camera/photobasics
    private fun launchCameraIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(activity!!.packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    // authority declared in manifest
                    val photoURI: Uri = FileProvider.getUriForFile(
                        context!!,
                        "edu.rosehulman.catchandkit",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, RC_TAKE_PICTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = activity!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun launchChooseIntent() {
        // https://developer.android.com/guide/topics/providers/document-provider
        val choosePictureIntent = Intent(
            Intent.ACTION_OPEN_DOCUMENT,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        choosePictureIntent.addCategory(Intent.CATEGORY_OPENABLE)
        choosePictureIntent.type = "image/*"
        if (choosePictureIntent.resolveActivity(context!!.packageManager) != null) {
            startActivityForResult(choosePictureIntent, RC_CHOOSE_PICTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("What is the name of this picture?")

        val editText = EditText(context)
        builder.setView(editText)

        builder.setPositiveButton("Save") { dialogInterface, i ->
            val name = editText.text.toString()
            when (requestCode) {
                RC_TAKE_PICTURE -> if (resultCode === RESULT_OK) {
                    sendCapturedPhotoToAdapter(name, data!!)
                }
                RC_CHOOSE_PICTURE -> sendGalleryPhotoToAdapter(name, data)
                else -> Log.d("TAG", "Invalid file request code")
            }
        }

        builder.create().show()

    }

    // Works Not working on phone
    private fun addPhotoToGallery() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            activity!!.sendBroadcast(mediaScanIntent)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnThumbnailListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnThumbnailListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(Constants.KEY_URL, currentPhotoPath)
    }

    interface OnThumbnailListener {
        fun onThumbnailSelected(thumbnail: Thumbnail)
    }
}