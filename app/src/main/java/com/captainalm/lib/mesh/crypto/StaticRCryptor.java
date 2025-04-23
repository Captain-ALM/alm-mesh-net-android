package com.captainalm.lib.mesh.crypto;

/**
 * Provides a same-on-all-threads rcryptor class.
 *
 * @author Alfred Manville
 */
public class StaticRCryptor extends RCryptor {
    private byte[] IV = null;
    private byte[] key = null;

    @Override
    public byte[] getIV() {
        return IV;
    }

    @Override
    public ICryptor setIV(byte[] IV) {
        this.IV = IV;
        return this;
    }

    @Override
    public ICryptor setKey(byte[] bytes) {
        this.key = bytes;
        return this;
    }

    @Override
    public byte[] getKey() {
        return key;
    }
}
