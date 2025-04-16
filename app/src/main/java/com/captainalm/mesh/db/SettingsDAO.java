package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SettingsDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addSettings(Settings settings);
    @Update
    void updateSettings(Settings settings);

    @Query("select * from settings")
    List<Settings> getSettings();
}
