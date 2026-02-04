package com.demo.vida

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.demo.vida.ui.theme.VidaTheme

// Sealed class to represent the different permission states
sealed class PermissionStatus {
    object Loading : PermissionStatus()
    object Granted : PermissionStatus()
    object Denied : PermissionStatus()
}

class MainActivity : ComponentActivity() {

    private var permissionStatus by mutableStateOf<PermissionStatus>(PermissionStatus.Loading)
    private lateinit var webView: WebView

    // --- File Chooser State (CRITICAL FIX FOR FILE UPLOADS) ---
    // 1. Holds the callback provided by the WebView for when the user selects a file.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 2. Launcher to handle the result of the native file selection Intent.
    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Determine the result URI(s) from the Intent data
            val results: Array<Uri>? = if (result.data == null || result.resultCode != RESULT_OK) {
                null
            } else {
                result.data?.data?.let { arrayOf(it) } ?: result.data?.clipData?.let { clipData ->
                    // Handle multiple files if selected
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
            }

            // Pass the result back to the WebView's JavaScript via the stored callback
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null // Clear the callback after use
        }
    // -----------------------------------------------------------

    // Use the ActivityResultLauncher for a modern way to request permissions
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
            // Check if ALL mandatory permissions were granted
            val allPermissionsGranted = isGranted.values.all { it }
            if (allPermissionsGranted) {
                permissionStatus = PermissionStatus.Granted
                Toast.makeText(this, "All required permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                permissionStatus = PermissionStatus.Denied
                Toast.makeText(this, "One or more permissions were denied. Check logs for details.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // If the WebView cannot go back, disable this callback and let the default back press behavior occur
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    // Re-enable the callback if you want it to be active for future back presses
                    // isEnabled = true
                }
            }
        })

        setContent {
            VidaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Check the current permission status and show the appropriate UI
                    when (permissionStatus) {
                        is PermissionStatus.Loading, is PermissionStatus.Denied -> {
                            PermissionHandler(permissionStatus, requestPermissionLauncher) { status ->
                                permissionStatus = status
                            }
                        }
                        is PermissionStatus.Granted -> {
                            MyWebView(
                                onWebViewReady = { webViewInstance ->
                                    webView = webViewInstance
                                },
                                // Inject dependencies required for file upload handling
                                fileChooserLauncher = fileChooserLauncher,
                                setFilePathCallback = { callback -> filePathCallback = callback }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionHandler(
    permissionStatus: PermissionStatus,
    requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    onStatusChange: (PermissionStatus) -> Unit // Callback to update state in MainActivity
) {
    val context = LocalContext.current

    // Dynamically build the list of permissions required based on Android version
    val permissionsToRequest = remember {
        mutableListOf<String>().apply {
            // --- LOCATION PERMISSIONS (NEWLY ADDED) ---
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            // --- CORE APP PERMISSIONS ---
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)

            // --- STORAGE/MEDIA PERMISSIONS (Handle API 33+ vs older) ---
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        }.toTypedArray()
    }

    LaunchedEffect(Unit) {
        val ungrantedPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (ungrantedPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(ungrantedPermissions)
        } else {
            // Permissions were already granted, so update the state directly
            onStatusChange(PermissionStatus.Granted)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Show different UI based on the permission status
        when (permissionStatus) {
            is PermissionStatus.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Checking permissions, including Location...",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            is PermissionStatus.Denied -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Permissions (including Location) are required to use this application. Please grant all permissions to continue.",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = {
                        // Launch the permission request again
                        requestPermissionLauncher.launch(permissionsToRequest)
                    }) {
                        Text("Grant Permissions")
                    }
                }
            }
            is PermissionStatus.Granted -> {
                // The main activity will handle this state
            }
        }
    }
}

// Custom WebViewClient to intercept URLs and launch external apps (like Maps)
class VidaWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url.toString()

        // 1. Check if the URL is a Google Maps link (using the standard HTTPS format)
        if (url.startsWith("https://www.google.com/maps/") || url.startsWith("https://maps.google.com/") || url.startsWith("geo:")) {
            try {
                // Manually create an Intent to view the URI
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())

                // Add this flag to ensure the activity is started in a new task (necessary for external apps)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Launch the external application (which triggers the Maps prompt)
                view?.context?.startActivity(intent)

                // Return true to indicate that we handled the navigation and the WebView should NOT load the URL internally.
                return true
            } catch (e: ActivityNotFoundException) {
                // Log and optionally show a toast if no app can handle the URL
                Log.e("VidaWebViewClient", "No app found to handle Maps URL: $url", e)
                Toast.makeText(view?.context, "Could not open map. Please ensure Google Maps is installed.", Toast.LENGTH_LONG).show()
                // Return false to let the WebView try to load the URL as a regular page (fallback)
                return false
            }
        }

        // 2. For all other URLs (e.g., vidamedicare.blogspot.com),
        // return false to let the WebView handle the loading internally.
        return false
    }
}

// Custom WebChromeClient to handle browser events, including Geolocation and File Chooser
class VidaWebChromeClient(
    // Dependencies injected from MainActivity for file upload
    private val fileChooserLauncher: ActivityResultLauncher<Intent>,
    private val setFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit
) : WebChromeClient() {

    // Existing Geolocation handler
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        // Since the main Android location permission is handled by PermissionHandler,
        // we can grant this access to the specific web origin automatically.
        callback?.invoke(origin, true, false) // Grant access, and don't remember the preference
    }

    // NEW: File Chooser handler for <input type="file">
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        // 1. Store the callback using the lambda passed from the Activity
        setFilePathCallback(filePathCallback)

        // 2. Create and launch the intent to open the native file picker
        val intent = fileChooserParams?.createIntent()
        if (intent != null) {
            try {
                fileChooserLauncher.launch(intent)
            } catch (e: Exception) {
                // Handle failure to launch the intent
                setFilePathCallback(null) // Clear the callback if launch fails
                Log.e("VidaWebChromeClient", "Cannot open file chooser", e)
                return false
            }
        } else {
            setFilePathCallback(null)
            return false
        }

        return true
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    VidaTheme {
        // NOTE: Preview cannot properly run MyWebView without the required activity result launchers.
        Text("WebView Preview requires launcher dependencies.")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MyWebView(
    modifier: Modifier = Modifier,
    onWebViewReady: (WebView) -> Unit,
    fileChooserLauncher: ActivityResultLauncher<Intent>, // NEW: Dependency for file upload
    setFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit // NEW: Dependency for file upload
) {

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT

                // START: FIXES FOR CHATBOT WIDGET VISIBILITY
                // These two settings are CRITICAL for correctly positioning 'position: fixed' elements
                // relative to the device viewport, preventing them from disappearing.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                // CRITICAL NEW FIX: Force WebView to use hardware acceleration for rendering.
                // This improves performance and fixes positioning issues for fixed elements.
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                // END: FIXES FOR CHATBOT WIDGET VISIBILITY

                // Enable Geolocation for the WebView
                settings.setGeolocationEnabled(true)

                // Set the custom WebChromeClient to handle Geolocation AND File Chooser requests
                webChromeClient = VidaWebChromeClient(
                    fileChooserLauncher = fileChooserLauncher,
                    setFilePathCallback = setFilePathCallback
                )

                // *** CRITICAL UPDATE: Apply the custom WebViewClient for URL interception ***
                webViewClient = VidaWebViewClient()

                loadUrl("https://smartagrigo.blogspot.com/")
                onWebViewReady(this) // Pass the WebView instance back
            }
        }
    )
}
