package se.anyro.nfc_reader;

import android.nfc.NfcAdapter;
import android.util.Log;

import java.lang.reflect.Method;

public class NfcUtil {

    public static void nfcEnable(NfcAdapter adapter) {
        try {
            Class clazz = Class.forName("android.nfc.NfcAdapter");
            Method enable = clazz.getMethod("enable");
            enable.setAccessible(true);
            enable.invoke(adapter);

            Log.d("ZYPP", "调用enable方法成功");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("ZYPP", "e: " + e.getMessage());
        }
    }
    public static void nfcDisable(NfcAdapter adapter) {
        try {
            Class clazz = Class.forName("android.nfc.NfcAdapter");
            Method enable = clazz.getMethod("disable", boolean.class);
            enable.setAccessible(true);
            enable.invoke(adapter, false);

            Log.d("ZYPP", "调用disable方法成功");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("ZYPP", "e: " + e.getMessage());
        }
    }
}
