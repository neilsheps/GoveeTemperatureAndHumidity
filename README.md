Short Android app that reverse engineers the temperature and humidity output from a Govee Bluetooth Thermo-Hygrometer Lite.  Note that this is a simple POC and probably will not be updated further.

This short demo app is to show how to decrypt the temperature and humidity values inserted into advertising packets by the Govee Thermo-Hygrometer Lite, although it may work for other devices too

https://www.govee.com/productDetail?sku=H5074&asin=B07R586J37

It's a great product - it chirps data every second or so

The demo app scans for advertising packets coming from these devices, and pulls out the temperature and relative humidity. These are two byte values stored Least Significant Byte first at bytes 34 and 36 respectively. Take that int value, divide by 100 and you get celcius and RH% respectively

I figured all this out by packet sniffing on an android device. I also learned that the Govee device sends approximately 2 advertisements per second. One doesn't seem to change, and is some kind of Apple specific advertisement, the other contains the data we need. 

This app has substantial limitations. It does not look for UUIDs associated with temperature or humidity and then pull that data by referencing UUIDs and expected data formats (that would be the correct way).  Instead, it tries to decrpyt the associated data it finds by brute force, relying on a derived position in the packet of the data i needed.  If a Govee device with a different data format shows up, that will get messed up (I heard that the H5075 device does not work with this code - thanks to Tom Hiller for the spot). 

If more than one Govee device is in range, you'll see results jumping around as different devices are decrypted. To do this properly would need some way to link the ble address of the device you want to listen to and store it in shared preferences or something. I didn't want to spend time on that

Finally, the Govee devices do in fact store data and the iOS and Android apps have some way to retrieve the historic data over a few seconds. I did not look into this; if you are listening for advertisements you will get the data, if you don't listen, you will miss it
