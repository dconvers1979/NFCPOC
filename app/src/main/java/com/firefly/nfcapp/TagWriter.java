package com.firefly.nfcapp;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.firefly.nfcapp.util.NFCOptions;
import com.firefly.nfcapp.util.Util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;


public class TagWriter extends Activity {

    private static final String LOG_TAG = "NFCUtil";

    Tag tag;
    private MifareClassic mMFC;
    Spinner sectorSpin;
    Spinner blockSpin;
    EditText input;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_writer);

        Intent intent = getIntent();
        tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        mMFC = MifareClassic.get(tag);
        TextView title = (TextView) findViewById(R.id.writerTitle);
        title.setText("Id: "+ Util.toReversedHex(tag.getId()));

        sectorSpin=(Spinner) findViewById(R.id.sectorSpinner);
        Integer[] items_s = new Integer[]{2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
        ArrayAdapter<Integer> adapter_s = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, items_s);
        sectorSpin.setAdapter(adapter_s);
        blockSpin=(Spinner) findViewById(R.id.blockSpinner);
        Integer[] items_b = new Integer[]{1,2,3};
        ArrayAdapter<Integer> adapter_b = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, items_b);
        blockSpin.setAdapter(adapter_b);

        input = (EditText) findViewById(R.id.writerInput);
    }

    public void writeMessage(View view){
        try {

            String value = input.getText().toString();
            Integer sector = (Integer)sectorSpin.getSelectedItem();
            Integer block = (Integer)blockSpin.getSelectedItem();
            while(value.length() < 16){
                value = value + " ";
            }

            MifareClassic mfc = MifareClassic.get(tag);
            try {
                mfc.connect();
                boolean authA = mfc.authenticateSectorWithKeyA(sector - 1, Util.KEY_DEFAULT_A[0]);
                if(!authA)
                    authA = mfc.authenticateSectorWithKeyA(sector - 1, Util.KEY_NEW_A);
                if(!authA) {
                    Toast.makeText(this, "Failed: Can't Authenticate on Sector", Toast.LENGTH_LONG)
                            .show();
                }
                else {
                    int index = mfc.sectorToBlock(sector - 1);
                    mfc.writeBlock(index + (block - 1), value.getBytes());
                    Toast.makeText(this, "Success: Wrote placed to nfc tag", Toast.LENGTH_LONG)
                            .show();
                }
                mfc.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                Toast.makeText(this, "Failed: Can´ wrote to nfc tag", Toast.LENGTH_LONG)
                        .show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed: Can´ wrote to nfc tag", Toast.LENGTH_LONG)
                        .show();
            }
        }catch(Exception e){
            Toast.makeText(this, "Failed: Can´ wrote to nfc tag", Toast.LENGTH_LONG)
                    .show();
        }
    }

    public void configureCard(View view){
        boolean keya = false;
        boolean keyb = false;

        Integer sector = (Integer)sectorSpin.getSelectedItem();

        MifareClassic mfc = MifareClassic.get(tag);
        try {
            mfc.connect();
            if(mfc.authenticateSectorWithKeyA(sector - 1, Util.KEY_DEFAULT_A[0])){
                byte[] value = new byte[16];
                value[0] = Util.KEY_NEW_A[0];value[1] = Util.KEY_NEW_A[1];value[2] = Util.KEY_NEW_A[2];value[3] = Util.KEY_NEW_A[3];
                value[4] = Util.KEY_NEW_A[4];value[5] = Util.KEY_NEW_A[5];
                value[6] = 0x7F;value[7] = 0x07;value[8] = (byte) 0x88;value[9] = (byte) 0x40;
                value[10] = Util.KEY_NEW_B[0];value[11] = Util.KEY_NEW_B[1];value[12] = Util.KEY_NEW_B[2];value[13] = Util.KEY_NEW_B[3];
                value[14] = Util.KEY_NEW_B[4];value[15] = Util.KEY_NEW_B[5];
                int index = mfc.sectorToBlock(sector - 1);
                mfc.writeBlock(index + 3, value);
                mfc.close();
            }
            else{
                keya = true;
                mfc.close();
            }
            mfc.connect();
            if(mfc.authenticateSectorWithKeyB(sector - 1, Util.KEY_DEFAULT_B)){
                byte[] value = new byte[16];
                value[0] = Util.KEY_NEW_A[0];value[1] = Util.KEY_NEW_A[1];value[2] = Util.KEY_NEW_A[2];value[3] = Util.KEY_NEW_A[3];
                value[4] = Util.KEY_NEW_A[4];value[5] = Util.KEY_NEW_A[5];
                value[6] = 0x7F;value[7] = 0x07;value[8] = (byte) 0x88;value[9] = (byte) 0x40;
                value[10] = Util.KEY_NEW_B[0];value[11] = Util.KEY_NEW_B[1];value[12] = Util.KEY_NEW_B[2];value[13] = Util.KEY_NEW_B[3];
                value[14] = Util.KEY_NEW_B[4];value[15] = Util.KEY_NEW_B[5];
                int index = mfc.sectorToBlock(sector - 1);
                mfc.writeBlock(index + 3, value);
                mfc.close();
            }
            else{
                keyb = true;
                mfc.close();
            }

            if(keya && keyb) {
                Toast.makeText(this, "Success: Sector Already Configured", Toast.LENGTH_LONG)
                        .show();
            }
            else{
                Toast.makeText(this, "Success: Sector Has Been Configured", Toast.LENGTH_LONG)
                        .show();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Toast.makeText(this, "Failed: Can´ wrote to nfc tag", Toast.LENGTH_LONG)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed: Can´ wrote to nfc tag", Toast.LENGTH_LONG)
                    .show();
        }
    }

    // Mifare Classic

    /**
     * Write a block of 16 Byte data to tag.
     * @param sectorIndex The sector to where the data should be written
     * @param blockIndex The block to where the data should be written
     * @param data 16 Byte of data.
     * @param key The Mifare Classic key for the given sector.
     * @param useAsKeyB If true, key will be treated as key B
     * for authentication.
     * @return The return codes are:<br />
     * <ul>
     * <li>0 - Everything went fine.</li>
     * <li>1 - Sector index is out of range.</li>
     * <li>2 - Block index is out of range.</li>
     * <li>3 - Data are not 16 Byte.</li>
     * <li>4 - Authentication went wrong.</li>
     * <li>5 - Error while writing to tag.</li>
     * </ul>
     *  #authenticate(int, byte[], boolean)
     */
    public int writeBlock(int sectorIndex, int blockIndex, byte[] data,
                          byte[] key, boolean useAsKeyB) {
        if (mMFC.getSectorCount()-1 < sectorIndex) {
            return 1;
        }
        if (mMFC.getBlockCountInSector(sectorIndex)-1 < blockIndex) {
            return 2;
        }
        if (data.length != 16) {
            return 3;
        }
        if (!NFCOptions.authenticate(mMFC, sectorIndex, key, useAsKeyB)) {
            return 4;
        }
        // Write block.
        int block = mMFC.sectorToBlock(sectorIndex) + blockIndex;
        try {
            mMFC.writeBlock(block, data);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing block to tag.", e);
            return 5;
        }
        return 0;
    }

    /**
     * This method checks if the present tag is writable with the provided keys
     * on the given positions (sectors, blocks). This is done by authenticating
     * with one of the keys followed by reading and interpreting
     * ({Common#getOperationInfoForBlock(byte, byte, byte,
     * de.syss.MifareClassicTool.Common.Operations, boolean, boolean)}) of the
     * Access Conditions.
     * @param pos A map of positions (key = sector, value = Array of blocks).
     * For each of these positions you will get the write information
     * (see return values).
     * @param keyMap A key map a generated by {CreateKeyMapActivity}.
     * @return A map within a map (all with type = Integer).
     * The key of the outer map is the sector number and the value is another
     * map with key = block number and value = write information.
     * The write information indicates which key is needed to write to the
     * present tag on the given position.<br /><br />
     * Write informations are:<br />
     * <ul>
     * <li>0 - Never</li>
     * <li>1 - Key A</li>
     * <li>2 - Key B</li>
     * <li>3 - Key A|B</li>
     * <li>4 - Key A, but AC never</li>
     * <li>5 - Key B, but AC never</li>
     * <li>6 - Key B, but keys never</li>
     * <li>-1 - Error</li>
     * <li>Inner map == null - Whole sector is dead (IO Error)</li>
     * </ul>
     */
    public HashMap<Integer, HashMap<Integer, Integer>> isWritableOnPositions(
            HashMap<Integer, int[]> pos,
            SparseArray<byte[][]> keyMap) {
        HashMap<Integer, HashMap<Integer, Integer>> ret =
                new HashMap<Integer, HashMap<Integer,Integer>>();
        for (int i = 0; i < keyMap.size(); i++) {
            int sector = keyMap.keyAt(i);
            if (pos.containsKey(sector)) {
                byte[][] keys = keyMap.get(sector);
                byte[] ac = null;
                // Authenticate.
                if (keys[0] != null) {
                    if (NFCOptions.authenticate(mMFC, sector, keys[0], false) == false) {
                        return null;
                    }
                } else if (keys[1] != null) {
                    if (NFCOptions.authenticate(mMFC, sector, keys[1], true) == false) {
                        return null;
                    }
                } else {
                    return null;
                }
                // Read Mifare Access Conditions.
                int acBlock = mMFC.sectorToBlock(sector)
                        + mMFC.getBlockCountInSector(sector) -1;
                try {
                    ac = mMFC.readBlock(acBlock);
                } catch (IOException e) {
                    ret.put(sector, null);
                    continue;
                }
                ac = Arrays.copyOfRange(ac, 6, 9);
                byte[][] acMatrix = Util.acToACMatrix(ac);
                boolean isKeyBReadable = Util.isKeyBReadable(
                        acMatrix[0][3], acMatrix[1][3], acMatrix[2][3]);

                // Check all Blocks with data (!= null).
                HashMap<Integer, Integer> blockWithWriteInfo =
                        new HashMap<Integer, Integer>();
                for (int block : pos.get(sector)) {
                    if ((block == 3 && sector <= 31)
                            || (block == 15 && sector >=32)) {
                        // Sector Trailer.
                        // Are the Access Bits writable?
                        int acValue = Util.getOperationInfoForBlock(
                                acMatrix[0][block],
                                acMatrix[1][block],
                                acMatrix[2][block],
                                Util.Operations.WriteAC,
                                true, isKeyBReadable);
                        // Is key A writable? (If so, key B will be writable
                        // with the same key.)
                        int keyABValue = Util.getOperationInfoForBlock(
                                acMatrix[0][block],
                                acMatrix[1][block],
                                acMatrix[2][block],
                                Util.Operations.WriteKeyA,
                                true, isKeyBReadable);

                        int result = keyABValue;
                        if (acValue == 0 && keyABValue != 0) {
                            // Write key found, but ac-bits are not writable.
                            result += 3;
                        } else if (acValue == 2 && keyABValue == 0) {
                            // Access Bits are writable with key B,
                            // but keys are not writable.
                            result = 6;
                        }
                        blockWithWriteInfo.put(block, result);
                    } else {
                        // Data block.
                        blockWithWriteInfo.put(
                                block, Util.getOperationInfoForBlock(
                                        acMatrix[0][block],
                                        acMatrix[1][block],
                                        acMatrix[2][block],
                                        Util.Operations.Write,
                                        false, isKeyBReadable));
                    }

                }
                if (blockWithWriteInfo.size() > 0) {
                    ret.put(sector, blockWithWriteInfo);
                }
            }
        }
        return ret;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.writer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
