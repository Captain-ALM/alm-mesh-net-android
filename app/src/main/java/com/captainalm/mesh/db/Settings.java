package com.captainalm.mesh.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.captainalm.lib.mesh.crypto.Provider;

import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey;
import org.bouncycastle.jcajce.interfaces.MLKEMPrivateKey;

@Entity(tableName = "settings")
public class Settings {
    @ColumnInfo(name = "id")
    @PrimaryKey()
    public int ID = 1;

    @ColumnInfo(name = "charge_size")
    public int packetChargeSize;
    @ColumnInfo(name = "max_ttl")
    public int maxTTL;
    @ColumnInfo(name = "encryption")
    public int encryptionMode;
    @ColumnInfo(name = "private_Key_kem")
    public String privateKeyKEM;

    @ColumnInfo(name = "private_key_dsa")
    public String privateKeyDSA;

    @ColumnInfo(name = "ethereal_private_Key_kem")
    public String etherealPrivateKeyKEM;

    @ColumnInfo(name = "ethereal_private_key_dsa")
    public String etherealPrivateKeyDSA;

    @ColumnInfo(name = "excluded_addresses")
    public String excludedAddresses;

    @Ignore
    public String[] getExcludedAddresses() {
        if (excludedAddresses == null)
            return new String[0];
        return excludedAddresses.split(";");
    }

    @Ignore
    public MLKEMPrivateKey getPrivateKeyKEM() {
        if (privateKeyKEM == null)
            return null;
        return (MLKEMPrivateKey) Provider.getMLKemPrivateKey(Provider.base64Decode(privateKeyKEM));
    }

    @Ignore
    public void setPrivateKeyKEM(MLKEMPrivateKey key) {
        if (key == null)
            privateKeyKEM = "";
        else
            privateKeyKEM = Provider.base64Encode(Provider.getMLKemPrivateKeyBytes(key));
    }

    @Ignore
    public MLDSAPrivateKey getPrivateKeyDSA() {
        if (privateKeyKEM == null)
            return null;
        return (MLDSAPrivateKey) Provider.getMLDsaPrivateKey(Provider.base64Decode(privateKeyDSA));
    }

    @Ignore
    public void setPrivateKeyDSA(MLDSAPrivateKey key) {
        if (key == null)
            privateKeyDSA = "";
        else
            privateKeyDSA = Provider.base64Encode(Provider.getMLDsaPrivateKeyBytes(key));
    }
    @Ignore
    public MLKEMPrivateKey getEtherealPrivateKeyKEM() {
        if (etherealPrivateKeyKEM == null)
            return null;
        return (MLKEMPrivateKey) Provider.getMLKemPrivateKey(Provider.base64Decode(etherealPrivateKeyKEM));
    }

    @Ignore
    public void setEtherealPrivateKeyKEM(MLKEMPrivateKey key) {
        if (key == null)
            etherealPrivateKeyKEM = "";
        else
            etherealPrivateKeyKEM = Provider.base64Encode(Provider.getMLKemPrivateKeyBytes(key));
    }

    @Ignore
    public MLDSAPrivateKey getEtherealPrivateKeyDSA() {
        if (etherealPrivateKeyKEM == null)
            return null;
        return (MLDSAPrivateKey) Provider.getMLDsaPrivateKey(Provider.base64Decode(etherealPrivateKeyDSA));
    }

    @Ignore
    public void setEtherealPrivateKeyDSA(MLDSAPrivateKey key) {
        if (key == null)
            etherealPrivateKeyDSA = "";
        else
            etherealPrivateKeyDSA = Provider.base64Encode(Provider.getMLDsaPrivateKeyBytes(key));
    }
}
