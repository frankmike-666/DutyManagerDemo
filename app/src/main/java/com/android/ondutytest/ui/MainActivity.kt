package com.android.ondutytest.ui

import android.Manifest
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.android.ondutytest.DutyApplication
import com.android.ondutytest.R
import com.android.ondutytest.constant.Constant
import com.android.ondutytest.databinding.ActivityMainBinding
import com.android.ondutytest.presenter.MainPresenter
import com.android.ondutytest.presenter.RecorderManager
import com.android.ondutytest.ui.fragment.MainFragment
import com.android.ondutytest.ui.widget.CustomDialog
import com.android.ondutytest.util.*
import com.android.ondutytest.viewmodel.PersonInfoViewModel
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

@RuntimePermissions
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel by lazy { ViewModelProvider(this).get(PersonInfoViewModel::class.java) }
    private val recordManager by lazy { RecorderManager(this) }
    private var lightThreshold = 0
    private var blueToothList = ArrayList<String>()
    private lateinit var btAdapter: BluetoothAdapter
    private var mDisposable: Disposable? = null
    private var flag = 1

    private val searchDevices = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val address = device?.address
                    if (address != null) {
                        blueToothList.add(address)
                    }
                    if (address == Constant.BLUETOOTH_DEVICE && flag == 1) {
                        Toast.makeText(context, "???????????????", Toast.LENGTH_SHORT).show()
                        SmsUtil.sendMessage(
                            this@MainActivity,
                            DutyApplication.instance.admin!!.phoneNumber,
                            "??????????????????????????????"
                        )
                        flag = 0
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Toast.makeText(context, "????????????", Toast.LENGTH_SHORT).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Toast.makeText(context, "????????????", Toast.LENGTH_SHORT).show()
                    flag = if (!blueToothList.contains(Constant.BLUETOOTH_DEVICE)) {
                        1
                    } else 0
                }
            }
        }
    }

    private fun registerBroadcast() {
        //??????receiver??????????????????????????????
        val intent = IntentFilter()
        intent.addAction(BluetoothDevice.ACTION_FOUND)
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(searchDevices, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerBroadcast()
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!btAdapter.isDiscovering) {
            btAdapter.startDiscovery()
        }

        setSupportActionBar(binding.toolbar)
//        //??????fragment
//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
//        setupActionBarWithNavController(navController, appBarConfiguration)

        lightThreshold = SPUtil.get(this, Constant.SP_LIGHT_THRESHOLD, 10) as Int

        initView()
        LogUtil.i(DeviceUtil.getLightSensitivity().toString())
        supportFragmentManager.beginTransaction().let {
            it.replace(R.id.fm_fg, MainFragment())
            it.commit()
        }
    }

    private fun initView() {
        binding.fab.setOnClickListener {
            DeviceUtil.changeAmbientLight(true, Constant.BREATHE_LAMP_GREEN)
            takeVideoWithPermissionCheck()
        }
        //???????????????????????????
        mDisposable = Observable.interval(0, 10, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (!btAdapter.isDiscovering) {
                    blueToothList.clear()
                    btAdapter.startDiscovery()
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            //??????????????????
            R.id.action_settings_off -> {
                val calendar = Calendar.getInstance()
                val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                TimePickerDialog(this, { _, hourThis, minuteThis ->
                    ToastUtil.showShortToast(this, "????????????")
                    val calendarThis = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourThis)
                        set(Calendar.MINUTE, minuteThis)
                    }
                    val bundle = Bundle()
                    bundle.putString("number", viewModel.admin?.phoneNumber)
                    AlarmUtil.setAlarm(calendarThis, this, 1, bundle)
                }, hourOfDay, minute, true).show()
                true
            }
            //??????????????????
            R.id.action_settings_warn -> {
                val calendar = Calendar.getInstance()
                val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                TimePickerDialog(this, { _, hourThis, minuteThis ->
                    ToastUtil.showShortToast(this, "????????????")
                    val calendarThis = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourThis)
                        set(Calendar.MINUTE, minuteThis)
                    }
                    val bundle = Bundle()
                    if (viewModel.personOnDuty.isNullOrEmpty()) {
                        LogUtil.i("???????????????")
                        return@TimePickerDialog
                    }
                    val nameList = ArrayList<String>()
                    val numberList = ArrayList<String>()
                    for (person in viewModel.personOnDuty!!) {
                        numberList.add(person.phoneNumber)
                        nameList.add(person.name)
                    }
                    LogUtil.i(numberList.toString())
                    LogUtil.i(nameList.toString())
                    bundle.putStringArrayList("name", nameList)
                    bundle.putStringArrayList("number", numberList)
                    AlarmUtil.setAlarm(calendarThis, this, 0, bundle)
                }, hourOfDay, minute, true).show()
                true
            }
            R.id.action_settings_threshold -> {
                //???????????????????????????????????????
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_adjust_threshold, null)
                val dialog = CustomDialog(this, layout = view)
                dialog.setCancelable(true)

                val seekBar = view.findViewById<SeekBar>(R.id.seekbar)
                seekBar.progress = lightThreshold
                val tvValue = view.findViewById<TextView>(R.id.tv_seekbar_value)
                tvValue.text = lightThreshold.toString()
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        tvValue.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
                dialog.show()
                view.findViewById<Button>(R.id.bt_confirm).setOnClickListener {
                    dialog.dismiss()
                    lightThreshold = seekBar.progress
                    SPUtil.put(this, Constant.SP_LIGHT_THRESHOLD, lightThreshold)
                    ToastUtil.showShortToast(this, "??????????????????")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @NeedsPermission(Manifest.permission.CAMERA)
    fun takeVideo() {
        val view = LayoutInflater.from(this).inflate(R.layout.recorder_layout, null)
        val params = ViewGroup.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.video_float_width),
            resources.getDimensionPixelSize(R.dimen.video_float_height)
        )
        view.layoutParams = params
        recordManager.startCamera(
            view.findViewById(R.id.view_finder),
            view.findViewById(R.id.camera_record_time)
        )
        EasyFloat.with(this)
            .setShowPattern(ShowPattern.ALL_TIME)
            .setSidePattern(SidePattern.RESULT_SIDE)
            .setImmersionStatusBar(true)
            .setGravity(Gravity.END)
            .setLayout(view) {
                it.findViewById<ImageView>(R.id.ivClose).setOnClickListener {
                    EasyFloat.dismiss()
                    recordManager.stopRecording()
                }
            }.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposable?.dispose()
        unregisterReceiver(searchDevices)
    }
}