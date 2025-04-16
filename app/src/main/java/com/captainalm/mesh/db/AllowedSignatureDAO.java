package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AllowedSignatureDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addAllowedSignature(AllowedSignature publicKey);
    @Delete
    void removeAllowedSignature(AllowedSignature publicKey);

    @Query("select * from allowed_signature_keys")
    List<AllowedSignature> getAllAllowedSignatures();

    @Query("select * from allowed_signature_keys where id == :ID")
    AllowedSignature getAllowedSignature(String ID);
}
