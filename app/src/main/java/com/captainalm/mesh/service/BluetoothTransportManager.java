package com.captainalm.mesh.service;

/**
 * Provides a bluetooth transport manager.
 *
 * @author Alfred Manville
 */
public class BluetoothTransportManager extends TransportManager {
    public BluetoothTransportManager(Thread monitor, Thread scanner) {
        super(monitor, scanner);
    }
}
