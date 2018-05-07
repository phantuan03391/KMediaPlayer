package com.kyoapp.kmedia.util

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity

fun AppCompatActivity.addFragmentToActivity(fragment: Fragment, frameId: Int) {
    supportFragmentManager.transact { add(frameId, fragment) }
}

private fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply(action).commit()
}