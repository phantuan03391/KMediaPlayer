package com.kyoapp.kmedia.fragment


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.kyoapp.kmedia.R

class DetailMediaFragment : Fragment() {

    companion object {
        fun newInstance(): DetailMediaFragment {
            return DetailMediaFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_detail_media, container, false)
    }

}
