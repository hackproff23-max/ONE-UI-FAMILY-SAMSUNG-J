package org.samsung.oneui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
    private lateinit var search: EditText
    private lateinit var drawerList: RecyclerView
    private lateinit var drawerAdapter: AppAdapter

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
        search = findViewById(R.id.search)
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
        homeGrid.layoutManager = object : GridLayoutManager(this@MainActivity, 4) {
            override fun canScrollVertically(): Boolean = false
        }
        homeGrid.adapter = homeAdapter
        homeGrid.overScrollMode = View.OVER_SCROLL_NEVER

        drawerAdapter = AppAdapter({ launch(it) }, { app, v -> showAppMenu(v, app, Source.DRAWER) })
        val span = (resources.displayMetrics.widthPixels / dp(84)).coerceIn(4, 8)
        drawerList.layoutManager = GridLayoutManager(this, span)
        drawerList.adapter = drawerAdapter
        drawerList.setHasFixedSize(true)
    }

    private fun setupDrawer() {
        root.drawerView = drawer
        root.homeView = home
        root.drawerScroller = drawerList
        root.onStateChanged = { open -> if (!open) hideKeyboard() }
        root.onClosed = {
            search.setText("")
            drawerList.scrollToPosition(0)
        }
        findViewById<View>(R.id.handle).setOnClickListener { root.openDrawer() }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDrawer()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        search.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                drawerAdapter.first()?.let { launch(it) }
                true
            } else {
                false
            }
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
            val apps = AppRepository.load(this, cache)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!force && apps.map { it.key } == allApps.map { it.key }) return@runOnUiThread
                allApps = apps
                byKey = apps.associateBy { it.key }
                pruneMissing()
                filterDrawer()
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

    private fun filterDrawer() {
        val q = search.text.toString().trim().lowercase()
        val list = if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) }
        drawerAdapter.submit(list)
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
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(app.newIcon())
            v.findViewById<TextView>(R.id.label).visibility = View.GONE
            v.setOnClickListener { launch(app) }
            v.setOnLongClickListener {
                showAppMenu(it, app, Source.DOCK)
                true
            }
            v.layoutParams = LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.WRAP_CONTENT)
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

    private fun pin(app: AppInfo, home: Boolean) {
        val list = if (home) prefs.home() else prefs.dock()
        val max = if (home) Prefs.HOME_MAX else Prefs.DOCK_MAX
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

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
        root.requestFocus()
    }

    private companion object {
        const val ID_PIN_HOME = 1
        const val ID_PIN_DOCK = 2
        const val ID_UNPIN_HOME = 3
        const val ID_UNPIN_DOCK = 4
        const val ID_INFO = 5
        const val ID_UNINSTALL = 6
    }
}
