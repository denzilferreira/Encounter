package com.awareframework.encounter.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.DataExportService
import kotlinx.android.synthetic.main.fragment_account.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.uiThread

class AccountFragment : Fragment() {

    companion object {
        lateinit var progress: ProgressBar
        lateinit var wait: TextView
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    lateinit var user: User

    override fun onResume() {
        super.onResume()

        progress = find(R.id.export_progress)
        wait = find(R.id.export_wait)

        export_progress.visibility = View.INVISIBLE
        export_wait.visibility = View.INVISIBLE

        doAsync {
            val db =
                Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                    .build()

            user = db.UserDao().getUser().first()
            uiThread {
                uuid_sharing.text = getString(R.string.uuid_placeholder).format(user.uuid)
            }

            db.close()
        }

        uuid_sharing.setOnClickListener {
            val share = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, user.uuid)
                putExtra(Intent.EXTRA_TITLE, "Encounter UUID")
                type = "text/plain"
            }, null)
            startActivity(share)
        }

        btn_share_data.setOnClickListener {
            progress.visibility = View.VISIBLE
            wait.visibility = View.VISIBLE
            context?.startService(Intent(context, DataExportService::class.java))
        }
    }
}