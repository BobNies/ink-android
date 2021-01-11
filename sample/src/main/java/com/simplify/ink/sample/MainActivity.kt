/*
 * Copyright (c) 2016 Mastercard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simplify.ink.sample

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import androidx.databinding.DataBindingUtil
import com.simplify.ink.InkView
import com.simplify.ink.sample.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(
                this,
                R.layout.activity_main
        )

        initToolbar()
    }

    private fun initToolbar() {
        val menu = binding.toolbar.menu
        menuInflater.inflate(R.menu.options, menu)
        var menuItem = menu.findItem(R.id.menu_interpolation)
        menuItem.isChecked = binding.ink.hasFlag(InkView.INTERPOLATION)
        menuItem.setOnMenuItemClickListener { item ->
            item.isChecked = !item.isChecked
            if (item.isChecked) {
                binding.ink.addFlag(InkView.INTERPOLATION)
            } else {
                binding.ink.removeFlag(InkView.INTERPOLATION)
            }
            true
        }

        menuItem = menu.findItem(R.id.menu_responsive)
        menuItem.isChecked = binding.ink.hasFlag(InkView.VELOCITY)
        menuItem.setOnMenuItemClickListener { item ->
            item.isChecked = !item.isChecked
            if (item.isChecked) {
                binding.ink.addFlag(InkView.VELOCITY)
            } else {
                binding.ink.removeFlag(InkView.VELOCITY)
            }
            true
        }

        menuItem = menu.findItem(R.id.menu_clear)
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menuItem.setOnMenuItemClickListener {
            binding.ink.clearCanvas()
            true
        }
    }
}