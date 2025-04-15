package com.captainalm.lib.mesh.crypto;

import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jcajce.provider.asymmetric.mlkem.BCMLKEMPrivateKey;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

/**
 * Provides an ML-KEM unwrapper.
 *
 * @author Alfred Manville
 */
public class MLKem implements IUnwrapper {
    private final ThreadLocal<PublicKey> publicKey = new ThreadLocal<>();
    private final ThreadLocal<PrivateKey> privateKey = new ThreadLocal<>();

    @Override
    public byte[] getPrivateKey() {
        if (privateKey.get() == null)
            return null;
        return Provider.getMLKemPrivateKeyBytes(privateKey.get());
    }

    @Override
    public IUnwrapper setPrivateKey(byte[] bytes) {
        if (bytes == null)
            return this;
        privateKey.set(Provider.getMLKemPrivateKey(bytes));
        publicKey.set(((BCMLKEMPrivateKey) privateKey.get()).getPublicKey());
        return this;
    }

    @Override
    public byte[] unwrap(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null || privateKey.get() == null)
            return new byte[32];
        try {
            return new MLKEMExtractor((MLKEMPrivateKeyParameters) PrivateKeyFactory.createKey(privateKey.get().getEncoded())).extractSecret(bytes);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Override
    public byte[] getPublicKey() {
        if (publicKey.get() == null)
            return null;
        return Provider.getMLKemPublicKeyBytes(publicKey.get());
    }

    @Override
    public IWrapper setPublicKey(byte[] bytes) {
        if (bytes == null)
            return this;
        publicKey.set(Provider.getMLKemPublicKey(bytes));
        privateKey.set(null);
        return this;
    }

    @Override
    public byte[] wrap(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null || publicKey.get() == null)
            return new byte[1088];
        try {
            return new MLKEMGenerator(new SecureRandom()).internalGenerateEncapsulated(PublicKeyFactory.createKey(publicKey.get().getEncoded()), bytes).getEncapsulation();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
