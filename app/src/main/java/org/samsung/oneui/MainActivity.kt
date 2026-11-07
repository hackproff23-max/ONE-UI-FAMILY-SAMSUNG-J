package org.samsung.oneui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private enum class Source { DRAWER, HOME, DOCK }

    private lateinit var prefs: Prefs
    private lateinit var root: LauncherRoot
    private lateinit var home: View
    private lateinit var dock: LinearLayout
    private lateinit var homeHint: TextView
    private lateinit var homeAdapter: AppAdapter
    private lateinit var drawer: View
    private lateinit var drawerList: RecyclerView
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var homeCtl: GridController
    private lateinit var drawerCtl: GridController

    private var allApps: List<AppInfo> = emptyList()
    private var byKey: Map<String, AppInfo> = emptyMap()
    private val worker = Executors.newSingleThreadExecutor()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setupWindow()
        setContentView(R.layout.activity_main)
        bindViews()
        setupClock()
        setupLists()
        setupDrawer()
        setupInsets()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        loadApps(force = false)
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // botao Home: volta para a tela inicial
        if (intent?.action == Intent.ACTION_MAIN) root.closeDrawer()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // launcher nunca fecha com Voltar; so fecha a gaveta
        if (root.isDrawerOpen) root.closeDrawer()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MASK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (ShapeMask.save(this, uri)) {
            toast("Forma importada")
            reapplyIcons()
        } else {
            toast("Não foi possível ler a imagem")
        }
    }

    // ---------- configuracao ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        dock = findViewById(R.id.dock)
        homeHint = findViewById(R.id.homeHint)
        drawer = findViewById(R.id.drawer)
        drawerList = findViewById(R.id.drawerList)
    }

    private fun setupClock() {
        val clock = findViewById<TextClock>(R.id.clock)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        val date = findViewById<TextClock>(R.id.date)
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        date.format12Hour = pattern
        date.format24Hour = pattern
    }

    private fun setupLists() {
        homeAdapter = AppAdapter({ launch(it) }, { app, v -> showAppMenu(v, app, Source.HOME) })
        val homeGrid = findViewById<RecyclerView>(R.id.homeGrid)
        homeGrid.adapter = homeAdapter
        homeGrid.overScrollMode = View.OVER_SCROLL_NEVER

        drawerAdapter = AppAdapter({ launch(it) }, { app, v -> showAppMenu(v, app, Source.DRAWER) })
        drawerList.adapter = drawerAdapter
        drawerList.setHasFixedSize(true)

        val span = (resources.displayMetrics.widthPixels / dp(84)).coerceIn(4, 8)
        homeCtl = GridController(homeGrid, homeAdapter, 4, true)
        drawerCtl = GridController(drawerList, drawerAdapter, span, false)
        applyGrids()
    }

    private fun applyGrids() {
        homeCtl.apply(prefs.grid("home"), prefs.iconSize())
        drawerCtl.apply(prefs.grid("drawer"), prefs.iconSize())
    }

    /** Quantos apps cabem na tela inicial: colunas x linhas quando as linhas foram escolhidas. */
    private fun homeMax(): Int {
        val g = prefs.grid("home")
        if (g.rows > 0 && g.style != GridStyle.LIST) {
            return maxOf(1, (if (g.cols > 0) g.cols else 4) * g.rows)
        }
        return Prefs.HOME_MAX
    }

    private fun setupDrawer() {
        root.drawerView = drawer
        root.homeView = home
        root.drawerScroller = drawerList
        root.onClosed = { drawerList.scrollToPosition(0) }
        findViewById<View>(R.id.handle).setOnClickListener { root.openDrawer() }
        findViewById<View>(R.id.menuBtn).setOnClickListener { showSettings() }
        home.setOnLongClickListener {
            showSettings()
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun readInsets(insets: WindowInsets): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            intArrayOf(bars.top, bars.bottom, maxOf(bars.bottom, ime.bottom))
        } else {
            intArrayOf(insets.systemWindowInsetTop, insets.systemWindowInsetBottom, insets.systemWindowInsetBottom)
        }
    }

    private fun setupInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val i = readInsets(insets)
            home.setPadding(0, i[0], 0, i[1])
            drawer.setPadding(0, i[0], 0, i[2])
            insets
        }
        root.requestApplyInsets()
    }

    // ---------- dados ----------

    private fun loadApps(force: Boolean) {
        val cache: Map<String, AppInfo> = if (force) emptyMap() else byKey
        worker.execute {
            val themer = IconThemer(this, prefs.iconPack(), prefs.iconShape(), prefs.fitIcons(), prefs.iconSize(), prefs.maskBlur())
            val apps = AppRepository.load(this, cache, themer)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!force && apps.map { it.key } == allApps.map { it.key }) return@runOnUiThread
                allApps = apps
                byKey = apps.associateBy { it.key }
                pruneMissing()
                drawerAdapter.submit(allApps)
                refreshHome()
            }
        }
    }

    /** Tira dos fixados os apps que foram desinstalados. */
    private fun pruneMissing() {
        if (byKey.isEmpty()) return
        val h = prefs.home()
        val d = prefs.dock()
        val h2 = h.filter { it in byKey }
        val d2 = d.filter { it in byKey }
        if (h2.size != h.size) prefs.setHome(h2)
        if (d2.size != d.size) prefs.setDock(d2)
    }

    private fun refreshHome() {
        val homeApps = prefs.home().mapNotNull { byKey[it] }
        homeAdapter.submit(homeApps)
        homeHint.visibility = if (homeApps.isEmpty()) View.VISIBLE else View.GONE
        refreshDock()
    }

    private fun refreshDock() {
        dock.removeAllViews()
        val apps = prefs.dock().mapNotNull { byKey[it] }
        dock.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        for (app in apps) {
            val v = layoutInflater.inflate(R.layout.item_app, dock, false)
            val iconView = v.findViewById<ImageView>(R.id.icon)
            val ip = iconView.layoutParams
            ip.width = dp(prefs.iconSize())
            ip.height = dp(prefs.iconSize())
            iconView.layoutParams = ip
            iconView.setImageDrawable(app.newIcon())
            v.findViewById<TextView>(R.id.label).visibility = View.GONE
            v.setOnClickListener { launch(app) }
            v.setOnLongClickListener {
                showAppMenu(it, app, Source.DOCK)
                true
            }
            v.layoutParams = LinearLayout.LayoutParams(dp(maxOf(68, prefs.iconSize() + 12)), LinearLayout.LayoutParams.WRAP_CONTENT)
            dock.addView(v)
        }
    }

    // ---------- acoes ----------

    private fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
            root.closeDrawer(animate = false)
        } catch (e: Exception) {
            toast("Não foi possível abrir o app")
        }
    }

    private fun showAppMenu(anchor: View, app: AppInfo, source: Source) {
        val popup = PopupMenu(this, anchor)
        val m = popup.menu
        when (source) {
            Source.DRAWER -> {
                if (app.key !in prefs.home()) m.add(0, ID_PIN_HOME, 0, "Fixar na tela inicial")
                if (app.key !in prefs.dock()) m.add(0, ID_PIN_DOCK, 0, "Fixar no dock")
            }
            Source.HOME -> m.add(0, ID_UNPIN_HOME, 0, "Remover da tela inicial")
            Source.DOCK -> m.add(0, ID_UNPIN_DOCK, 0, "Remover do dock")
        }
        m.add(0, ID_INFO, 0, "Informações do app")
        m.add(0, ID_UNINSTALL, 0, "Desinstalar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_PIN_HOME -> pin(app, home = true)
                ID_PIN_DOCK -> pin(app, home = false)
                ID_UNPIN_HOME -> {
                    prefs.setHome(prefs.home().apply { remove(app.key) })
                    refreshHome()
                }
                ID_UNPIN_DOCK -> {
                    prefs.setDock(prefs.dock().apply { remove(app.key) })
                    refreshHome()
                }
                ID_INFO -> startSafely(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                )
                ID_UNINSTALL -> startSafely(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                )
            }
            true
        }
        popup.show()
    }

    // ---------- opcoes (tres pontinhos na gaveta ou segurar na tela inicial) ----------

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("Opções")
            .setItems(arrayOf("Ícones", "Grade")) { _, which ->
                if (which == 0) showIconSettings() else showGridSettings()
            }
            .show()
    }

    // ----- Ícones -----

    private fun showIconSettings() {
        val hasMask = ShapeMask.exists(this)
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        labels.add("Pacote de ícones: ${currentPackLabel()}")
        actions.add { chooseIconPack() }

        labels.add("Tamanho dos ícones: ${prefs.iconSize()} dp")
        actions.add { chooseIconSize() }

        labels.add("Ajustar ícones (sem fundo branco): ${if (prefs.fitIcons()) "ligado" else "desligado"}")
        actions.add {
            prefs.setFitIcons(!prefs.fitIcons())
            reapplyIcons()
        }

        labels.add(if (hasMask) "Trocar forma importada (PNG)" else "Importar forma (PNG)")
        actions.add { pickMask() }

        if (hasMask) {
            labels.add("Remover forma importada")
            actions.add {
                ShapeMask.delete(this)
                reapplyIcons()
            }
        }

        labels.add("Borrar bordas da forma: ${prefs.maskBlur()}")
        actions.add { chooseMaskBlur() }

        labels.add("Formato dos ícones sem tema: ${prefs.iconShape().label}")
        actions.add { chooseShape() }

        AlertDialog.Builder(this)
            .setTitle("Ícones")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Voltar") { _, _ -> showSettings() }
            .show()
    }

    private fun chooseIconSize() {
        val cur = prefs.iconSize()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
        }
        val value = TextView(this).apply {
            text = "$cur dp"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val bar = SeekBar(this).apply {
            max = Prefs.ICON_MAX - Prefs.ICON_MIN
            progress = cur - Prefs.ICON_MIN
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = "${p + Prefs.ICON_MIN} dp"
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(value)
        box.addView(bar)
        AlertDialog.Builder(this)
            .setTitle("Tamanho dos ícones")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setIconSize(bar.progress + Prefs.ICON_MIN)
                reapplyIcons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseMaskBlur() {
        val cur = prefs.maskBlur()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
        }
        val value = TextView(this).apply {
            text = "$cur"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val bar = SeekBar(this).apply {
            max = 100
            progress = cur
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = "$p"
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(value)
        box.addView(bar)
        AlertDialog.Builder(this)
            .setTitle("Borrar bordas da forma (0 a 100)")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setMaskBlur(bar.progress)
                reapplyIcons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----- Grade -----

    private fun showGridSettings() {
        AlertDialog.Builder(this)
            .setTitle("Grade")
            .setItems(arrayOf("Gaveta de apps", "Tela inicial")) { _, which ->
                if (which == 0) showGridTarget("drawer", "Gaveta de apps")
                else showGridTarget("home", "Tela inicial")
            }
            .setNegativeButton("Voltar") { _, _ -> showSettings() }
            .show()
    }

    private fun numLabel(n: Int): String = if (n == 0) "automático" else n.toString()

    private fun showGridTarget(target: String, title: String) {
        val g = prefs.grid(target)
        val labels = arrayOf(
            "Estilo: ${g.style.label}",
            "Colunas: ${numLabel(g.cols)}",
            "Linhas: ${numLabel(g.rows)}"
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> chooseGridStyle(target, title)
                    1 -> chooseGridNumber(target, title, "Colunas", g.cols) { prefs.setGridCols(target, it) }
                    else -> chooseGridNumber(target, title, "Linhas", g.rows) { prefs.setGridRows(target, it) }
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showGridSettings() }
            .show()
    }

    private fun chooseGridStyle(target: String, title: String) {
        val styles = GridStyle.values()
        val names = styles.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Estilo")
            .setSingleChoiceItems(names, prefs.grid(target).style.ordinal) { dialog, which ->
                prefs.setGridStyle(target, styles[which])
                dialog.dismiss()
                applyGrids()
                showGridTarget(target, title)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseGridNumber(target: String, title: String, what: String, current: Int, save: (Int) -> Unit) {
        val names = Array(16) { if (it == 0) "Automático" else it.toString() }
        AlertDialog.Builder(this)
            .setTitle(what)
            .setSingleChoiceItems(names, current.coerceIn(0, 15)) { dialog, which ->
                save(which)
                dialog.dismiss()
                applyGrids()
                showGridTarget(target, title)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickMask() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_MASK)
        } catch (e: Exception) {
            toast("Nenhum seletor de arquivos disponível")
        }
    }

    private fun currentPackLabel(): String {
        val current = prefs.iconPack()
        if (current.isEmpty()) return "Padrão do sistema"
        return IconPackLoader.list(this).firstOrNull { it.packageName == current }?.label ?: "Padrão do sistema"
    }

    private fun chooseIconPack() {
        val packs = IconPackLoader.list(this)
        if (packs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Pacote de ícones")
                .setMessage("Nenhum pacote de ícones instalado. Instale um pacote compatível (formato ADW, Nova ou Apex) e volte aqui.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val names = (listOf("Padrão do sistema") + packs.map { it.label }).toTypedArray()
        val current = prefs.iconPack()
        val selected = packs.indexOfFirst { it.packageName == current } + 1 // -1 vira 0 (sistema)
        AlertDialog.Builder(this)
            .setTitle("Pacote de ícones")
            .setSingleChoiceItems(names, selected) { dialog, which ->
                prefs.setIconPack(if (which == 0) "" else packs[which - 1].packageName)
                dialog.dismiss()
                reapplyIcons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseShape() {
        val shapes = IconShape.values()
        val names = shapes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Formato dos ícones sem tema")
            .setSingleChoiceItems(names, prefs.iconShape().ordinal) { dialog, which ->
                prefs.setIconShape(shapes[which])
                dialog.dismiss()
                reapplyIcons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reapplyIcons() {
        toast("Aplicando ícones…")
        applyGrids()
        loadApps(force = true)
    }

    private fun pin(app: AppInfo, home: Boolean) {
        val list = if (home) prefs.home() else prefs.dock()
        val max = if (home) homeMax() else Prefs.DOCK_MAX
        if (list.size >= max) {
            toast(if (home) "Tela inicial cheia" else "Dock cheio")
            return
        }
        list.add(app.key)
        if (home) prefs.setHome(list) else prefs.setDock(list)
        refreshHome()
        toast("Fixado")
        root.closeDrawer()
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Ação indisponível")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val ID_PIN_HOME = 1
        const val ID_PIN_DOCK = 2
        const val ID_UNPIN_HOME = 3
        const val ID_UNPIN_DOCK = 4
        const val ID_INFO = 5
        const val ID_UNINSTALL = 6
        const val REQ_MASK = 101
    }
}
