package com.captainalm.mesh.service;

import android.content.Context;
import android.content.Intent;

/**
 * Provides a bluetooth transport manager.
 *
 * @author Alfred Manville
 */
public class BluetoothTransportManager extends TransportManager {
    public BluetoothTransportManager(Thread monitor, Thread scanner) {
        super(monitor, scanner);
    }

    @Override
    public void discover() {

    }

    @Override
    public void receiveBroadcast(Context context, Intent intent) {

    }
}
