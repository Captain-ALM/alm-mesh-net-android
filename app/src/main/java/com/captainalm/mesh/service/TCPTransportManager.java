package com.captainalm.mesh.service;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.crypto.StaticCryptor;
import com.captainalm.lib.mesh.handshake.HandshakeProcessor;
import com.captainalm.lib.mesh.packets.Packet;
import com.captainalm.lib.mesh.packets.PacketBytesInputStream;
import com.captainalm.lib.mesh.routing.Router;
import com.captainalm.lib.mesh.transport.INetTransport;
import com.captainalm.mesh.IntentActions;
import com.captainalm.mesh.MeshVpnService;
import com.captainalm.mesh.db.Settings;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/**
 * TCP Transport Manager.
 *
 * @author Alfred Manville
 */
public class TCPTransportManager extends TransportManager {
    private final SecureRandom random = new SecureRandom();
    private ServerSocket sSocket;
    private boolean listen;
    private final int PORT = 30909;
    private final int TIMEOUT = 10000;
    private final List<String> allowedNetworks = new ArrayList<>();

    public TCPTransportManager(MeshVpnService service) {
        super(service);
        String base = "172.";
        for (int a = 16; a <= 32; a++) {
            for (int b = 0; b <= 255; b++)
                allowedNetworks.add(base + a + "." + b);
        }
        base = "192.168.";
        for (int b = 0; b <= 255; b++)
            allowedNetworks.add(base + b);
        monitorThread = new Thread(() -> {
            resetListener();
            Object slockL = new Object();
            synchronized (slockL) {
                try {
                    while (active)
                        slockL.wait();
                } catch (InterruptedException ignored) {
                }
            }
            try {
                synchronized (slockListen) {
                    if (sSocket != null)
                        sSocket.close();
                }
            } catch (IOException ignored) {
            }
        });
        monitorThread.start();
        scannerThread = new Thread(() -> {
            while (active) {
                Object slockL = new Object();
                synchronized (slockL) {
                    try {
                        slockL.wait();
                    } catch (InterruptedException ignored) {
                    }
                    ConnectivityManager cm = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        List<String> targNetworks = new ArrayList<>();
                        List<String> myAddresses = new ArrayList<>();
                        Network[] networks = cm.getAllNetworks();
                        for (Network n : networks) {
                            LinkProperties lp = cm.getLinkProperties(n);
                            if (lp != null) {
                                List<LinkAddress> addrs = lp.getLinkAddresses();
                                for (LinkAddress addr : addrs) {
                                    String addrT = addr.getAddress().getHostAddress();
                                    if (addrT == null)
                                        continue;
                                    myAddresses.add(addrT);
                                    for (String c : allowedNetworks)
                                        if (addrT.startsWith(c))
                                            targNetworks.add(c);
                                }
                            }
                        }
                        for (String tNet : targNetworks) {
                            for (int b = 130; b <= 135; b++) {
                                String tAddr = tNet + "." + b;
                                if (!myAddresses.contains(tAddr)) {
                                    try {
                                        Socket s = new Socket();
                                        s.bind(new InetSocketAddress(0));
                                        service.protect(s);
                                        s.connect(new InetSocketAddress(tAddr, PORT), 5000);
                                        connect(s);
                                    } catch (IOException ignored) {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        scannerThread.start();
    }

    private final Object slockConnect = new Object();
    public void connect(Socket socket) {
        synchronized (slockConnect) {
            InetAddress addr = socket.getInetAddress();
            if (addr == null) {
                safeCloseSocket(socket);
                return;
            }
            String deviceName = addr.getHostAddress();
            if (!deviceExists(deviceName) && !deviceBlocked(deviceName)) {
                android.util.Log.w("T-C", "Attempting connection: " + deviceName);
                addDevice(deviceName);
                android.util.Log.w("T-C", "Starting connection: " + deviceName);
                Thread handshakeErrorThread = null;
                Transport transport = null;
                try {
                    transport = new Transport(socket, deviceName);
                    Settings settings = service.loadSettings();
                    if (settings == null) {
                        removeDevice(deviceName);
                        return;
                    }
                    byte[][] keyData = service.app.cryptographyProvider.GetWrapperInstance().setPublicKey(
                                    Provider.getMLKemPublicKeyBytes(settings.getPrivateKeyKEM().getPublicKey()))
                            .wrap(random);
                    transport.key = keyData[0];
                    transport.in.upgradeCipher = new StaticCryptor().setKey(transport.key);
                    HandshakeProcessor handshakeProcessor = new HandshakeProcessor(service.app.thisNode, transport,
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
                    byte[] outEnc = handshakeProcessor.handshake(TIMEOUT);
                    android.util.Log.w("B-C", "Handshaked: " + deviceName +
                            ((outEnc == null) ? "-" : "+") + (handshakeProcessor.localAuthorizeSuccess() ? "+" : "-"));
                    if (outEnc == null) {
                        if (!handshakeProcessor.localAuthorizeSuccess())
                            blockDevice(deviceName);
                        safeCloseSocket(socket);
                        transport.close();
                        return;
                    }
                    Router r = getRouter();
                    if (r == null) {
                        safeCloseSocket(socket);
                        transport.close();
                        return;
                    }
                    r.addTransport(handshakeProcessor.getRemote(), transport, handshakeProcessor.getOtherPackets());
                } catch (GeneralSecurityException | IOException | InterruptedException e) {
                    if (!(e instanceof InterruptedException))
                        service.app.showException(e);
                    safeCloseSocket(socket);
                    if (transport != null && transport.isActive())
                        transport.close();
                } finally {
                    if (handshakeErrorThread != null && handshakeErrorThread.isAlive())
                        handshakeErrorThread.interrupt();
                }
            } else {
                if (socket.isConnected())
                    safeCloseSocket(socket);
            }
        }
    }

    private final Object slockListen = new Object();
    private void resetListener() {
        synchronized (slockListen) {
            if (sSocket != null) {
                try {
                    listen = false;
                    sSocket.close();
                } catch (IOException ignored) {
                }
            }
            try {
                sSocket = new ServerSocket(PORT);
                listen = true;
            } catch (IOException ignored) {
                sSocket = null;
            }
            if (sSocket != null)
                new Thread(() -> {
                    while (active && listen) {
                        try {
                            Socket accepted = sSocket.accept();
                            connect(accepted);
                        } catch (IOException ignored) {
                        }
                    }
                }).start();
        }
    }

    @Override
    public void discover() {
        if (scannerThread.isAlive())
            scannerThread.interrupt();
    }

    @Override
    public void receiveBroadcast(Context context, Intent intent) {
        if (IntentActions.ANNOUNCE.equals(intent.getAction()))
            resetListener();
    }

    private class Transport implements INetTransport {
        public final String device;
        public final PacketBytesInputStream in;
        public OutputStream out;
        private final Socket socket;
        public byte[] key;
        public byte[] outEnc;
        private boolean nextWriteUpgrades = false;
        private boolean active = true;

        public Transport(Socket socket, String device) throws IOException {
            this.socket = socket;
            this.device = device;
            this.in = new PacketBytesInputStream(socket.getInputStream());
            this.out = socket.getOutputStream();
        }

        @Override
        public void send(byte[] packet) {
            if (!active)
                return;
            try {
                if (nextWriteUpgrades) {
                    nextWriteUpgrades = false;
                    byte[] iv = Provider.generateIV(16);
                    out.write(iv);
                    out = new CipherOutputStream(out, new StaticCryptor()
                            .setKey(outEnc).getCipher(Cipher.ENCRYPT_MODE, iv));
                }
                android.util.Log.w("PK_DBG_OUT", packet.length + "#-#" + Packet.getPacketFromBytes(packet).getPacketSize() + "#-#" + getMeta(packet));
                out.write(packet);
            } catch (IOException | GeneralSecurityException e) {
                service.app.showException(e);
                close();
            }
        }

        @Override
        public byte[] receive() {
            try {
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
            if (active) {
                removeDevice(device);
                active = false;
                safeClose(in);
                safeClose(out);
                safeClose(socket);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void upgrade(byte[] oKey) {
            if (!in.isUpgraded()) {
                outEnc = oKey;
                in.upgrade();
                nextWriteUpgrades = true;
            }
        }
    }

    private void safeCloseSocket(Socket socket) {
        if (socket != null) {
            try {
                safeClose(socket.getInputStream());
            } catch (IOException ignored) {
            }
            try {
                safeClose(socket.getOutputStream());
            } catch (IOException ignored) {
            }
        }
        safeClose(socket);
    }

    private void safeClose(Closeable closeable) {
        if (closeable == null)
            return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private String getMeta(byte[] packet) {
        String toret = "";
        for (int i = 0; i < Math.min(12,packet.length); i++)
            toret += ((packet[i] < 0) ? (int) packet[i] + 256 : packet[i]) + ",";
        if (toret.isEmpty())
            return "";
        return toret.substring(0, toret.length() - 1);
    }
}
