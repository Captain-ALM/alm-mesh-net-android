package com.captainalm.lib.mesh.crypto;

import com.captainalm.mesh.TheApplication;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Provides a hasher.
 *
 * @author Alfred Manville
 */
public class Hasher implements IHasher {
    final ThreadLocal<MessageDigest> lDigest = new ThreadLocal<>();
    private final TheApplication context;

    public Hasher(TheApplication context) {
        this.context = context;
    }

    @Override
    public byte[] hash(byte[] bytes) {
        if (bytes == null)
            return new byte[32];
        try {
            if (lDigest.get() == null)
                lDigest.set(MessageDigest.getInstance("SHA-256"));
            return lDigest.get().digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            if (context != null)
                context.showException(e);
            return new byte[32];
        }
    }

    @Override
    public byte[] hashStream(InputStream inputStream, int i) throws IOException {
        if (inputStream == null)
            return new byte[32];
        try {
            if (lDigest.get() == null)
                lDigest.set(MessageDigest.getInstance("SHA-256"));
            byte[] buff = new byte[8192];
            int n = inputStream.read(buff, 0, Math.min(i, 8192));
            lDigest.get().update(buff, 0, n);
            i -= n;
            while (n > 0) {
                n = inputStream.read(buff, 0, Math.min(i, 8192));
                lDigest.get().update(buff, 0, n);
            }
            return lDigest.get().digest();
        } catch (NoSuchAlgorithmException e) {
            if (context != null)
                context.showException(e);
            return new byte[32];
        }
    }
}
