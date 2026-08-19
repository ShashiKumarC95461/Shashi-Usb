package com.example.usbmediaexplorer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UsbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.hardware.usb.action.USB_DEVICE_ATTACHED" ->
                Toast.makeText(context, "USB device connected — open USB Media Explorer to scan", Toast.LENGTH_SHORT).show()
            "android.hardware.usb.action.USB_DEVICE_DETACHED" ->
                Toast.makeText(context, "USB device disconnected", Toast.LENGTH_SHORT).show()
        }
    }
}
