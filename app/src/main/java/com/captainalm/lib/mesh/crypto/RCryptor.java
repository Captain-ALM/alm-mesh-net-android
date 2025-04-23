package com.captainalm.lib.mesh.crypto;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

/**
 * Provides a {@link Cryptor} with the
 * byte array functions placing the IV at the end,
 * and keeping the IV in the data during decryption.
 *
 * @author Alfred Manville
 */
public class RCryptor extends Cryptor {
    @Override
    public byte[] encrypt(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null)
            return new byte[0];
        byte[] buff = new byte[bytes.length];
        byte[] lIV;
        if (this.getIV() == null && bytes.length < 16) {
            lIV = Provider.generateIV(12);
            System.arraycopy(lIV, 0, buff, buff.length - 16, 12);
        } else
            lIV = this.getIV();
        getCipher(Cipher.ENCRYPT_MODE, lIV).doFinal(bytes, 0,
                (this.getIV() == null) ? bytes.length - 16 : bytes.length, buff, 0);
        return buff;
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null)
            return new byte[0];
        byte[] lIV = getIV();
        if (lIV == null) {
            if (bytes.length < 16)
                return new byte[0];
            lIV = new byte[16];
            System.arraycopy(bytes, bytes.length - 16, lIV, 0, 16);
        }
        byte[] buff = new byte[bytes.length];
        getCipher(Cipher.DECRYPT_MODE, lIV).doFinal(bytes, 0,
                (getIV() == null) ? buff.length - 16 : buff.length, buff, 0);
        return buff;
    }
}
