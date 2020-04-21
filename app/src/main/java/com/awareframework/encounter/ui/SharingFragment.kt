package com.awareframework.encounter.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.awareframework.encounter.EncounterHome
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.DataExportService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_sharing.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.uiThread
import java.util.*

class SharingFragment : Fragment() {

    companion object {
        lateinit var progress: ProgressBar
        lateinit var wait: TextView
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sharing, container, false)
    }

    lateinit var user: User

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                putExtra(Intent.EXTRA_TITLE, getString(R.string.your_encounter_uuid))
                type = "text/plain"
            }, null)
            startActivity(share)
        }

        btn_share_data.setOnClickListener {
            progress.visibility = View.VISIBLE
            wait.visibility = View.VISIBLE
            context?.startService(Intent(context, DataExportService::class.java))
        }

        btn_delete_data.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.delete_data))
                .setMessage(getString(R.string.delete_confirm))
                .setPositiveButton(getString(R.string.yes)) { dialog, which ->
                    doAsync {
                        val db =
                            Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                                .build()

                        db.EncounterDao().clear()

                        val user = db.UserDao().getUser().first()
                        user.uuid = UUID.randomUUID().toString()
                        db.UserDao().update(user)

                        uiThread {
                            toast(getString(R.string.delete_complete))
                            dialog.dismiss()
                        }

                        db.close()

                        startActivity(Intent(context, EncounterHome::class.java))
                    }
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                    dialog.cancel()
                }
                .show()
        }
    }
}