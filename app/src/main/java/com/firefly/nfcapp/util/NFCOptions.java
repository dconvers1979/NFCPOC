package com.firefly.nfcapp.util;

import android.nfc.tech.MifareClassic;
import android.util.Log;

import java.io.IOException;

/**
 * Created by alex on 31/05/17.
 */
public class NFCOptions {

    /**
     * Authenticate to given sector of the tag.
     * @param sectorIndex The sector to authenticate to.
     * @param key Key for the authentication.
     * @param useAsKeyB If true, key will be treated as key B
     * for authentication.
     * @return True if authentication was successful. False otherwise.
     */
    public static boolean authenticate(MifareClassic mMFC, int sectorIndex, byte[] key,
                                 boolean useAsKeyB) {
        try {
            if (!useAsKeyB) {
                // Key A.
                return mMFC.authenticateSectorWithKeyA(sectorIndex, key);
            } else {
                // Key B.
                return mMFC.authenticateSectorWithKeyB(sectorIndex, key);
            }
        } catch (IOException e) {
            Log.d("NFCOptions", "Error while authenticate with tag.");
        }
        return false;
    }

}
