package pt.comandoprojetor

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComandoTheme { App(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.retomar()
    }

    // Os botões de volume do telemóvel controlam o volume do projetor
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (vm.state.value.ecra == Ecra.COMANDO &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            vm.tecla(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
