package alexmaslakov.me.wifi_old_sport

import android.app.AlertDialog
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.*
import android.widget.*

abstract class WiFiAccessPointListBaseActivity : ListActivity() {
    protected var adapter: NetworkManagerAdapter? = null
    private lateinit var dbHelper: DbHelper

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = DbHelper(this)
    }

    public override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.wifi_access_points_manager, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_remove_all -> {
                //todo
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    fun refresh() {
        adapter!!.refresh()
    }

    abstract fun confirmClearAll()

    protected abstract inner class NetworkManagerAdapter : BaseAdapter() {
        protected var prefs: SharedPreferencesEx? = null
        protected var wifiManager: WifiManager? = null
        private var layoutInflater: LayoutInflater? = null
        protected var networkList: ArrayList<NetworkAvailability>? = null

        init {
            val context = this@WiFiAccessPointListBaseActivity.applicationContext
            prefs = SharedPreferencesEx(context)
            wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            refresh()
        }

        abstract fun refresh()

        override fun getCount(): Int {
            return networkList!!.size
        }

        override fun getItem(position: Int): Any {
            return networkList!![position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val layout: LinearLayout = if (convertView == null) {
                layoutInflater!!.inflate(R.layout.wifi_access_points_manager_item, null) as LinearLayout
            } else {
                (convertView as LinearLayout?)!!
            }

            val SSIDinfo = getItem(position) as NetworkAvailability
            val SSIDtext = layout.findViewById<View>(R.id.SSIDname) as TextView
            SSIDtext.text = SSIDinfo.name
            val signalStrengthImage = layout.findViewById<View>(R.id.signalStrength) as ImageView
            val colour = if (SSIDinfo.accessPointSafety === WifiAccessPointTrustLevel.UNTRUSTED) {
                "pink"
            } else {
                "teal"
            }

            val resourceName = if (SSIDinfo.signalStrength == -1) {
                "ic_wifi_unavailable_"
            } else {
                "ic_wifi_signal_" + SSIDinfo.signalStrength + "_"
            } + colour

            val resourceId = resources.getIdentifier(resourceName, "drawable", packageName)
            signalStrengthImage.setImageResource(resourceId)
            return layout
        }
    }

    protected inner class NetworkAvailability(name: String, rssi: Int, accessPointSafety: WifiAccessPointTrustLevel) {
        var name: String? = null

        var signalStrength: Int = 0
            set(rssi) = if (rssi < -999) {
                field = -1
            } else {
                field = WifiManager.calculateSignalLevel(rssi, 5)
            }

        var accessPointSafety: WifiAccessPointTrustLevel? = null

        val isAvailable: Boolean
            get() = this.signalStrength >= 0

        init {
            this.name = name
            this.signalStrength = rssi
            this.accessPointSafety = accessPointSafety
        }
    }
}

class WiFiAccessPointListActivity : WiFiAccessPointListBaseActivity() {
    var ssid: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ssid = getIntent().getStringExtra("SSID")
        title = ssid
        adapter = MACManagerAdapter()
        listAdapter = adapter
    }

    override fun onListItemClick(listView: ListView, view: View, position: Int, id: Long) {
        val listItem = listView.getItemAtPosition(position) as NetworkAvailability
        val mac = listItem.name
        val builder = AlertDialog.Builder(this)

        /*
        builder.setMessage(String.format(resources.getString(R.string.dialog_removetrustedmac), mac))
        builder.setPositiveButton(R.string.dialog_remove, DialogInterface.OnClickListener { dialog, id ->
            // Actually remove the BSSID from the 'trusted' list
            val prefs = SharedPreferncesEx(this@MACManagerActivity)
            if (listItem.getAccessPointSafety() === ScanResultsChecker.AccessPointSafety.TRUSTED) {
                prefs.removeAllowedBSSID(ssid, mac)
            } else {
                prefs.removeBlockedBSSID(ssid, mac)
            }

            this@MACManagerActivity.refresh()
        })
        builder.setNegativeButton(R.string.dialog_clearhotspots_no, DialogInterface.OnClickListener { dialog, id ->
            // User canceled
        })

        builder.show()
        */
    }

    override fun confirmClearAll() {
        // Ask for confirmation first
        val builder = AlertDialog.Builder(this)
        builder.setMessage(String.format(resources.getString(R.string.dialog_clearhotspotsformac), ssid))
        builder.setPositiveButton(R.string.dialog_clearhotspots_yes, DialogInterface.OnClickListener { _, id ->
            //   clear the list
            //todo remove all
            this@MACManagerActivity.refresh()
        })

        builder.show()
    }

    private inner class MACManagerAdapter : NetworkManagerAdapter() {
        override fun refresh() {
            networkList = ArrayList()
            val scanResults = wifiManager!!.scanResults

            //todo retrieve from db
            val trustedMacList = null // prefs.getAllowedBSSIDs(ssid)

            // Add currently available access points that we trust to the list
            for (scanResult in scanResults) {
                if (trustedMacList.contains(scanResult.BSSID)) {
                    networkList.add(NetworkAvailability(scanResult.BSSID, scanResult.level, WiFiAccessPointScanReceiver.AccessPointSafety.TRUSTED))
                    trustedMacList.remove(scanResult.BSSID)
                }
            }

            //todo
            val blockedMacList = null // prefs.getBlockedBSSIDs(ssid)
            // Add currently available access points that we block to the list
            for (scanResult in scanResults) {
                if (blockedMacList.contains(scanResult.BSSID)) {
                    networkList.add(NetworkAvailability(scanResult.BSSID, scanResult.level, WiFiAccessPointScanReceiver.AccessPointSafety.UNTRUSTED))
                    blockedMacList.remove(scanResult.BSSID)
                }
            }

            // Add all other (non-available) saved SSIDs to the list
            trustedMacList.forEach { x -> networkList.add(NetworkAvailability(x, -99999, WiFiAccessPointScanReceiver.AccessPointSafety.TRUSTED)) }
            blockedMacList.forEach { x -> networkList.add(NetworkAvailability(x, -99999, WiFiAccessPointScanReceiver.AccessPointSafety.UNTRUSTED)) }
            notifyDataSetChanged()
        }
    }
}