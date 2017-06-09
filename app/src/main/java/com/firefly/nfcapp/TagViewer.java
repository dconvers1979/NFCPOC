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
package com.firefly.nfcapp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.firefly.nfcapp.util.NFCOptions;
import com.firefly.nfcapp.util.Util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An {Activity} which handles a broadcast of a new tag that the device just discovered.
 */
public class TagViewer extends Activity {

    private static final String LOG_TAG = "NFCUtil";
    public  static final String NO_KEY = "XXXXXXXXXXXX";
    public  static final String NO_DATA = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";

    private static final DateFormat TIME_FORMAT = SimpleDateFormat.getDateTimeInstance();
    private LinearLayout mTagContent;

    private Tag mTag;
    private NfcAdapter mAdapter;
    private MifareClassic mMFC;
    private ListView lvData;
    private Sample[] mSamples = {};
    private PendingIntent mPendingIntent;
    private NdefMessage mNdefPushMessage;

    private SparseArray<byte[][]> mKeyMap = new SparseArray<byte[][]>();
    private int mKeyMapStatus = 0;
    private int mLastSector = -1;
    private int mFirstSector = 0;
    private HashSet<byte[]> mKeys;

    private AlertDialog mDialog;

    private List<Tag> mTags = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_viewer);
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
        mNdefPushMessage = new NdefMessage(new NdefRecord[] { newTextRecord(
                "Message from NFC Reader :-)", Locale.ENGLISH, true) });

        // Prepare the GridView
        lvData = (ListView) findViewById(R.id.dataList);
        lvData.setAdapter(new SampleAdapter());
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
        if (mAdapter != null) {
            if (!mAdapter.isEnabled()) {
                showWirelessSettingsDialog();
            }
            mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
            mAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
            mAdapter.disableForegroundNdefPush(this);
        }
    }

    public void openWriter(View view) {
        if(mTag != null)
        openWriter(mTag);
    }

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
        builder.create().show();
        return;
    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            mTag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            mMFC = MifareClassic.get(mTag);
            try {
                mMFC.connect();
                // Setup the views
                buildTagViews(mTag);
                mMFC.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public void openWriter(Tag tag){
        Intent intent = new Intent(this, TagWriter.class);
        intent.putExtra(NfcAdapter.EXTRA_TAG, tag);
        startActivity(intent);
    }

    void buildTagViews(Tag tag) {
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;

        // Parse the first message in the list
        // Build views for all of the sub records
        Date now = new Date();
        TextView tvId = (TextView)findViewById(R.id.tvId);
        tvId.setText(String.valueOf(Util.toDec(tag.getId())));
        TextView tvTech = (TextView)findViewById(R.id.tvTech);
        tvTech.setText(Arrays.toString(tag.getTechList()));
        TextView tvMem = (TextView)findViewById(R.id.tvMem);
        tvMem.setText(String.valueOf(tag.describeContents()));
        TextView tvTime = (TextView)findViewById(R.id.tvTime);
        tvTime.setText(TIME_FORMAT.format(now));
        loadBlockData();
    }

    public void loadBlockData() {

        mSamples = new Sample[mMFC.getSectorCount()];
        for (int i = 0; i < mMFC.getSectorCount(); i++) {
            try {
                String[] val = null;
                for(int x = 0; x < Util.KEY_DEFAULT_A.length; x++) {
                    val = readSector(i, Util.KEY_DEFAULT_A[x], false);
                    if (val != null)
                        break;
                }
                if (val == null)
                    val = readSector(i, Util.KEY_DEFAULT_B, true);
                if(val== null)
                    val = readSector(i, Util.KEY_NEW_A, false);
                if(val== null)
                    val = readSector(i, Util.KEY_NEW_B, true);
                mSamples[i] = new Sample("Sector "+ (i+1) +":",Arrays.toString(val));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        lvData.setAdapter(new SampleAdapter());
        lvData.requestLayout();
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
        case R.id.menu_main_clear:
            return true;
        case R.id.menu_copy_hex:
            copyIds(getIdsHex());
            return true;
        case R.id.menu_copy_reversed_hex:
            copyIds(getIdsReversedHex());
            return true;
        case R.id.menu_copy_dec:
            copyIds(getIdsDec());
            return true;
        case R.id.menu_copy_reversed_dec:
            copyIds(getIdsReversedDec());
            return true;
        default:
            return super.onOptionsItemSelected(item);
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
            builder.append(Util.toHex(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString().replace(" ", "");
    }

    private String getIdsReversedHex() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(Util.toReversedHex(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString().replace(" ", "");
    }

    private String getIdsDec() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(Util.toDec(tag.getId()));
            builder.append('\n');
        }
        builder.setLength(builder.length() - 1); // Remove last new line
        return builder.toString();
    }

    private String getIdsReversedDec() {
        StringBuilder builder = new StringBuilder();
        for (Tag tag : mTags) {
            builder.append(Util.toReversedDec(tag.getId()));
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

    public Tag getmTag() {
        return mTag;
    }

    public void setmTag(Tag mTag) {
        this.mTag = mTag;
    }


    //Mifare Classic

    /**
     * Read as much as possible from the tag with the given key information.
     * @param keyMap Keys (A and B) mapped to a sector.
     * See {@link #buildNextKeyMapPart()}.
     * @return A Key-Value Pair. Keys are the sector numbers, values
     * are the tag data. This tag data (values) are arrays containing
     * one block per field (index 0-3 or 0-15).
     * If a block is "null" it means that the block couldn't be
     * read with the given key information.<br />
     * On Error "null" will be returned (most likely the tag was removed
     * during reading). If none of the keys in the key map is valid for reading
     * and therefore no sector is read, an empty set (SparseArray.size() == 0)
     * will be returned.
     * @see #buildNextKeyMapPart()
     */
    public SparseArray<String[]> readAsMuchAsPossible(
            SparseArray<byte[][]> keyMap) {
        SparseArray<String[]> ret = null;
        if (keyMap != null && keyMap.size() > 0) {
            ret = new SparseArray<String[]>(keyMap.size());
            // For all entries in map do:
            for (int i = 0; i < keyMap.size(); i++) {
                String[][] results = new String[2][];
                try {
                    if (keyMap.valueAt(i)[0] != null) {
                        // Read with key A.
                        results[0] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[0], false);
                    }
                    if (keyMap.valueAt(i)[1] != null) {
                        // Read with key B.
                        results[1] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[1], true);
                    }
                } catch (TagLostException e) {
                    return null;
                }
                // Merge results.
                if (results[0] != null || results[1] != null) {
                    ret.put(keyMap.keyAt(i), mergeSectorData(
                            results[0], results[1]));
                }
            }
            return ret;
        }
        return ret;
    }

    /**
     * Read as much as possible from the tag depending on the
     * mapping range and the given key information.
     * The key information must be set before calling this method
     * (use { #setKeyFile(File[])}).
     * Also the mapping range must be specified before calling this method
     * (use { #setMappingRange(int, int)}).
     * Attention: This method builds a key map. Depending on the key count
     * in the given key file, this could take up to minutes and more.
     * The old key map from { #getKeyMap()} will be destroyed and
     * the full new one is getable afterwards.
     * return A Key-Value Pair. Keys are the sector numbers, values
     * are the tag data. This tag data (values) are arrays containing
     * one block per field (index 0-3 or 0-15).
     * If a block is "NULL" it means that the block couldn't be
     * read with the given key information.
     *  #buildNextKeyMapPart()
     *  #setKeyFile(File[])
     */
    public SparseArray<String[]> readAsMuchAsPossible() {
        mKeyMapStatus = mMFC.getSectorCount();
        while (buildNextKeyMapPart() < mMFC.getSectorCount()-1);
        return readAsMuchAsPossible(mKeyMap);
    }

    /**
     * Read a as much as possible from a sector with the given key.
     * Best results are gained from a valid key B (except key B is marked as
     * readable in the access conditions).
     *  sectorIndex Index of the Sector to read. (For Mifare Classic 1K:
     * 0-63)
     *  key Key for the authentication.
     *  useAsKeyB If true, key will be treated as key B
     * for authentication.
     *  Array of blocks (index 0-3 or 0-15). If a block or a key is
     * marked with { #NO_DATA} or { #NO_KEY}
     * it means that this data could be read or found. On authentication error
     * "null" will be returned.
     *  TagLostException When tag is lost.
     *  #mergeSectorData(String[], String[])
     */
    public String[] readSector(int sectorIndex, byte[] key, boolean useAsKeyB)
            throws TagLostException {
        boolean auth = NFCOptions.authenticate(mMFC, sectorIndex, key, useAsKeyB);
        String[] ret = null;
        // Read sector.
        if (auth) {
            // Read all blocks.
            ArrayList<String> blocks = new ArrayList<String>();
            int firstBlock = mMFC.sectorToBlock(sectorIndex);
            int lastBlock = firstBlock + 4;
            if (mMFC.getSize() == MifareClassic.SIZE_4K
                    && sectorIndex > 31) {
                lastBlock = firstBlock + 16;
            }
            for (int i = firstBlock; i < lastBlock; i++) {
                try {
                    blocks.add(Util.byte2HexString(
                            mMFC.readBlock(i)));
                } catch (TagLostException e) {
                    throw e;
                } catch (IOException e) {
                    // Could not read block.
                    // (Maybe due to key/authentication method.)
                    Log.d(LOG_TAG, "Error while reading block "
                            + i + " from tag.");
                    blocks.add(NO_DATA);
                    if (!mMFC.isConnected()) {
                        throw new TagLostException(
                                "Tag removed during readSector(...)");
                    }
                    // After error reauthentication is needed.
                    auth = NFCOptions.authenticate(mMFC, sectorIndex, key, useAsKeyB);
                }
            }
            ret = blocks.toArray(new String[blocks.size()]);
            int last = ret.length -1;
            // Merge key in last block (sector trailer).
            if (!useAsKeyB) {
                if (Util.isKeyBReadable(Util.hexStringToByteArray(
                        ret[last].substring(12, 20)))) {
                    ret[last] = Util.byte2HexString(key)
                            + ret[last].substring(12, 32);
                } else {
                    ret[last] = Util.byte2HexString(key)
                            + ret[last].substring(12, 20) + NO_KEY;
                }
            } else {
                if (ret[0].equals(NO_DATA)) {
                    // If Key B may be read in the corresponding Sector Trailer,
                    // it cannot serve for authentication (according to NXP).
                    // What they mean is that you can authenticate successfully,
                    // but can not read data. In this case the
                    // readBlock() result is 0 for each block.
                    ret = null;
                } else {
                    ret[last] = NO_KEY + ret[last].substring(12, 20)
                            + Util.byte2HexString(key);
                }
            }
        }
        return ret;
    }

    /**
     * Build Key-Value Pairs in which keys represent the sector and
     * values are one or both of the Mifare keys (A/B).
     * The Mifare key information must be set before calling this method
     * (use {@link #setKeyFile(File[])}).
     * Also the mapping range must be specified before calling this method
     * (use {@link #setMappingRange(int, int)}).<br /><br />
     * The mapping works like some kind of dictionary attack.
     * All keys are checked against the next sector
     * with both authentication methods (A/B). If at least one key was found
     * for a sector, the map will be extended with an entry, containing the
     * key(s) and the information for what sector the key(s) are. You can get
     * this Key-Value Pairs by calling { #getKeyMap()}. A full
     * key map can be gained by calling this method as often as there are
     * sectors on the tag (See { #getSectorCount()}). If you call
     * this method once more after a full key map was created, it resets the
     * key map an starts all over.
     * The sector that was checked at the moment. On error it returns
     * "-1" and resets the key map to "null".
     *  #getKeyMap()
     *  #setKeyFile(File[])
     *  #setMappingRange(int, int)
     *  #readAsMuchAsPossible(SparseArray)
     */
    public int buildNextKeyMapPart() {
        // Clear status and key map before new walk trough sectors.
        boolean error = false;
        if (mKeys != null && mLastSector != -1) {
            if (mKeyMapStatus == mLastSector+1) {
                mKeyMapStatus = mFirstSector;
                mKeyMap = new SparseArray<byte[][]>();
            }

            byte[][] keys = new byte[2][];
            boolean[] foundKeys = new boolean[] {false, false};
            try {
                // Check next sector against all keys (lines) with
                // authentication method A and B.
                for (byte[] key : mKeys) {
                    if (!foundKeys[0] &&
                            mMFC.authenticateSectorWithKeyA(
                                    mKeyMapStatus, key)) {
                        keys[0] = key;
                        foundKeys[0] = true;
                    }
                    if (!foundKeys[1] &&
                            mMFC.authenticateSectorWithKeyB(
                                    mKeyMapStatus, key)) {
                        keys[1] = key;
                        foundKeys[1] = true;
                    }
                    if (foundKeys[0] && foundKeys[1]) {
                        // Both keys found. Continue with next sector.
                        break;
                    }
                }
                if (foundKeys[0] || foundKeys[1]) {
                    // At least one key found. Add key(s).
                    mKeyMap.put(mKeyMapStatus, keys);
                }
                mKeyMapStatus++;
            } catch (Exception e) {
                Log.d(LOG_TAG, "Error while building next key map part");
                error = true;
            }
        } else {
            error = true;
        }

        if (error) {
            mKeyMapStatus = 0;
            mKeyMap = null;
            return -1;
        }
        return mKeyMapStatus - 1;
    }

    /**
     * Merge the result of two { #readSector(int, byte[], boolean)}
     * calls on the same sector (with different keys or authentication methods).
     * In this case merging means empty blocks will be overwritten with non
     * empty ones and the keys will be added correctly to the sector trailer.
     * The access conditions will be taken from the first (firstResult)
     * parameter if it is not null.
     *  firstResult First
     * { #readSector(int, byte[], boolean)} result.
     *  secondResult Second
     * { #readSector(int, byte[], boolean)} result.
     *  Array (sector) as result of merging the given
     * sectors. If a block is { #NO_DATA} it
     * means that none of the given sectors contained data from this block.
     *  #readSector(int, byte[], boolean)
     *  #authenticate(int, byte[], boolean)
     */
    public String[] mergeSectorData(String[] firstResult,
                                    String[] secondResult) {
        String[] ret = null;
        if (firstResult != null || secondResult != null) {
            if ((firstResult != null && secondResult != null)
                    && firstResult.length != secondResult.length) {
                return null;
            }
            int length  = (firstResult != null)
                    ? firstResult.length : secondResult.length;
            ArrayList<String> blocks = new ArrayList<String>();
            // Merge data blocks.
            for (int i = 0; i < length -1 ; i++) {
                if (firstResult != null && firstResult[i] != null
                        && !firstResult[i].equals(NO_DATA)) {
                    blocks.add(firstResult[i]);
                } else if (secondResult != null && secondResult[i] != null
                        && !secondResult[i].equals(NO_DATA)) {
                    blocks.add(secondResult[i]);
                } else {
                    // Non of the results got the data for the block.
                    blocks.add(NO_DATA);
                }
            }
            ret = blocks.toArray(new String[blocks.size() + 1]);
            int last = length - 1;
            // Merge sector trailer.
            if (firstResult != null && firstResult[last] != null
                    && !firstResult[last].equals(NO_DATA)) {
                // Take first for sector trailer.
                ret[last] = firstResult[last];
                if (secondResult != null && secondResult[last] != null
                        && !secondResult[last].equals(NO_DATA)) {
                    // Merge key form second result to sector trailer.
                    ret[last] = ret[last].substring(0, 20)
                            + secondResult[last].substring(20);
                }
            } else if (secondResult != null && secondResult[last] != null
                    && !secondResult[last].equals(NO_DATA)) {
                // No first result. Take second result as sector trailer.
                ret[last] = secondResult[last];
            } else {
                // No sector trailer at all.
                ret[last] = NO_DATA;
            }
        }
        return ret;
    }

    /**
     * Set the key files for {@link #buildNextKeyMapPart()}.
     * @param keyFiles One or more key files.
     * These files are simple text files with one key
     * per line. Empty lines and lines STARTING with "#"
     * will not be interpreted.
     */
    public void setKeyFile(File[] keyFiles) {
        mKeys = new HashSet<byte[]>();
        for (File file : keyFiles) {
            String[] lines = Util.readFileLineByLine(file, false);
            if (lines != null) {
                for (String line : lines) {
                    if (!line.equals("") && line.length() == 12
                            && line.matches("[0-9A-Fa-f]+")) {
                        mKeys.add(Util.hexStringToByteArray(line));
                    }
                }
            }
        }
    }

    /**
     * Set the mapping range for {@link #buildNextKeyMapPart()}.
     * @param firstSector Index of the first sector of the key map.
     * @param lastSector Index of the last sector of the key map.
     * @return True if range parameters were correct. False otherwise.
     */
    public boolean setMappingRange(int firstSector, int lastSector) {
        if (firstSector >= 0 && lastSector < mMFC.getSectorCount()
                && firstSector <= lastSector) {
            mFirstSector = firstSector;
            mLastSector = lastSector;
            // Init. status of buildNextKeyMapPart to create a new key map.
            mKeyMapStatus = lastSector+1;
            return true;
        }
        return false;
    }

    /**
     * Check if the Mifare Classic tag has the factory Mifare Classic Access
     * Conditions (0xFF0780) and the standard key A
     * (0xFFFFFFFFFFFF).
     * @return True if tag has factory ACs and factory key A, False otherwise.
     */
    public boolean isCleanTag() {
        int blockIndex = 0;
        for (int i = 0; i < mMFC.getSectorCount(); i++) {
            // Authenticate.
            if (!NFCOptions.authenticate(mMFC, i, MifareClassic.KEY_DEFAULT, false)) {
                return false;
            }
            // Read.
            byte[] data = null;
            blockIndex += mMFC.getBlockCountInSector(i);
            try {
                data = mMFC.readBlock(blockIndex-1);
            } catch (IOException e) {
                Log.d(LOG_TAG, "Error while reading block from tag.");
                return false;
            }
            // Extract Access Conditions.
            String ac = Util.byte2HexString(data).substring(12, 18);
            // Check Access Conditions (= Factory settings).
            if (!ac.equals("FF0780")) {
                return false;
            }
        }
        return true;
    }



    // Adapter for List View
    private class SampleAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mSamples.length;
        }

        @Override
        public Object getItem(int position) {
            return mSamples[position];
        }

        @Override
        public long getItemId(int position) {
            return mSamples[position].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.tag_viewer_data,
                        container, false);
            }

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(
                    mSamples[position].title);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(
                    mSamples[position].description);
            return convertView;
        }
    }

    private class Sample {
        String title;
        String description;

        public Sample(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }
}