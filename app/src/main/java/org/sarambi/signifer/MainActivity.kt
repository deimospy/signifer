package org.sarambi.signifer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.sarambi.signifer.databinding.ActivityMainBinding

/** La unica actividad. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
