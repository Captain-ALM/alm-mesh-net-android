package com.captainalm.lib.mesh.crypto;

import com.captainalm.lib.mesh.utils.InputStreamTransfer;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides a symmetric cryptography class.
 *
 * @author Alfred Manville
 */
public class Cryptor implements ICryptor {
    private final ThreadLocal<byte[]> IV = new ThreadLocal<>();
    private final ThreadLocal<byte[]> symmetricKey = new ThreadLocal<>();

    @Override
    public ICryptor setKey(byte[] bytes) {
        if (bytes != null && bytes.length == 32)
            symmetricKey.set(bytes);
        return this;
    }

    @Override
    public byte[] getKey() {
        return symmetricKey.get();
    }

    public ICryptor setIV(byte[] IV) {
        if (IV.length < 12)
            return this;
        this.IV.set(IV);
        return this;
    }

    public byte[] getIV() {
        return this.IV.get();
    }

    private Cipher getCipher(int mode ,byte[] lIV) throws GeneralSecurityException {
        if (this.symmetricKey.get() == null)
            throw new GeneralSecurityException("No Key");
        try {
            Cipher cc = Cipher.getInstance("ChaCha20", BouncyCastleProvider.PROVIDER_NAME);
            IvParameterSpec ivp = new IvParameterSpec(lIV, 0, 12);
            cc.init(mode, new SecretKeySpec(this.symmetricKey.get() ,"ChaCha20"), ivp);
            return cc;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw e;
        }
    }

    @Override
    public int encryptToStream(byte[] bytes, OutputStream outputStream) throws IOException, GeneralSecurityException {
        if (bytes == null || outputStream == null)
            return 0;
        int w = 0;
        byte[] lIV;
        if (this.IV.get() == null) {
            lIV = Provider.generateIV(12);
            outputStream.write(lIV);
            w += 12;
            outputStream.write(new byte[4]);
            w += 4;
            outputStream.flush();
        } else
            lIV = this.IV.get();
        OutputStream cos = new CipherOutputStream(outputStream, getCipher(Cipher.ENCRYPT_MODE, lIV));
        cos.write(bytes);
        cos.flush();
        w += bytes.length;
        return w;
    }

    @Override
    public byte[] encrypt(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null)
            return new byte[0];
        byte[] buff;
        byte[] lIV;
        int startIndex = 0;
        if (this.IV.get() == null) {
            buff = new byte[bytes.length + 16];
            lIV = Provider.generateIV(12);
            System.arraycopy(lIV, 0, buff, 0, 12);
            startIndex = 16;
        } else {
            buff = new byte[bytes.length];
            lIV = this.IV.get();
        }
        getCipher(Cipher.ENCRYPT_MODE, lIV).doFinal(bytes, 0, bytes.length, buff, startIndex);
        return buff;
    }

    @Override
    public void encryptInPlace(byte[] bytes) throws GeneralSecurityException {
        System.arraycopy(encrypt(bytes), 0, bytes, 0, bytes.length);
    }

    @Override
    public void encryptStream(InputStream inputStream, OutputStream outputStream) throws IOException, GeneralSecurityException {
        if (inputStream == null || outputStream == null)
            return;
        byte[] lIV;
        if (this.IV.get() == null) {
            lIV = Provider.generateIV(12);
            outputStream.write(lIV);
            outputStream.write(new byte[4]);
            outputStream.flush();
        } else
            lIV = this.IV.get();
        OutputStream cos = new CipherOutputStream(outputStream, getCipher(Cipher.ENCRYPT_MODE, lIV));
        InputStreamTransfer.streamTransfer(inputStream, cos);
    }

    @Override
    public byte[] decryptFromStream(InputStream inputStream, int i) throws IOException, GeneralSecurityException {
        if (inputStream == null)
            return new byte[0];
        byte[] lIV = IV.get();
        if (lIV == null) {
            if (i < 16) {
                inputStream.skip(i);
                return new byte[0];
            }
            lIV = new byte[16];
            int n = inputStream.read(lIV);
            if (n != 16)
                return new byte[0];
        }
        InputStream cis = new CipherInputStream(inputStream, getCipher(Cipher.DECRYPT_MODE, lIV));
        byte[] buff = new byte[(IV.get() == null) ? i - 16 : i];
        int idx = 0;
        while (idx < i)
            idx += cis.read(buff, idx, buff.length - idx);
        return buff;
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null)
            return new byte[0];
        byte[] lIV = IV.get();
        if (lIV == null) {
            if (bytes.length < 16)
                return new byte[0];
            lIV = new byte[16];
            System.arraycopy(bytes, 0, lIV, 0, 16);
        }
        byte[] buff = new byte[(IV.get() == null) ? bytes.length - 16 : bytes.length];
        getCipher(Cipher.DECRYPT_MODE, lIV).doFinal(bytes, (IV.get() == null) ? 16 : 0, buff.length, buff, 0);
        return buff;
    }

    @Override
    public void decryptInPlace(byte[] bytes) throws GeneralSecurityException {
        System.arraycopy(decrypt(bytes), 0, bytes, 0, bytes.length);
    }

    @Override
    public void decryptStream(InputStream inputStream, OutputStream outputStream) throws IOException, GeneralSecurityException {
        if (inputStream == null)
            return;
        byte[] lIV = IV.get();
        if (lIV == null) {
            lIV = new byte[16];
            int n = inputStream.read(lIV);
            if (n != 16)
                return;
        }
        CipherInputStream cis = new CipherInputStream(inputStream, getCipher(Cipher.DECRYPT_MODE, lIV));
        InputStreamTransfer.streamTransfer(cis, outputStream);
    }
}
