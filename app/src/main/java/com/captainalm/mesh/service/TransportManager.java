package com.captainalm.mesh.service;

import com.captainalm.lib.mesh.routing.Router;

import java.util.LinkedList;
import java.util.List;

/**
 * Provides a base class for transport managers.
 *
 * @author Alfred Manville
 */
public abstract class TransportManager {
    private Router router;
    private final Object slockRouter = new Object();
    private final List<String> blockedDevices = new LinkedList<>();
    private final Object slockBlockedDevices = new Object();
    private final List<String> connectedDevices = new LinkedList<>();
    private final Object slockConnectedDevices = new Object();
    boolean active = true;
    protected final Thread monitorThread;
    protected final Thread scannerThread;

    public TransportManager(Thread monitor, Thread scanner) {
        monitorThread = monitor;
        scannerThread = scanner;
        monitorThread.start();
        scannerThread.start();
    }

    protected boolean deviceExists(String id) {
        if (!active)
            return true;
        synchronized (slockConnectedDevices) {
            return connectedDevices.contains(id);
        }
    }

    protected boolean addDevice(String id) {
        if (!active)
            return false;
        synchronized (slockConnectedDevices) {
            if (connectedDevices.contains(id))
                return false;
            connectedDevices.add(id);
            return true;
        }
    }

    protected void removeDevice(String id) {
        if (!active)
            return;
        synchronized (slockConnectedDevices) {
            connectedDevices.remove(id);
        }
    }

    protected boolean deviceBlocked(String id) {
        if (!active)
            return true;
        synchronized (slockBlockedDevices) {
            return blockedDevices.contains(id);
        }
    }

    protected void blockDevice(String id) {
        if (!active)
            return;
        synchronized (slockBlockedDevices) {
            blockedDevices.add(id);
        }
    }

    public void purgeBlockCache() {
        synchronized (slockBlockedDevices) {
            blockedDevices.clear();
        }
    }

    protected Router getRouter() {
        if (!active)
            return null;
        synchronized (slockRouter) {
            return router;
        }
    }

    public void clearRouter() {
        synchronized (slockRouter) {
            router = null;
        }
    }

    public void setRouter(Router router) {
        if (!active)
            return;
        synchronized (slockRouter) {
            this.router = router;
        }
    }

    public void terminate() {
        active = false;
        if (scannerThread.isAlive())
            scannerThread.interrupt();
        if (monitorThread.isAlive())
            monitorThread.interrupt();
        purgeBlockCache();
        clearRouter();
        synchronized (slockConnectedDevices) {
            connectedDevices.clear();
        }
    }
}
