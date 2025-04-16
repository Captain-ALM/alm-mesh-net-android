package com.captainalm.lib.mesh.crypto;

import android.content.Context;

import com.captainalm.mesh.TheApplication;

import org.bouncycastle.jcajce.provider.asymmetric.mldsa.BCMLDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.mldsa.BCMLDSAPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.mlkem.BCMLKEMPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.mlkem.BCMLKEMPublicKey;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * Provides a cryptography provider.
 *
 * @author Alfred Manville
 */
public class Provider implements IProvider {
    private static final KeyPairGenerator mlKEMKeyPairGenerator;
    private static final KeyPairGenerator mlDSAKeyPairGenerator;
    private static final Random random = new SecureRandom();

    private final TheApplication context;

    public Provider(Context context) {
        if (context instanceof TheApplication ta)
            this.context = ta;
        else if (context.getApplicationContext() instanceof TheApplication ta)
            this.context = ta;
        else
            this.context = null;
        hasher = new Hasher(this.context);

    }

    static { //Init all key pair generators.
        KeyPairGenerator keyPairGeneratorTemp;
        try {
                keyPairGeneratorTemp = KeyPairGenerator.getInstance("ML-KEM", BouncyCastleProvider.PROVIDER_NAME);
                keyPairGeneratorTemp.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
                keyPairGeneratorTemp = null;
        }
        mlKEMKeyPairGenerator = keyPairGeneratorTemp;
        try {
            keyPairGeneratorTemp = KeyPairGenerator.getInstance("ML-DSA", BouncyCastleProvider.PROVIDER_NAME);
            keyPairGeneratorTemp.initialize(MLDSAParameterSpec.ml_dsa_44, new SecureRandom());
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            keyPairGeneratorTemp = null;
        }
        mlDSAKeyPairGenerator = keyPairGeneratorTemp;
    }
    public static KeyPair generateMLKemKeyPair() {
        if (mlKEMKeyPairGenerator == null)
            return null;
        return mlKEMKeyPairGenerator.generateKeyPair();
    }

    public static KeyPair generateMLDsaKeyPair() {
        if (mlDSAKeyPairGenerator == null)
            return null;
        return mlDSAKeyPairGenerator.generateKeyPair();
    }

    public static byte[] getMLKemPublicKeyBytes(PublicKey pubKey) {
        if (pubKey instanceof BCMLKEMPublicKey pk)
            return pk.getPublicData();
        return new byte[1184];
    }

    public static PublicKey getMLKemPublicKey(byte[] bytes) {
        if (bytes == null)
            return null;
        return new BCMLKEMPublicKey(new MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, bytes));
    }

    public static byte[] getMLKemPrivateKeyBytes(PrivateKey privKey) {
        if (privKey instanceof BCMLKEMPrivateKey pk)
            return pk.getPrivateData();
        return new byte[2400];
    }

    public static PrivateKey getMLKemPrivateKey(byte[] bytes) {
        if (bytes == null)
            return null;
        return new BCMLKEMPrivateKey(new MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, bytes));
    }

    public static byte[] getMLDsaPublicKeyBytes(PublicKey pubKey) {
        if (pubKey instanceof BCMLDSAPublicKey pk)
            return pk.getPublicData();
        return new byte[1312];
    }

    public static PublicKey getMLDsaPublicKey(byte[] bytes) {
        if (bytes == null)
            return null;
        return new BCMLDSAPublicKey(new MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_44, bytes));
    }

    public static byte[] getMLDsaPrivateKeyBytes(PrivateKey privKey) {
        if (privKey instanceof BCMLDSAPrivateKey pk)
            return pk.getPrivateData();
        return new byte[2560];
    }

    public static PrivateKey getMLDsaPrivateKey(byte[] bytes) {
        if (bytes == null)
            return null;
        return new BCMLDSAPrivateKey(new MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_44, bytes));
    }

    public static byte[] base64Decode(String b64String) {
        try {
            if (b64String == null || b64String.isEmpty())
                return new byte[0];
            return Base64.getDecoder().decode(b64String);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String base64Encode(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return "";
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] generateIV(int size) {
        if (size < 1)
            return new byte[0];
        byte[] IV = new byte[size];
        while (IV[0] == 0) // IVs cannot start with a zero byte in the library
            random.nextBytes(IV);
        return IV;
    }

    private final Cryptor crypt = new Cryptor();

    @Override
    public ICryptor GetCryptorInstance() {
        return crypt;
    }

    @Override
    public ICryptor GetFixedIVCryptorInstance() {
        return crypt;
    }

    private final IHasher hasher;
    @Override
    public IHasher GetHasherInstance() {
        return hasher;
    }

    private final MLDsa signer = new MLDsa();
    private final MLKem unwrapper = new MLKem();

    @Override
    public ISigner GetSignerInstance() {
        return signer;
    }

    @Override
    public IUnwrapper GetUnwrapperInstance() {
        return unwrapper;
    }

    @Override
    public IVerifier GetVerifierInstance() {
        return signer;
    }

    @Override
    public IWrapper GetWrapperInstance() {
        return unwrapper;
    }
}
