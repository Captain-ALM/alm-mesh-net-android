package com.captainalm.lib.mesh.crypto;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jcajce.provider.asymmetric.mldsa.BCMLDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.mlkem.BCMLKEMPrivateKey;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Provides an ML-DSA Signer.
 *
 * @author Alfred Manville
 */
public class MLDsa implements ISigner {
    private final ThreadLocal<PublicKey> publicKey = new ThreadLocal<>();
    private final ThreadLocal<PrivateKey> privateKey = new ThreadLocal<>();

    @Override
    public byte[] getPrivateKey() {
        if (privateKey.get() == null)
            return null;
        return Provider.getMLDsaPrivateKeyBytes(privateKey.get());
    }

    @Override
    public ISigner setPrivateKey(byte[] bytes) {
        if (bytes == null)
            return this;
        privateKey.set(Provider.getMLDsaPrivateKey(bytes));
        publicKey.set(((BCMLDSAPrivateKey) privateKey.get()).getPublicKey());
        return this;
    }

    @Override
    public byte[] sign(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null || privateKey.get() == null)
            return new byte[2420];
        MLDSASigner sr = new MLDSASigner();
        try {
            sr.init(true, PrivateKeyFactory.createKey(privateKey.get().getEncoded()));
            sr.update(bytes,0, bytes.length);
            return sr.generateSignature();
        } catch (IOException | CryptoException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Override
    public byte[] sign(InputStream inputStream) throws GeneralSecurityException, IOException {
        if (inputStream == null || privateKey.get() == null)
            return new byte[2420];
        MLDSASigner sr = new MLDSASigner();
        byte[] buff = new byte[4096];
        int r = 0;
        try {
            sr.init(true, PrivateKeyFactory.createKey(privateKey.get().getEncoded()));
            while ((r = inputStream.read(buff)) != -1)
                sr.update(buff, 0, r);
            return sr.generateSignature();
        } catch (IOException | CryptoException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Override
    public byte[] getPublicKey() {
        if (publicKey.get() == null)
            return null;
        return Provider.getMLDsaPublicKeyBytes(publicKey.get());
    }

    @Override
    public ISigner setPublicKey(byte[] bytes) {
        if (bytes == null)
            return this;
        publicKey.set(Provider.getMLDsaPublicKey(bytes));
        privateKey.set(null);
        return this;
    }

    @Override
    public boolean verify(byte[] bytes, byte[] signature) throws GeneralSecurityException {
        if (bytes == null || signature == null || publicKey.get() == null)
            return false;
        MLDSASigner sr = new MLDSASigner();
        try {
            sr.init(false, PublicKeyFactory.createKey(publicKey.get().getEncoded()));
            sr.update(bytes,0, bytes.length);
            return sr.verifySignature(signature);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Override
    public boolean verify(InputStream inputStream, byte[] signature) throws GeneralSecurityException, IOException {
        if (inputStream == null || signature == null || publicKey.get() == null)
            return false;
        MLDSASigner sr = new MLDSASigner();
        byte[] buff = new byte[4096];
        int r = 0;
        try {
            sr.init(false, PublicKeyFactory.createKey(publicKey.get().getEncoded()));
            while ((r = inputStream.read(buff)) != -1)
                sr.update(buff, 0, r);
            return sr.verifySignature(signature);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
