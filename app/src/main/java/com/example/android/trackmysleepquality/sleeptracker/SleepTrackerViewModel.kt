/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    /*
    *  To use coroutines in kotlin, you need three things:
    *  1. A Job
    *  2. A Dispatcher
    *  3. A Scope
    * */

    // A Job
    private var viewModelJob = Job()

    // A Scope: It needs a dispatcher and Job as parameter:
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)// Dispatchers.Main is a dispatcher.

    private val nights = database.getAllNights()

    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }

    private var tonight = MutableLiveData<SleepNight?>()

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    init {
        initialiseTonight()
    }

    // implement initialiseTonight() with the uiScope to launch a coroutine.
    private fun initialiseTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(newNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(newNight)
        }

    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value
                    ?: return@launch // return@launch syntax specifies the function from which this statement returns, amont several nested functions.
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(oldNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(oldNight)
        }
    }

    fun onClearSleepData() {
        uiScope.launch {
            clearData()
            tonight.value = null

            _showSnackBar.value = true
        }
    }

    private suspend fun clearData() {
        withContext(Dispatchers.IO) {
            database.clear()
        }

    }

    fun doneNavigating(){
        _navigateToSleepQuality.value = null
    }


    val startButtonState = Transformations.map(tonight){
        it==null
    }

    val stopButtonState = Transformations.map(tonight){
        it!=null
    }

    val clearButtonState = Transformations.map(nights){
        it?.isNotEmpty()
    }

    //For Snack-bar:
    private val _showSnackBar = MutableLiveData<Boolean>()
    val showSnackBar:LiveData<Boolean>
    get() = _showSnackBar

    fun doneShowingSnackBar(){
        _showSnackBar.value = false
    }

    override fun onCleared() {
        super.onCleared()

        viewModelJob.cancel()
    }
}

