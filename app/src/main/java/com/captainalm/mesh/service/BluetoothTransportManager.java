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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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
            Object infWait = new Object();
            while (active) {
                try {
                    synchronized (infWait) {
                        infWait.wait();
                    }
                } catch (InterruptedException e) {
                    if (active) {
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
                    }
                }
            }
        });
        scannerThread.start();
        uuidQueue.add(UUID.fromString("695a5c7d-6608-479c-b29e-87ae613e0013"));
        uuidQueue.add(UUID.fromString("93271753-a9a5-41c6-82e9-cb32dbe27ad7"));
        uuidQueue.add(UUID.fromString("83d9b788-917f-4392-8d0b-e56f42890a47"));
        uuidQueue.add(UUID.fromString("df56ce3b-0af5-481f-a940-a0d482707b1d"));
        monitorThread.start();
    }

    private void addWaiting(Transport transport) {
        synchronized (slockWaiting) {
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
            if (!deviceBlocked(getAddress(device))) {
                try {
                    Transport nw = getNextWaitingTimeout(5000);
                    if (nw != null)
                        nw.connect(device);
                } catch (InterruptedException ignored) {
                }
            }
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
                        lSS = serverSocket;
                    }
                    boolean connR = true;
                    if (lSS != null) {
                        BluetoothSocket accepted = lSS.accept();
                        if (!connect(accepted)) {
                            try {
                                lSS.close();
                            } catch (IOException ignored) {
                            }
                            listen();
                        }
                    }
                } catch (InterruptedException | SecurityException | IOException ignored) {
                }
            }).start();
        }

        public void connect(BluetoothDevice device) throws InterruptedException {
            if (active)
                return;
            if (device == null) {
                addWaiting(this);
                return;
            }
            if (serverSocket != null) {
                synchronized (slockServerSocket) {
                    try {
                        serverSocket.close();
                    } catch (IOException ignored) {
                    }
                    serverSocket = null;
                }
            }
            try {
                if (connect(device.createInsecureRfcommSocketToServiceRecord(uuid)))
                    return;
            } catch (IOException | SecurityException e) {
            }
            listen();
            addWaiting(this);
        }

        private boolean connect(BluetoothSocket socket) throws InterruptedException {
            synchronized (slockConnect) {
                if (!socket.isConnected()) {
                    try {
                        BluetoothAdapter adapter = service.app.getBluetoothAdapter();
                        if (adapter != null)
                            adapter.cancelDiscovery();
                        socket.connect();
                    } catch (IOException | SecurityException e) {
                        return false;
                    }
                }

                deviceName = getAddress(socket.getRemoteDevice());
                if (!deviceExists(deviceName) && !deviceBlocked(deviceName)) {
                    removeWaiting(this);
                    this.socket = socket;
                    try {
                        Settings settings = service.loadSettings();
                        if (settings == null)
                            return false;
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
                        byte[] outEnc = handshakeProcessor.handshake(5000);
                        if (outEnc == null) {
                            if (!handshakeProcessor.localAuthorizeSuccess())
                                blockDevice(deviceName);
                            socket.close();
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
                            return false;
                        }
                        r.addTransport(handshakeProcessor.getRemote(), this, handshakeProcessor.getOtherPackets());
                        addDevice(deviceName);
                        return true;
                    } catch (GeneralSecurityException | IOException e) {
                        service.app.showException(e);
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                        return false;
                    }
                } else {
                    deviceName = "";
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    return false;
                }
            }
        }

        @Override
        public void send(byte[] packet) {
            try {
                out.write(packet);
            } catch (IOException e) {
                close();
            }
        }

        @Override
        public byte[] receive() {
            try {
                if (!in.isUpgraded())
                    in.upgradeCipher.setKey(key);
                return in.readNext();
            } catch (IOException e) {
                close();
                return new byte[0];
            }
        }

        @Override
        public void close() {
            removeWaiting(this);
            synchronized (slockServerSocket) {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                    }
                    serverSocket = null;
                }
            }
            if (active) {
                active = false;
                removeDevice(deviceName);
                try {
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
