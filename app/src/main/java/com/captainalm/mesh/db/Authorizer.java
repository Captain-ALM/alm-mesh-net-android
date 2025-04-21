package com.captainalm.mesh.db;

import android.content.Context;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.handshake.IPeerAuthorizer;
import com.captainalm.lib.mesh.utils.BytesToHex;
import com.captainalm.mesh.TheApplication;

/**
 * Tries to authorize a peer.
 *
 * @author Alfred Manville
 */
public final class Authorizer implements IPeerAuthorizer {
    private final TheApplication theApplication;
    private Context context;
    private final Object slockContext = new Object();

    public Authorizer(TheApplication ta) {
        theApplication = ta;
    }

    @Override
    public boolean authorize(byte[] ID, byte[] recommendationPubKey) {
        if (ID == null)
            return false;
        if (theApplication.database.getBlockedNodeDAO().getBlockedNode(BytesToHex.bytesToHex(ID)) != null)
            return false;
        if (theApplication.database.getAllowedNodeDAO().getAllowedNode(BytesToHex.bytesToHex(ID)) != null)
            return true;
        if (recommendationPubKey == null || theApplication.database.getAllowedSignatureDAO().getAllowedSignature(Provider.base64Encode(recommendationPubKey)) == null) {
            Context lContext;
            synchronized (slockContext) {
                lContext = (context == null) ? theApplication : context;
            }
            theApplication.peerRequestOperation(lContext, new PeerRequest(ID));
            return false;
        }
        return true;
    }

    public void setContext(Context ctx) {
        synchronized (slockContext) {
            context = ctx;
        }
    }
}
