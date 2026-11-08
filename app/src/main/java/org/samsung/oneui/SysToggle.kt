package org.samsung.oneui

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Roda comandos de shell pelo Shizuku, para a central de controle ligar/desligar sem abrir configuracoes. */
object SysToggle {
    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun ready(): Boolean = try {
        running() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun ask() {
        try {
            Shizuku.requestPermission(7)
        } catch (e: Throwable) {
        }
    }

    /** Executa o comando como shell. Bloqueia ate terminar: chamar fora da thread principal. */
    fun run(cmd: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        p.waitFor() == 0
    } catch (e: Throwable) {
        false
    }
}
