package com.captainalm.mesh.db;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.handshake.IPeerAuthorizer;

/**
 * Tries to authorize a peer.
 *
 * @author Alfred Manville
 */
public final class Authorizer implements IPeerAuthorizer {
    private final TheDatabase theDatabase;

    public Authorizer(TheDatabase db) {
        theDatabase = db;
    }

    @Override
    public boolean authorize(byte[] ID, byte[] recommendationPubKey) {
        if (ID == null)
            return false;
        if (theDatabase.getAllowedNodeDAO().getAllowedNode(Provider.base64Encode(ID)) != null)
            return true;
        if (recommendationPubKey == null)
            return false;
        return theDatabase.getAllowedSignatureDAO().getAllowedSignature(Provider.base64Encode(recommendationPubKey)) != null;
    }
}
