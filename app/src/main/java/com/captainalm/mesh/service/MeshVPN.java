package com.captainalm.mesh.service;

import android.os.ParcelFileDescriptor;

import com.captainalm.lib.mesh.transport.INetTransport;
import com.captainalm.mesh.MeshVpnService;
import com.captainalm.mesh.TheApplication;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Provides a transport for {@link com.captainalm.lib.mesh.transport.INetTransport}
 * in VPN mode for IP packets.
 *
 * @author Alfred Manville
 */
public final class MeshVPN implements INetTransport  {
    private final ParcelFileDescriptor vpnInterface;
    private final FileInputStream vpnOut;
    private final FileOutputStream vpnIn;
    private final TheApplication app;
    private final byte[] recv = new byte[MeshVpnService.MTU];
    private boolean active = true;

    public MeshVPN(TheApplication ta, ParcelFileDescriptor iface) {
        app = ta;
        vpnInterface = iface;
        vpnIn = new FileOutputStream(iface.getFileDescriptor());
        vpnOut = new FileInputStream(iface.getFileDescriptor());
    }

    @Override
    public void send(byte[] packet) {
        if (!active || packet == null || packet.length > MeshVpnService.MTU)
            return;
        try {
            vpnIn.write(packet);
        } catch (IOException e) {
            if (app != null)
                app.showException(e);
            close();
        }
    }

    @Override
    public byte[] receive() {
        if (active) {
            try {
                int n = vpnOut.read(recv);
                if (n > 0) {
                    byte[] buff = new byte[n];
                    System.arraycopy(recv, 0, buff, 0, n);
                    return buff;
                }
            } catch (IOException e) {
                if (app != null)
                    app.showException(e);
                close();
            }
        }
        return new byte[0];
    }

    @Override
    public void close() {
        if (!active)
            return;
        try {
            active = false;
            vpnInterface.close();
        } catch (IOException e) {
            if (app != null)
                app.showException(e);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }
}
