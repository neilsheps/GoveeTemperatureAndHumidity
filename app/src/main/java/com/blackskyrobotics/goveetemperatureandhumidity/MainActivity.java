package com.blackskyrobotics.goveetemperatureandhumidity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

	/*

	This short demo app is to show how to decrypt the temperature and humidity values inserted into
	advertising packets by the Govee Thermo-Hygrometer Lite, although it may work for other devices too

	https://www.govee.com/productDetail?sku=H5074&asin=B07R586J37

	The demo app scans for advertising packets coming from these devices, and pulls out the temperature
	and relative humidity.  These are two byte values stored LSB first at bytes 34 and 36 respectively

	Take that int value, divide by 100 and you get celcius and RH% respectively

	I figured all this out by packet sniffing on an android device.  I also learned that the Govee device
	sends approximately 2 advertisements per second.  One doesn't seem to change, and is some kind of
	Apple specific advertisement, the other contains the data we need

	This app has substantial limitations.  It sniffs for any Govee device in range and tries to decrpyt
	the associated data it finds.  If more than one Govee device is in range, you'll see results jumping
	around.  If a Govee device with a different data format shows up, that will get messed up too.
	To do this properly would need some way to link the ble address of the device you want to listen
	to and store it in shared preferences or something.  I didn't want to spend time on that

	Also, the Govee devices store data and the iOS and Android apps have some way to retrieve the
	historic data over a few seconds.  I did not look into this; if you are listening for advertisements
	you will get the data, if you don't listen, you will miss it

	 */


	private static final String LOGTAG = "MainActivity";
	public static final String UPDATE_SCREEN= "Update screen";

	public static final String BLE_DEVICE_NAME = "bleDeviceName";
	public static final String BLE_DEVICE_ADDRESS = "bleDeviceAddress";
	public static final String BLE_RSSI = "bleRssi";
	public static final String BLE_SCANNED_BYTES = "scannedBytes";

	public static final int TEMPERATURE_LSB_BYTE = 34;                                              // the first byte (LSB) in the advertisement
	public static final int TEMPERATURE_BYTES = 2;
	public static final boolean LSB_FIRST = true;
	public static final double TEMPERATURE_DIVISOR = 100d;

	public static final int RELATIVE_HUMIDITY_LSB_BYTE = 36;                                              // the first byte (LSB) in the advertisement
	public static final int RELATIVE_HUMIDITY_BYTES = 2;
	public static final double RELATIVE_HUMIDITY_DIVISOR = 100d;

	private TextView temperatureTextView, relativeHumidityTextView, rssiTextView, deviceNameTextView;
	private ImageView scanImageView;
	private BleHandler bleHandler;



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		deviceNameTextView = (TextView)findViewById(R.id.deviceTextView);
		temperatureTextView = (TextView)findViewById(R.id.temperatureTextView);
		relativeHumidityTextView = (TextView)findViewById(R.id.relativeHumidityTextView);
		rssiTextView = (TextView)findViewById(R.id.rssiTextView);

		bleHandler = new BleHandler(this);
		registerReceiver(updateScreenReceiver, new IntentFilter(MainActivity.UPDATE_SCREEN));
	}

	protected void onDestroy() {
		super.onDestroy();
		bleHandler.close();
		unregisterReceiver(updateScreenReceiver);

	}

	public static long getLongFromByteArray(byte[] bytes, int position, int numbytes, boolean lsbFirst) {
		long result = 0;
		for (int i = 0; i < numbytes; i++) {
			result *= 256;
			if (lsbFirst) {
				result += ((long)(bytes[position+numbytes-i-1] & 0xFF));
			} else {
				result += ((long)(bytes[position+i] & 0xFF));
			}
		}
		//Log.i(LOGTAG, "Result = "+result+", in HEX = "+Integer.toHexString((int)result));

		if (result > Math.pow(256,numbytes)/2) {                                                    // fix for two's complement negative #s
			result -=  Math.pow(256,numbytes);
			//Log.i(LOGTAG, "Fixed to "+result);
		}

		return result;
	}


	public BroadcastReceiver updateScreenReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context ct, Intent intent) {

			StringBuffer sb = new StringBuffer(200);
			if (intent.getStringExtra(BLE_DEVICE_NAME) == null) {
				sb.append("NULL");
			} else {
				sb.append(intent.getStringExtra(BLE_DEVICE_NAME));
			}

			sb.append('\n');
			sb.append(intent.getStringExtra(BLE_DEVICE_ADDRESS));
			deviceNameTextView.setText(sb.toString());

			DecimalFormat df = new DecimalFormat("##0.0");

			rssiTextView.setText("rssi = "+intent.getIntExtra(MainActivity.BLE_RSSI,-1000));

			byte[] scannedBytes = intent.getByteArrayExtra(MainActivity.BLE_SCANNED_BYTES);

			double celcius = ((double)getLongFromByteArray(scannedBytes,TEMPERATURE_LSB_BYTE, TEMPERATURE_BYTES, LSB_FIRST)) / TEMPERATURE_DIVISOR;
			temperatureTextView.setText(df.format(celcius) + (char) 0x00B0 + "C");

			double relativeHumidity = ((double)getLongFromByteArray(scannedBytes,RELATIVE_HUMIDITY_LSB_BYTE, RELATIVE_HUMIDITY_BYTES, LSB_FIRST)) / RELATIVE_HUMIDITY_DIVISOR;
			relativeHumidityTextView.setText(df.format(relativeHumidity)+"%");

		}
	};








}
