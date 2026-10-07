package com.cybereun.dayflow

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.cybereun.dayflow.data.*
import com.cybereun.dayflow.ui.DayflowApp
import kotlinx.coroutines.launch

class PlannerViewModel(application:Application):AndroidViewModel(application) {
    private val database=Room.databaseBuilder(application,PlannerDatabase::class.java,"dayflow.db").build()
    val repository=PlannerRepository(database)
    init {viewModelScope.launch{repository.load()}}
    fun edit(block:(PlannerDocument)->PlannerDocument){viewModelScope.launch{repository.mutate(block)}}
    override fun onCleared(){database.close();super.onCleared()}
}
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=true
            isAppearanceLightNavigationBars=true
        }
        setContent {val model:PlannerViewModel=viewModel();DayflowApp(model)}
    }
}
