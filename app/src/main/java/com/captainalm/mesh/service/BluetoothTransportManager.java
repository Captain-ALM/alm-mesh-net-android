package com.captainalm.mesh.service;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.handshake.HandshakeProcessor;
import com.captainalm.lib.mesh.packets.PacketBytesInputStream;
import com.captainalm.lib.mesh.routing.Router;
import com.captainalm.lib.mesh.transport.INetTransport;
import com.captainalm.mesh.MeshVpnService;
import com.captainalm.mesh.R;
import com.captainalm.mesh.db.Settings;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/**
 * Provides a bluetooth transport manager.
 *
 * @author Alfred Manville
 */
public class BluetoothTransportManager extends TransportManager {
    private final SecureRandom random = new SecureRandom();
    private final BlockingQueue<UUID> uuidQueue = new LinkedBlockingQueue<>();
    private final List<Transport> waitingQueue = new LinkedList<>();
    private final Object slockWaiting = new Object();
    private final int SCAN_TIME = 5000;

    @SuppressLint("MissingPermission")
    public BluetoothTransportManager(MeshVpnService service) {
        super(service);
        active = true;
        monitorThread = new Thread(() -> {
            while (active) {
                try {
                    UUID nUUID = uuidQueue.take();
                    new Transport(nUUID);
                } catch (InterruptedException ignored) {
                }
            }
        });
        scannerThread = new Thread(() -> {
            Object lSlock = new Object();
            while (active) {
                try {
                    Thread.sleep(SCAN_TIME);
                    BluetoothAdapter adapter = service.app.getBluetoothAdapter();
                    android.util.Log.w("DEBUGGING-MESHNET-B-SCAN", "started");
                    if (adapter != null && !adapter.isDiscovering())
                        adapter.startDiscovery();
                    Thread.sleep(SCAN_TIME);
                    if (adapter != null && adapter.isDiscovering())
                        adapter.cancelDiscovery();
                    android.util.Log.w("DEBUGGING-MESHNET-B-SCAN", "ended");
                } catch (InterruptedException ignored) {
                }
                if(!active)
                    return;
                try {
                    synchronized (lSlock) {
                        lSlock.wait();
                    }
                } catch (InterruptedException e) {
                    /*if (active) {
                        BluetoothAdapter adapter = service.app.getBluetoothAdapter();
                        Set<BluetoothDevice> paired = null;
                        if (adapter != null)
                            paired = adapter.getBondedDevices();
                        if (paired != null){
                            try {
                                for (BluetoothDevice dev : paired) {
                                    if (!deviceBlocked(getAddress(dev))) {
                                        Transport nw = getNextWaiting();
                                        if (nw != null)
                                            nw.connect(dev);
                                    }
                                }
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } */
                }
            }
        });
        scannerThread.start();
        List<UUID> uuids = new ArrayList<>();
        uuids.add(UUID.fromString("98989800-0000-0000-0000-000000000000"));
        uuids.add(UUID.fromString("98989801-0000-0000-0000-000000000000"));
        uuids.add(UUID.fromString("98989802-0000-0000-0000-000000000000"));
        uuids.add(UUID.fromString("98989803-0000-0000-0000-000000000000"));
        uuids.add(UUID.fromString("98989804-0000-0000-0000-000000000000"));
        uuids.add(UUID.fromString("98989805-0000-0000-0000-000000000000"));
        Collections.shuffle(uuids);
        uuidQueue.addAll(uuids);
        monitorThread.start();
    }

    private void addWaiting(Transport transport) {
        synchronized (slockWaiting) {
            if (!waitingQueue.contains(transport))
                waitingQueue.add(transport);
            slockWaiting.notifyAll();
        }
    }

    private void removeWaiting(Transport transport) {
        synchronized (slockWaiting) {
            waitingQueue.remove(transport);
        }
    }

    private Transport getNextWaiting() throws InterruptedException {
        synchronized (slockWaiting) {
            while (waitingQueue.isEmpty())
                slockWaiting.wait();
            return waitingQueue.remove(waitingQueue.size() - 1);
        }
    }

    private Transport getNextWaitingTimeout(int timeout) throws InterruptedException {
        synchronized (slockWaiting) {
            slockWaiting.wait(timeout);
            if (waitingQueue.isEmpty())
                return null;
            return waitingQueue.remove(waitingQueue.size() - 1);
        }
    }

    @Override
    public void terminate() {
        boolean wasActive = active;
        super.terminate();
        if (wasActive) {
            waitingQueue.clear();
            uuidQueue.clear();
        }
    }

    @Override
    public void discover() {
        if (scannerThread.isAlive())
            scannerThread.interrupt();
    }

    @Override
    public void receiveBroadcast(Context context, Intent intent) {
        if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String addr = getAddress(device);
            if (addr != null)
                android.util.Log.w("B-RB-FOUND", addr);
            new Thread(() -> {
                if (!deviceBlocked(getAddress(device))) {
                    try {
                        Transport nw = getNextWaitingTimeout(SCAN_TIME);
                        if (nw != null)
                            nw.connect(device);
                    } catch (InterruptedException ignored) {
                    }
                }
            }).start();
        }
    }

    private class Transport implements INetTransport {
        private String deviceName;
        private PacketBytesInputStream in;
        private OutputStream out;
        private BluetoothServerSocket serverSocket;
        private BluetoothSocket socket;
        private final Object slockServerSocket = new Object();
        private final Object slockConnect = new Object();
        private byte[] key;
        private boolean active = false;
        private final UUID uuid;
        private boolean shouldListen = true;

        public Transport(UUID uuid) {
            this.uuid = uuid;
            addWaiting(this);
            listen();
        }

        private void listen() {
            if (!BluetoothTransportManager.this.active)
                return;
            new Thread(() -> {
                BluetoothServerSocket lSS = null;
                try {
                    BluetoothAdapter adapter = service.app.getBluetoothAdapter();
                    synchronized (slockServerSocket) {
                        if (adapter != null)
                            serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(service.getString(R.string.app_name), uuid);
                    }
                    while (BluetoothTransportManager.this.active && !active && serverSocket != null) {
                        synchronized (slockServerSocket) {
                            while (!shouldListen && BluetoothTransportManager.this.active && serverSocket != null)
                                slockServerSocket.wait();
                            if (adapter != null)
                                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(service.getString(R.string.app_name), uuid);
                            lSS = serverSocket;
                        }
                        if (lSS != null) {
                            try {
                                BluetoothSocket accepted = lSS.accept();
                                removeWaiting(this);
                                String addr = getAddress(accepted.getRemoteDevice());
                                if (addr != null)
                                    android.util.Log.w("B-L", addr + " " + accepted.isConnected());
                                if (connect(accepted))
                                    return;
                            } catch (IOException e) {
                                addWaiting(this);
                                service.app.showException(e);
                            }
                        } else {
                            close();
                            uuidQueue.add(uuid);
                            return;
                        }
                    }
                } catch (InterruptedException | SecurityException | IOException e) {
                    service.app.showException(e);
                }
                close();
                uuidQueue.add(uuid);
            }).start();
        }

        public void connect(BluetoothDevice device) throws InterruptedException {
            if (active)
                return;
            if (device == null) {
                addWaiting(this);
                return;
            }
            synchronized (slockServerSocket) {
                shouldListen = false;
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            try {
                if (connect(device.createInsecureRfcommSocketToServiceRecord(uuid)))
                    return;
            } catch (IOException | SecurityException e) {
                android.util.Log.e("B-CD", "", e);
            }
            synchronized (slockServerSocket) {
                shouldListen = true;
                slockServerSocket.notifyAll();
            }
        }

        private boolean connect(BluetoothSocket socket) throws InterruptedException {
            synchronized (slockConnect) {
                deviceName = getAddress(socket.getRemoteDevice());
                if (!deviceExists(deviceName) && !deviceBlocked(deviceName)) {
                    android.util.Log.w("B-C", "Attempting connection: " + deviceName);
                    addDevice(deviceName);
                    if (!socket.isConnected()) {
                        try {
                            BluetoothAdapter adapter = service.app.getBluetoothAdapter();
                            if (adapter != null)
                                adapter.cancelDiscovery();
                            socket.connect();
                        } catch (IOException | SecurityException e) {
                            android.util.Log.e("B-C", "", e);
                            removeDevice(deviceName);
                            addWaiting(this);
                            return false;
                        }
                    }
                    android.util.Log.w("B-C", "Starting connection: " + deviceName);
                    Thread handshakeErrorThread = null;
                    try {
                        Settings settings = service.loadSettings();
                        if (settings == null) {
                            removeDevice(deviceName);
                            addWaiting(this);
                            return false;
                        }
                        byte[][] keyData = service.app.cryptographyProvider.GetWrapperInstance().setPublicKey(
                                        Provider.getMLKemPublicKeyBytes(settings.getPrivateKeyKEM().getPublicKey()))
                                .wrap(random);
                        key = keyData[0];
                        in = new PacketBytesInputStream(socket.getInputStream());
                        in.upgradeCipher = service.app.cryptographyProvider.GetCryptorInstance();
                        out = socket.getOutputStream();
                        active = true;
                        HandshakeProcessor handshakeProcessor = new HandshakeProcessor(service.app.thisNode, this,
                                service.app.cryptographyProvider, service.app.authorizer,
                                Provider.getMLKemPrivateKeyBytes(settings.getPrivateKeyKEM()),
                                Provider.getMLDsaPrivateKeyBytes(settings.getPrivateKeyDSA()), keyData[1]);
                        handshakeErrorThread = new Thread(() -> {
                            Exception err;
                            try {
                                while ((err = handshakeProcessor.getFirstException()) != null)
                                    service.app.showException(err);
                            } catch (InterruptedException ignored) {
                            }
                        });
                        handshakeErrorThread.start();
                        byte[] outEnc = handshakeProcessor.handshake(SCAN_TIME);
                        android.util.Log.w("B-C", "Handshaked: " + deviceName +
                                ((outEnc == null) ? "-" : "+") + (handshakeProcessor.localAuthorizeSuccess() ? "+" : "-"));
                        if (outEnc == null) {
                            if (!handshakeProcessor.localAuthorizeSuccess())
                                blockDevice(deviceName);
                            socket.close();
                            close();
                            return false;
                        }
                        byte[] iv = Provider.generateIV(16);
                        out.write(iv);
                        out = new CipherOutputStream(out, service.app.cryptographyProvider.GetCryptorInstance()
                                .setKey(outEnc).getCipher(Cipher.ENCRYPT_MODE, iv));
                        Router r = getRouter();
                        if (r == null) {
                            try {
                                socket.close();
                            } catch (IOException ignored) {
                            }
                            close();
                            return false;
                        }
                        r.addTransport(handshakeProcessor.getRemote(), this, handshakeProcessor.getOtherPackets());
                        this.socket = socket;
                        synchronized (slockServerSocket) {
                            slockServerSocket.notifyAll();
                        }
                        return true;
                    } catch (GeneralSecurityException | IOException e) {
                        service.app.showException(e);
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                        if (active)
                            close();
                        return false;
                    } finally {
                        if (handshakeErrorThread != null && handshakeErrorThread.isAlive())
                            handshakeErrorThread.interrupt();
                    }
                } else {
                    deviceName = "";
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    addWaiting(this);
                    return false;
                }
            }
        }

        @Override
        public void send(byte[] packet) {
            try {
                android.util.Log.w("PK_DBG_OUT", getMeta(packet));
                out.write(packet);
            } catch (IOException e) {
                service.app.showException(e);
                close();
            }
        }

        private String getMeta(byte[] packet) {
            String toret = "";
            for (int i = 0; i < Math.min(4,packet.length); i++)
                toret += ((packet[i] < 0) ? (int) packet[i] + 128 : packet[i]) + ",";
            if (toret.isEmpty())
                return "";
            return toret.substring(0, toret.length() - 1);
        }

        @Override
        public byte[] receive() {
            try {
                if (!in.isUpgraded())
                    in.upgradeCipher.setKey(key);
                return in.readNext();
            } catch (IOException e) {
                service.app.showException(e);
                close();
                return new byte[0];
            } finally {
                android.util.Log.w("PK_DBG_IN", in.getBufferMetaString());
            }
        }

        @Override
        public void close() {
            removeWaiting(this);
            synchronized (slockServerSocket) {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException ignored) {
                    }
                    serverSocket = null;
                }
                slockServerSocket.notifyAll();
            }
            if (active) {
                active = false;
                removeDevice(deviceName);
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                }
                uuidQueue.add(uuid);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    @SuppressLint("MissingPermission")
    private String getAddress(BluetoothDevice device) {
        if (device == null)
            return null;
        return device.getAddress() + ((device.getName() == null) ? "" : device.getName());
    }
}
