package androidx.activity
import android.content.Context
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
open class ComponentActivity : android.app.Activity() {
    fun <I, O> registerForActivityResult(contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>): ActivityResultLauncher<I> = TODO()
    override fun onCreate(savedInstanceState: android.os.Bundle?) {}
    override fun onDestroy() {}
}
inline fun <reified VM : ViewModel> ComponentActivity.viewModels(): Lazy<VM> = TODO()
