package app.simple.inure.ui.viewers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import app.simple.inure.R
import app.simple.inure.decorations.popup.PopupLinearLayout
import app.simple.inure.decorations.ripple.DynamicRippleImageButton
import app.simple.inure.decorations.views.CustomWebView
import app.simple.inure.decorations.views.TypeFaceTextView
import app.simple.inure.dialogs.miscellaneous.ErrorPopup
import app.simple.inure.extension.fragments.ScopedFragment
import app.simple.inure.popups.app.PopupXmlViewer
import app.simple.inure.viewmodels.factory.TextDataFactory
import app.simple.inure.viewmodels.viewers.TextViewerData
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class HtmlViewer : ScopedFragment() {

    private lateinit var html: CustomWebView
    private lateinit var path: TypeFaceTextView
    private lateinit var options: DynamicRippleImageButton

    private lateinit var textViewerData: TextViewerData
    private lateinit var textDataFactory: TextDataFactory

    private var htmlTxt: String = ""

    private val exportText = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        if (uri == null) {
            // Back button pressed.
            return@registerForActivityResult
        }
        try {
            requireContext().contentResolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) throw IOException()
                outputStream.write(htmlTxt.toByteArray())
                outputStream.flush()
                Toast.makeText(requireContext(), R.string.saved_successfully, Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), R.string.failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_html_viewer, container, false)

        html = view.findViewById(R.id.html_viewer)
        path = view.findViewById(R.id.html_name)
        options = view.findViewById(R.id.html_viewer_options)
        applicationInfo = requireArguments().getParcelable("application_info")!!

        textDataFactory = TextDataFactory(
            applicationInfo,
            requireArguments().getString("path")!!,
            requireActivity().application,
        )

        textViewerData = ViewModelProvider(this, textDataFactory).get(TextViewerData::class.java)

        path.text = requireArguments().getString("path")!!
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startPostponedEnterTransition()

        textViewerData.getText().observe(viewLifecycleOwner, {
            runCatching {
                htmlTxt = it
                html.loadData(it, "text/html", "UTF-8")
            }.getOrElse {
                val e = ErrorPopup.newInstance(it.message!!)
                e.show(childFragmentManager, "error_dialog")
                e.setOnErrorDialogCallbackListener(object : ErrorPopup.Companion.ErrorDialogCallbacks {
                    override fun onDismiss() {
                        requireActivity().onBackPressed()
                    }
                })
            }
        })

        options.setOnClickListener {
            val p = PopupXmlViewer(LayoutInflater.from(requireContext())
                                           .inflate(R.layout.popup_xml_options,
                                                    PopupLinearLayout(requireContext()),
                                                    true), it)

            p.setOnPopupClickedListener(object : PopupXmlViewer.PopupXmlCallbacks {
                override fun onPopupItemClicked(source: String) {
                    when (source) {
                        getString(R.string.copy) -> {
                            val clipboard: ClipboardManager? = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                            val clip = ClipData.newPlainText("xml", htmlTxt)
                            clipboard?.setPrimaryClip(clip)
                        }
                        getString(R.string.save) -> {
                            val fileName: String = applicationInfo.packageName + "_" + path.text
                            exportText.launch(fileName)
                        }
                    }
                }
            })
        }
    }

    companion object {
        fun newInstance(applicationInfo: ApplicationInfo, path: String): HtmlViewer {
            val args = Bundle()
            args.putParcelable("application_info", applicationInfo)
            args.putString("path", path)
            val fragment = HtmlViewer()
            fragment.arguments = args
            return fragment
        }
    }
}
