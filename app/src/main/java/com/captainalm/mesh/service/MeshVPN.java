package com.captainalm.mesh.service;

import com.captainalm.lib.mesh.transport.INetTransport;

/**
 * Provides a thread for {@link com.captainalm.mesh.MeshVpnService} and
 * a transport for {@link com.captainalm.lib.mesh.transport.INetTransport}.
 *
 * @author Alfred Manville
 */
public final class MeshVPN implements Runnable, INetTransport  {
    @Override
    public void send(byte[] packet) {

    }

    @Override
    public byte[] receive() {
        return new byte[0];
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void run() {

    }
}
