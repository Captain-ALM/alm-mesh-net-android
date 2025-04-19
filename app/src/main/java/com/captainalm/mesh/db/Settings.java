package com.captainalm.mesh.db;

import androidx.annotation.NonNull;
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
    @NonNull
    public String privateKeyKEM = "";

    @ColumnInfo(name = "private_key_dsa")
    @NonNull
    public String privateKeyDSA = "";

    @ColumnInfo(name = "ethereal_private_Key_kem")
    public String etherealPrivateKeyKEM;

    @ColumnInfo(name = "ethereal_private_key_dsa")
    public String etherealPrivateKeyDSA;

    @ColumnInfo(name = "excluded_addresses")
    @NonNull
    public String excludedAddresses = "";

    @ColumnInfo(name = "rec_sig_key")
    @NonNull
    public String recommendedSigPublicKey = "";
    @ColumnInfo(name = "rec_sig")
    @NonNull
    public String recommendedSig = "";

    @ColumnInfo(name = "gateway_on")
    public int gatewayOn;

    @ColumnInfo(name = "transports")
    public int transports;

    @Ignore
    public String[] getExcludedAddresses() {
        return excludedAddresses.split(";");
    }

    @Ignore
    public MLKEMPrivateKey getPrivateKeyKEM() {
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

    @Ignore
    public byte[] getRecommendKey() {
        return Provider.base64Decode(recommendedSigPublicKey);
    }
    @Ignore
    public byte[] getRecommendedSig() {
        return Provider.base64Decode(recommendedSig);
    }

    @Ignore
    public boolean gatewayMode() {
        return gatewayOn == 1;
    }

    @Ignore
    public boolean e2eEnabled() {
        return encryptionMode > 0;
    }

    @Ignore
    public boolean e2eRequired() {
        return encryptionMode > 1;
    }

    @Ignore
    public boolean e2eIgnoreNonEncryptedPackets() {
        return encryptionMode > 2;
    }

    @Ignore
    public void setWiFiDirect(boolean enabled) {
        if (enabled)
            transports |= 1 << 1;
        else
            transports &= ~(1 << 1);

    }

    @Ignore
    public void setBluetooth(boolean enabled) {
        if (enabled)
            transports |= 1 << 2;
        else
            transports &= ~(1 << 2);
    }

    @Ignore
    public boolean enabledWiFiDirect() {
        return ((transports >> 1) & 1) == 1;
    }

    @Ignore
    public boolean enabledBluetooth() {
        return ((transports >> 2) & 1) == 1;
    }
}
