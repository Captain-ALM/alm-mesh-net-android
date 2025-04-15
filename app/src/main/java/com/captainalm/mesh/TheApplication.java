package com.captainalm.mesh;

import android.app.Application;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

/**
 * Provides a way of overriding the security provider.
 *
 * @author Alfred Manville
 */
public class TheApplication extends Application {
    /**
     * Adapted from:
     * https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android/66323575#66323575
     * (satur9nine and Mendhak)
     */
    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }
}
