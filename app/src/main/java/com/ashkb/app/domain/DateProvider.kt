package com.ashkb.app.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.0.86（批次 11）：**全应用唯一的「今天」来源**。
 *
 * 为什么必须收成一个进程级单例：此前有 7 个 ViewModel 各自抄了一份一模一样的跨零点 ticker
 * （构造 → `while(true){ delay(到次日零点 + 1s); 日期 = LocalDate.now() }`）。重复代码只是表症，
 * 真正的问题是**那份实现根本不可靠**：
 *
 * 协程 `delay` 在 Android 主线程调度器上落到 `Handler.postDelayed`，而 `postDelayed` 按
 * **uptimeMillis** 计时——**深睡不计时**。夜里手机睡着时零点那一刻不会触发，ticker 要等设备
 * 醒来才补跑；于是早上打开应用时，各页面里的「今天 / 昨天」还停在**前一天**
 * （日期标题、昨日待补剂量卡、复核到期计数、紧急卡「当前用药」的在用判断都读这个值）。
 *
 * 修法（本批次选定方案）：**不再用定时器推算零点，改为监听系统的日期变更广播**——
 *   · [Intent.ACTION_DATE_CHANGED]：系统跨日时发出（日期真的变了才发）；
 *   · [Intent.ACTION_TIME_CHANGED] / [Intent.ACTION_TIMEZONE_CHANGED]：手动改时间 / 改时区
 *     （可能把日期改掉，同样要重算）。
 *
 * 为什么选广播而不是「ticker + 唤醒时校正」：ticker 哪怕加了校正，也仍然要**先发生"唤醒"**
 * 才可能发现自己过期；日期变更广播则是**系统在跨日那一刻主动送达**的——接收方是动态注册的
 * 接收器（不受隐式广播限制），设备深睡时系统照发，进程醒来即可收到。二者是「事后补偿」与
 * 「当场送达」的区别。残余风险是极个别 ROM 不发这条广播，故保留 [refreshIfStale]
 * （由现有 ON_RESUME 调用点触发）作为第二道防线，而不是把它当主路径。
 *
 * 线程语义：接收器与 [onDateKnown] 都在主线程执行；[today] 是 `MutableStateFlow`（线程安全），
 * 监听器集合用 `CopyOnWriteArraySet`——回调期间增删监听不会并发改动集合。
 *
 * 语义承诺：**只在「今天」真的变了时才通知**（同值重复广播是空操作）。各 ViewModel 因此可以
 * 放心地把「跨日后要重算的东西」挂在 [addOnDateChangedListener] 上，不必自己再判一次。
 *
 * [scope] 是**应用级**协程作用域（`AshkbApplication.appScope`），显式声明为依赖是为了让
 * 「日期变化之后要起协程重算」这类将来的动作有正确的生命周期落点；当前的重算动作都由各
 * ViewModel 用自己的 `viewModelScope` 触发（它们的生命周期比应用短，用应用级 scope 反而会在
 * VM 销毁后继续跑）。
 */
class DateProvider(
    private val scope: CoroutineScope,
    /** 时钟可注入：单测里换成假时钟，日期推进就不必真的等到明天。 */
    private val clock: () -> LocalDate = { LocalDate.now() },
) {

    init {
        // 构造期显式引用一次，保证「应用级 scope」这一契约不会被静默丢掉（detekt 亦不会误报未用参数）
        scope.coroutineContext
    }

    private val _today = MutableStateFlow(clock())

    /** 「今天」。构造时取一次系统时钟，此后只由 [onDateKnown]（系统广播 / 前台校正）推进。 */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val listeners = CopyOnWriteArraySet<(LocalDate) -> Unit>()

    /**
     * 与系统时钟对一次。**返回值没变就是空操作**——不重复通知监听器，也不触发多余的重查库。
     *
     * 调用点：① 系统日期变更广播；② 各页现有的 `ON_RESUME`（深睡 / ROM 不发广播时的兜底）。
     */
    fun refreshIfStale() {
        onDateKnown(clock())
    }

    /** 登记「日期变化」回调，返回反注册句柄（ViewModel 在 `onCleared` 里调用）。 */
    fun addOnDateChangedListener(listener: (LocalDate) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * 接收一个已知的日期值并推进状态。**只在新值 ≠ 当前值时才通知监听器**——
     * 这是「跨日重算」的唯一触发口径，刻意不让调用方再各自判一次。
     */
    fun onDateKnown(date: LocalDate) {
        if (_today.value == date) return
        _today.value = date
        listeners.forEach { it(date) }
    }

    /**
     * 注册系统日期 / 时间变更广播，注册后立刻与系统时钟对一次
     * （覆盖「注册之前就已经跨过日」的那段窗口）。进程结束时必须调用 [stop]。
     */
    fun start(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= API_REGISTER_RECEIVER_FLAGS) {
            // API 33+：动态注册须显式声明是否对外导出。这三条都是系统广播，
            // RECEIVER_NOT_EXPORTED 不拦系统投递，但挡住其它应用伪造同 action 的 Intent——
            // 伪造成功最多让日期多刷一次（值相同即空操作），仍然不该开放。
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        refreshIfStale()
    }

    /** 反注册（进程结束 / 测试收尾）。重复调用安全。 */
    fun stop(context: Context) {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 三条 action 共用同一处理：只当"跟系统时钟对一次"的触发信号，判定交给 refreshIfStale
            runCatching { refreshIfStale() }
                .onFailure { Log.w(TAG, "refresh date failed", it) }
        }
    }

    private companion object {
        const val TAG = "ASHKB"

        /** API 33 起 `registerReceiver` 必须显式声明导出标志（低于此版本传入会直接抛异常）。 */
        const val API_REGISTER_RECEIVER_FLAGS = 33
    }
}
