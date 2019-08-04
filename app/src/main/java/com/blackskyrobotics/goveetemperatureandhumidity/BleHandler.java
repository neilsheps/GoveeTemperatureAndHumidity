package com.blackskyrobotics.goveetemperatureandhumidity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleHandler {

	private static final String LOGTAG = "bleHandler";
	private static final String GOVEE_PREFIX = "Govee_";

	public static final String SEND_DATA_BY_BLE = "sendDataByBle";

	public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
	public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
	public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
	public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
	public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
	public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");


	private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
	private final static UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
	private final static UUID SERVICE_CHANGED_CHARACTERISTIC = UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb");


	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeScanner mBluetoothLeScanner;

	private ScanSettings settings;
	private List<ScanFilter> filters;
	private int bleAdapterState = STATE_DISCONNECTED;

	private static final int STATE_NOT_AVAILABLE = -1;
	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_SCANNING = 1;
	private static final int STATE_NOT_SCANNING = 2;
	private static final int STATE_CONNECTING = 3;
	private static final int STATE_CONNECTED = 4;

	private static final long SCAN_PERIOD = 60000;                                                  // one minute
	private final static char[] HEX = "0123456789ABCDEF".toCharArray();
	private static final long MAX_WAIT_BETWEEN_CONNECTION_ATTEMPTS_MILLIS = 5000;

	private Context context;
	private Handler mHandler;

	public BleHandler(Context ct) {
		context = ct;
		Log.i(LOGTAG, "bleHandler initialize method called");
		mHandler = new Handler();
		bleAdapterState = STATE_NOT_AVAILABLE;

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(context, "ERROR - bluetooth not supported", Toast.LENGTH_SHORT).show();
			Log.e(LOGTAG, "ERROR - BLE not supported");
			return;
		}

		mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();

		if (mBluetoothAdapter == null) {
			Toast.makeText(context, "ERROR - no bluetooth adapter", Toast.LENGTH_SHORT).show();
			Log.e(LOGTAG, "ERROR - no bluetooth adapter");
			return;
		}

		if (Build.VERSION.SDK_INT >= 21) {
			mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
			settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
			filters = new ArrayList<>();
		}

		bleAdapterState = STATE_NOT_SCANNING;
		Log.i(LOGTAG, "bleHandler initialize called, now scanning for devices");
		scanForDevices(true);
	}

	public void close() {
		scanForDevices(false);
		Log.i(LOGTAG, "bleHandler now closed");

	}


	public void scanForDevices(boolean b) {
		if (b) {
			Log.i(LOGTAG, "Now scanning for BLE devices");
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					try {
						mBluetoothLeScanner.stopScan(mScanCallback);
						Log.i(LOGTAG, "Stopped scanning for BLE devices after " + SCAN_PERIOD / 1000 + " seconds");
						bleAdapterState = STATE_NOT_SCANNING;
					} catch (Exception e) {
						e.printStackTrace();
					}
					restartScan();
				}
			}, SCAN_PERIOD);

			mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
			bleAdapterState = STATE_SCANNING;
		} else {
			mBluetoothLeScanner.stopScan(mScanCallback);
			Log.i(LOGTAG, "Stopping scanning for BLE devices, based on scanForDevices(false)");
			bleAdapterState = STATE_NOT_SCANNING;
		}
	}

	public void restartScan() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.i(LOGTAG,"Restarting scan");
				scanForDevices(true);
			}
		},50);
	}

	private ScanCallback mScanCallback = new ScanCallback() {

		@Override
		public void onScanResult(int callbackType, ScanResult result) {

			// Very basic filtering.  Device must start with "Govee_"
			// and we assume that it is a Govee temperature sensor
			// and that there's only one in range.  I did not want to build a listview
			// etc. to filter these things properly


			if (result.getDevice().getName() == null || !result.getDevice().getName().startsWith(GOVEE_PREFIX)) {
				return;
			}

			Log.i(LOGTAG,
					"OnLeScan:  Device.name() = " + result.getDevice().getName()
							+ ", device.getAddress() = " + result.getDevice().getAddress()
							+ ", rssi = " + result.getRssi()
							+ ", byte string = " + getByteString(result.getScanRecord().getBytes()));

			// this is here because the Govee device sends two different advertisements per second
			// One contains the data we want, the other appears to be some kind of iOS advertisement
			// that doesn't contain any moving data.  We discard that one
			// In the correct advertisement, bytes 40-62 are all zero, we just pick one to filter on
			if (result.getScanRecord().getBytes()[44] != 0) {
				Log.i(LOGTAG, "Scan received is not the right format, discarding");
				return;
			}

			Intent intent = new Intent();
			intent.setAction(MainActivity.UPDATE_SCREEN);
			intent.putExtra(MainActivity.BLE_DEVICE_NAME, result.getDevice().getName());
			intent.putExtra(MainActivity.BLE_DEVICE_ADDRESS, result.getDevice().getAddress());
			intent.putExtra(MainActivity.BLE_RSSI, result.getRssi());
			intent.putExtra(MainActivity.BLE_SCANNED_BYTES, result.getScanRecord().getBytes());

			context.sendBroadcast(intent);
		}


		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			for (ScanResult sr : results) {
				processScanCallback(sr.getDevice(), sr.getRssi());
			}
		}

		@Override
		public void onScanFailed(int errorCode) {
			Log.e("Scan Failed", "Error Code: " + errorCode);
		}
	};

	private void processScanCallback(BluetoothDevice device, final int rssi) {
		String bleDeviceName = device.getName();
		Log.i(LOGTAG, "BLE Device " + bleDeviceName + " discovered and recognized, trying to connect next (have not attempted in 5000 millis)");
	}

	private String getByteString(byte[] b) {

		StringBuffer sb = new StringBuffer(300);
		for (int i = 0; i < b.length; i++) {
			int bb = b[i] & 0xFF;
			sb.append(HEX[bb >> 4]);
			sb.append(HEX[bb & 0x0F]);
			sb.append(" ");
		}
		return sb.toString();
	}


}