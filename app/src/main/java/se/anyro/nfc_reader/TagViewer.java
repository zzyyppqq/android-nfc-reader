/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2011 Adam Nybäck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.anyro.nfc_reader;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import se.anyro.nfc_reader.record.ParsedNdefRecord;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device just discovered.
 */
public class TagViewer extends Activity {

    public static final String TAG = "ZYPP";

    private static final DateFormat TIME_FORMAT = SimpleDateFormat.getDateTimeInstance();
    private LinearLayout mTagContent;

    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private NdefMessage mNdefPushMessage;

    private AlertDialog mDialog;

    private List<Tag> mTags = new ArrayList<>();


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "接收成功");
            String action = intent.getAction();
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                getStatus(intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF));
            }
        }

        private Handler mHandler = new Handler(Looper.getMainLooper());
        private void getStatus(int state) {
            Message msg = new Message();
            switch (state) {
                case NfcAdapter.STATE_OFF:
                    msg.obj = "close";
                    Log.i(TAG, "nfc state: " + state + ", str: " + msg.obj);
                    if (mAdapter != null) {
                        if (!mAdapter.isEnabled()) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    NfcUtil.nfcEnable(mAdapter);
                                }
                            }, 1000L);
                        } else {
                            Log.i("ZYPP", "nfc isEnabled true");
                        }
                    } else {
                        Log.i("ZYPP", "nfc mAdapter = null");
                    }
                    break;
                case NfcAdapter.STATE_ON:
                    msg.obj = "open";
                    Log.i(TAG, "nfc state: " + state + ", str: " + msg.obj);
                    if (mAdapter != null) {
                        Log.i("ZYPP", "nfc isEnabled " + mAdapter.isEnabled());
                    } else {
                        Log.i("ZYPP", "nfc mAdapter = null");
                    }
                    closeWirelessSettingsDialog();
                    break;
                case NfcAdapter.STATE_TURNING_OFF:
                    msg.obj = "turning off";
                    Log.i(TAG, "nfc state: " + state + ", str: " + msg.obj);
                    break;
                case NfcAdapter.STATE_TURNING_ON:
                    msg.obj = "turning on";
                    Log.i(TAG, "nfc state: " + state + ", str: " + msg.obj);
                    break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_viewer);
        mTagContent = (LinearLayout) findViewById(R.id.list);
        resolveIntent(getIntent());

        mDialog = new AlertDialog.Builder(this).setNeutralButton("Ok", null).create();

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mAdapter == null) {
            showMessage(R.string.error, R.string.no_nfc);
            finish();
            return;
        }

        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        mNdefPushMessage = new NdefMessage(new NdefRecord[]{newTextRecord(
                "Message from NFC Reader :-)", Locale.ENGLISH, true)});


        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
    }

    private void showMessage(int title, int message) {
        mDialog.setTitle(title);
        mDialog.setMessage(getText(message));
        mDialog.show();
    }

    private NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = text.getBytes(utfEncoding);

        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);

        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("ZYPP", "onResume");
        if (mAdapter != null) {
            Log.i("ZYPP", "onResume mAdapter isEnabled " + mAdapter.isEnabled());
            if (!mAdapter.isEnabled()) {
                //showWirelessSettingsDialog();
            }
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("*/*");    /* Handles all MIME based dispatches.
                                           You should specify only the ones that you need. */
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("fail", e);
            }
            IntentFilter[] intentFiltersArray = new IntentFilter[] {ndef, };

            String[][] techListsArray = new String[][] { new String[] { NfcF.class.getName() } };
            mAdapter.enableForegroundDispatch(this, mPendingIntent, null, techListsArray);
            mAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("ZYPP", "onPause");
        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
            mAdapter.disableForegroundNdefPush(this);
        }
    }

    private AlertDialog mAlertDialog = null;
    private void showWirelessSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.nfc_disabled);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        mAlertDialog = builder.create();
        mAlertDialog.show();
        Log.i("ZYPP", "showWirelessSettingsDialog mAlertDialog: " + mAlertDialog);
    }

    private void closeWirelessSettingsDialog() {
        Log.i("ZYPP", "closeWirelessSettingsDialog mAlertDialog: " + mAlertDialog);
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        Log.i("ZYPP", "resolveIntent action: " + action);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            // 从标签中解析出的一组 NDEF 消息。此 extra 对于 ACTION_NDEF_DISCOVERED Intent 而言是必需的。
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                Log.i("ZYPP", "resolveIntent rawMsgs size: " +rawMsgs.length);
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
                buildTagViews(msgs);
            }
            //else {
                // Unknown tag type
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                Log.i("ZYPP", "resolveIntent Tech id: " + toHex(tag.getId()) + ", TechList: " + Arrays.toString(tag.getTechList()));
                byte[] payload = dumpTagData(tag).getBytes();
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
                msgs = new NdefMessage[]{msg};
                mTags.add(tag);
            //}
            // Setup the views
            buildTagViews(msgs);

            resetNfc();
        }
    }

    private String dumpTagData(Tag tag) {
        StringBuilder sb = new StringBuilder();
        byte[] id = tag.getId();
        sb.append("ID (hex): ").append(toHex(id)).append('\n');
        sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n');
        sb.append("ID (dec): ").append(toDec(id)).append('\n');
        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                String type = "Unknown";
                try {
                    MifareClassic mifareTag;
                    try {
                        mifareTag = MifareClassic.get(tag);
                    } catch (Exception e) {
                        // Fix for Sony Xperia Z3/Z5 phones
                        tag = cleanupTag(tag);
                        mifareTag = MifareClassic.get(tag);
                    }
                    switch (mifareTag.getType()) {
                        case MifareClassic.TYPE_CLASSIC:
                            type = "Classic";
                            break;
                        case MifareClassic.TYPE_PLUS:
                            type = "Plus";
                            break;
                        case MifareClassic.TYPE_PRO:
                            type = "Pro";
                            break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                } catch (Exception e) {
                    sb.append("Mifare classic error: " + e.getMessage());
                }
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }

            if (tech.equals(Ndef.class.getName())) {
                sb.append('\n');
                Ndef ndefTag = Ndef.get(tag);
                String type = new String(ndefTag.getType());
                int maxSize = ndefTag.getMaxSize();
                int curSize = ndefTag.getCachedNdefMessage().getByteArrayLength();
                Log.i("ZYPP", "Ndef type: " + type + ", curSize: " + curSize + ", maxSize: " + maxSize);
                sb.append("Ndef type: " + type + ", curSize: " + curSize + ", maxSize: " + maxSize);
            }

            if (tech.equals(NdefFormatable.class.getName())) {
                NdefFormatable ndefFormatableTag = NdefFormatable.get(tag);
                Log.i("ZYPP", "NdefFormatable ndefFormatableTag: " + ndefFormatableTag);
            }

            if (tech.equals(NfcA.class.getName())) {
                sb.append('\n');
                NfcA nfcATag = NfcA.get(tag);
                byte[] atqa = nfcATag.getAtqa();
                Short sak = nfcATag.getSak();
                int timeout = nfcATag.getTimeout();
                int maxTransceiveLength = nfcATag.getMaxTransceiveLength();
                Log.i("ZYPP", "NfcA ATQA: " + toHex(atqa) + ", SAK: " + sak + ", timeout: " + timeout + ", maxTransceiveLength: " + maxTransceiveLength);
                sb.append("NfcA ATQA: " + toHex(atqa) + ", SAK: " + sak + ", timeout: " + timeout + ", maxTransceiveLength: " + maxTransceiveLength);
            }

            if (tech.equals(NfcB.class.getName())) {
                NfcB nfcBTag = NfcB.get(tag);
                byte[] applicationData = nfcBTag.getApplicationData();
                byte[] protocolInfo = nfcBTag.getProtocolInfo();
                int maxTransceiveLength = nfcBTag.getMaxTransceiveLength();
                Log.i("ZYPP", "NfcB applicationData: " + toHex(applicationData) + ", protocolInfo: " + toHex(protocolInfo) + ", maxTransceiveLength: " + maxTransceiveLength);
            }

            if (tech.equals(NfcF.class.getName())) {
                NfcF nfcFTag = NfcF.get(tag);
                byte[] manufacturer = nfcFTag.getManufacturer();
                byte[] systemCode = nfcFTag.getSystemCode();
                int timeout = nfcFTag.getTimeout();
                int maxTransceiveLength = nfcFTag.getMaxTransceiveLength();
                Log.i("ZYPP", "NfcF manufacturer: " + toHex(manufacturer) + ", systemCode: " + toHex(systemCode) + ", timeout: " + timeout + ", maxTransceiveLength: " + maxTransceiveLength);
            }

            if (tech.equals(NfcV.class.getName())) {
                NfcV nfcVTag = NfcV.get(tag);
                byte dsfId = nfcVTag.getDsfId();
                byte responseFlags = nfcVTag.getResponseFlags();
                int maxTransceiveLength = nfcVTag.getMaxTransceiveLength();
                Log.i("ZYPP", "NfcV dsfId: " + dsfId + ", responseFlags: " + toHex(new byte[]{responseFlags}) + ", maxTransceiveLength: " + maxTransceiveLength);
            }

            if (tech.equals(IsoDep.class.getName())) {
                IsoDep isoDepTag = IsoDep.get(tag);
                byte[] hiLayerResponse = isoDepTag.getHiLayerResponse();
                byte[] historicalBytes = isoDepTag.getHistoricalBytes();
                int timeout = isoDepTag.getTimeout();
                int maxTransceiveLength = isoDepTag.getMaxTransceiveLength();
                Log.i("ZYPP", "IsoDep hiLayerResponse: " + toHex(hiLayerResponse) + ", historicalBytes: " + toHex(historicalBytes) + ", timeout: " + timeout + ", maxTransceiveLength: " + maxTransceiveLength);
            }
        }

        return sb.toString();
    }

    private Tag cleanupTag(Tag oTag) {
        if (oTag == null)
            return null;

        String[] sTechList = oTag.getTechList();

        Parcel oParcel = Parcel.obtain();
        oTag.writeToParcel(oParcel, 0);
        oParcel.setDataPosition(0);

        int len = oParcel.readInt();
        byte[] id = null;
        if (len >= 0) {
            id = new byte[len];
            oParcel.readByteArray(id);
        }
        int[] oTechList = new int[oParcel.readInt()];
        oParcel.readIntArray(oTechList);
        Bundle[] oTechExtras = oParcel.createTypedArray(Bundle.CREATOR);
        int serviceHandle = oParcel.readInt();
        int isMock = oParcel.readInt();
        IBinder tagService;
        if (isMock == 0) {
            tagService = oParcel.readStrongBinder();
        } else {
            tagService = null;
        }
        oParcel.recycle();

        int nfca_idx = -1;
        int mc_idx = -1;
        short oSak = 0;
        short nSak = 0;

        for (int idx = 0; idx < sTechList.length; idx++) {
            if (sTechList[idx].equals(NfcA.class.getName())) {
                if (nfca_idx == -1) {
                    nfca_idx = idx;
                    if (oTechExtras[idx] != null && oTechExtras[idx].containsKey("sak")) {
                        oSak = oTechExtras[idx].getShort("sak");
                        nSak = oSak;
                    }
                } else {
                    if (oTechExtras[idx] != null && oTechExtras[idx].containsKey("sak")) {
                        nSak = (short) (nSak | oTechExtras[idx].getShort("sak"));
                    }
                }
            } else if (sTechList[idx].equals(MifareClassic.class.getName())) {
                mc_idx = idx;
            }
        }

        boolean modified = false;

        if (oSak != nSak) {
            oTechExtras[nfca_idx].putShort("sak", nSak);
            modified = true;
        }

        if (nfca_idx != -1 && mc_idx != -1 && oTechExtras[mc_idx] == null) {
            oTechExtras[mc_idx] = oTechExtras[nfca_idx];
            modified = true;
        }

        if (!modified) {
            return oTag;
        }

        Parcel nParcel = Parcel.obtain();
        nParcel.writeInt(id.length);
        nParcel.writeByteArray(id);
        nParcel.writeInt(oTechList.length);
        nParcel.writeIntArray(oTechList);
        nParcel.writeTypedArray(oTechExtras, 0);
        nParcel.writeInt(serviceHandle);
        nParcel.writeInt(isMock);
        if (isMock == 0) {
            nParcel.writeStrongBinder(tagService);
        }
        nParcel.setDataPosition(0);

        Tag nTag = Tag.CREATOR.createFromParcel(nParcel);

        nParcel.recycle();

        return nTag;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private long toReversedDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;

        // Parse the first message in the list
        // Build views for all of the sub records
        Date now = new Date();
        List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
        final int size = records.size();
        for (int i = 0; i < size; i++) {
            TextView timeView = new TextView(this);
            timeView.setText(TIME_FORMAT.format(now));
            content.addView(timeView, 0);
            ParsedNdefRecord record = records.get(i);
            content.addView(record.getView(this, inflater, content, i), 1 + i);
            content.addView(inflater.inflate(R.layout.tag_divider, content, false), 2 + i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (mTags.size() == 0) {
            Toast.makeText(this, R.string.nothing_scanned, Toast.LENGTH_LONG).show();
            return true;
        }

        switch (item.getItemId()) {
//            case R.id.menu_main_clear:
//                clearTags();
//                return true;
//            case R.id.menu_copy_hex:
//                copyIds(getIdsHex());
//                return true;
//            case R.id.menu_copy_reversed_hex:
//                copyIds(getIdsReversedHex());
//                return true;
//            case R.id.menu_copy_dec:
//                copyIds(getIdsDec());
//                return true;
//            case R.id.menu_copy_reversed_dec:
//                copyIds(getIdsReversedDec());
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearTags() {
        mTags.clear();
        for (int i = mTagContent.getChildCount() - 1; i >= 0; i--) {
            View view = mTagContent.getChildAt(i);
            if (view.getId() != R.id.tag_viewer_text) {
                mTagContent.removeViewAt(i);
            }
        }
    }

    private void copyIds(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("NFC IDs", text);
        clipboard.setPrimaryClip(clipData);
        Toast.makeText(this, mTags.size() + " IDs copied", Toast.LENGTH_SHORT).show();
    }

    private String getIdsHex() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(toHex(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString().replace(" ", "");
    }

    private String getIdsReversedHex() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(toReversedHex(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString().replace(" ", "");
    }

    private String getIdsDec() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(toDec(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString();
    }

    private String getIdsReversedDec() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(toReversedDec(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString();
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    private void resetNfc() {
        Log.i("ZYPP", "resetNfc mAdapter: " + mAdapter);
        if (mAdapter != null) {
            NfcUtil.nfcDisable(mAdapter);
        }
    }
}