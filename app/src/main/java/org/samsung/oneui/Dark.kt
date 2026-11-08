package org.samsung.oneui

import android.app.AlertDialog
import android.content.Context

/** Janelas do app no estilo escuro (igual ao editor de fotos). */
object Dark {
    fun builder(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
}
