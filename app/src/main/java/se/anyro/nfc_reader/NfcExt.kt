package se.anyro.nfc_reader

import android.nfc.NfcAdapter
import android.util.Log


private const val TAG = "ZYPP"
private fun NfcAdapter.nfcEnable() {
    try {
        val clazz = Class.forName("android.nfc.NfcAdapter")
        val enable = clazz.getMethod("enable")
        // enable.setAccessible(true);
        enable.invoke(this)
        Log.d(TAG, "调用enable方法成功")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun NfcAdapter.nfcDisable() {
    try {
        val clazz = Class.forName("android.nfc.NfcAdapter")
        val enable = clazz.getMethod("disable", Boolean::class.java)
        // enable.setAccessible(true);
        enable.invoke(this, false)
        Log.d(TAG, "调用disable方法成功")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
