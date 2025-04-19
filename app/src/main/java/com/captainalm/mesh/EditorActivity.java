package com.captainalm.mesh;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.mesh.db.AllowedNode;
import com.captainalm.mesh.db.AllowedSignature;
import com.captainalm.mesh.db.BaseIDEntity;
import com.captainalm.mesh.db.BlockedNode;
import com.captainalm.mesh.db.PeerRequest;
import com.captainalm.mesh.db.TheDatabase;
import com.google.android.material.snackbar.Snackbar;

public class EditorActivity extends AppCompatActivity {
    FragmentIndicator user = FragmentIndicator.Unknown;
    boolean adding;
    BaseIDEntity obj;
    TheApplication app;

    @Override
    protected void onDestroy() {
            super.onDestroy();
            obj = null;
            app = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_editor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        user = FragmentIndicator.getIndicator(getIntent().getIntExtra("frag",0));
        adding = getIntent().getBooleanExtra("adder", false);
        if (getApplicationContext() instanceof TheApplication ta)
            app = ta;

        if (app == null)
            return;

        TextView header = findViewById(R.id.editorHeader);
        TextView idLabel = findViewById(R.id.textViewID);
        EditText idBox = findViewById(R.id.editTextID);
        EditText dataBox = findViewById(R.id.editTextData);
        Button removeButton = findViewById(R.id.buttonActionRemove);
        Button closeButton =    findViewById(R.id.buttonActionClose);

        switch (user) {
            case AllowedNodes -> {
                header.setText(headerPrefix(adding) + getString(R.string.editor_allowed_node));
                idLabel.setText(R.string.editor_id);
                if (adding)
                    obj = new AllowedNode();
                else {
                    obj = app.database.getAllowedNodeDAO().getAllowedNode(getIntent().getStringExtra("edit_id"));
                }
            }
            case BlockedNodes -> {
                header.setText(headerPrefix(adding) + getString(R.string.editor_blocked_node));
                idLabel.setText(R.string.editor_id);
                if (adding)
                    obj = new BlockedNode();
                else {
                    obj = app.database.getBlockedNodeDAO().getBlockedNode(getIntent().getStringExtra("edit_id"));
                }
            }
            case AllowedNodeSignatureKeys -> {
                header.setText(headerPrefix(adding) + getString(R.string.editor_allowed_key));
                idLabel.setText(R.string.editor_key);
                if (adding)
                    obj = new AllowedSignature();
                else {
                    obj = app.database.getAllowedSignatureDAO().getAllowedSignature(getIntent().getStringExtra("edit_id"));
                }
            }
            case PeeringRequests -> {
                header.setText(headerPrefix(adding) + getString(R.string.editor_peer_request));
                idLabel.setText(R.string.editor_id);
                if (adding)
                    obj = new PeerRequest();
                else {
                    obj = app.database.getPeerRequestDAO().getPeerRequest(getIntent().getStringExtra("edit_id"));
                }
            }
        }

        dataBox.setTextIsSelectable(true);

        if (obj == null) {
            removeButton.setOnClickListener(v -> switchToMain());
            closeButton.setOnClickListener(v -> switchToMain());
            return;
        }

        if (adding) {
            removeButton.setText(R.string.close);
            removeButton.setOnClickListener(v -> switchToMain());
            closeButton.setText(R.string.editor_add);
            closeButton.setOnClickListener(v -> {
                TheDatabase db = app.database;
                if (obj instanceof AllowedSignature)
                    obj.setID(Provider.base64Decode(idBox.getText().toString()));
                else if (idBox.getText().length() == 64)
                    obj.ID = idBox.getText().toString();
                else
                    obj.ID = "";
                if (obj.valid()) {
                    switch (user) {
                        case AllowedNodes -> {
                            if (db.getBlockedNodeDAO().getBlockedNode(obj.ID) == null) {
                                db.getAllowedNodeDAO().addAllowedNode((AllowedNode) obj);
                                if (db.getPeerRequestDAO().getPeerRequest(obj.ID) != null)
                                    db.getPeerRequestDAO().removePeerRequest(new PeerRequest(obj.getID()));
                                if (app != null)
                                    app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                            } else {
                                Snackbar.make(v, "Node Blocked Already!", Snackbar.LENGTH_LONG).setAnchorView(R.id.buttonActionClose).setAction("Entry Issue", null).show();
                                return;
                            }
                        }
                        case BlockedNodes -> {
                            if (db.getAllowedNodeDAO().getAllowedNode(obj.ID) == null) {
                                db.getBlockedNodeDAO().addBlockedNode((BlockedNode) obj);
                                if (db.getPeerRequestDAO().getPeerRequest(obj.ID) != null)
                                    db.getPeerRequestDAO().removePeerRequest(new PeerRequest(obj.getID()));
                                if (app != null)
                                    app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                            } else {
                                Snackbar.make(v, "Node Allowed Already!", Snackbar.LENGTH_LONG).setAnchorView(R.id.buttonActionClose).setAction("Entry Issue", null).show();
                                return;
                            }
                        }
                        case AllowedNodeSignatureKeys -> {
                            db.getAllowedSignatureDAO().addAllowedSignature((AllowedSignature) obj);
                            if (app != null)
                                app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                        }
                    }
                    switchToMain();
                } else if (app != null)
                    Snackbar.make(v, "Invalid Entry!", Snackbar.LENGTH_LONG).setAnchorView(R.id.buttonActionClose).setAction("Entry Issue", null).show();
            });
        } else {
            idBox.setInputType(InputType.TYPE_NULL);
            idBox.setTextIsSelectable(true);
            idBox.setText(obj.ID);
            dataBox.setText(obj.extraData());
            if (user == FragmentIndicator.PeeringRequests) {
                TheDatabase db = app.database;
                removeButton.setText(R.string.deny);
                removeButton.setOnClickListener(v -> {
                    if (db.getAllowedNodeDAO().getAllowedNode(obj.ID) == null)
                        db.getBlockedNodeDAO().addBlockedNode(new BlockedNode(obj.getID()));
                    db.getPeerRequestDAO().removePeerRequest((PeerRequest) obj);
                    if (app != null)
                        app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                    switchToMain();
                });
                closeButton.setText(R.string.allow);
                closeButton.setOnClickListener(v -> {
                    if (db.getBlockedNodeDAO().getBlockedNode(obj.ID) == null)
                        db.getAllowedNodeDAO().addAllowedNode(new AllowedNode(obj.getID()));
                    db.getPeerRequestDAO().removePeerRequest((PeerRequest) obj);
                    if (app != null)
                        app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                    switchToMain();
                });
            } else {
                removeButton.setText(R.string.delete);
                removeButton.setOnClickListener(v -> {
                    TheDatabase db = app.database;
                    switch (user) {
                        case AllowedNodes -> db.getAllowedNodeDAO().removeAllowedNode((AllowedNode) obj);
                        case BlockedNodes -> db.getBlockedNodeDAO().removeBlockedNode((BlockedNode) obj);
                        case AllowedNodeSignatureKeys -> db.getAllowedSignatureDAO().removeAllowedSignature((AllowedSignature) obj);
                    }
                    if (app != null)
                        app.sendBroadcast(new Intent(IntentActions.PURGE_BLOCKED));
                    switchToMain();
                });
                closeButton.setText(R.string.close);
                closeButton.setOnClickListener(v -> switchToMain());
            }

            idBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (obj != null && !s.toString().equals(obj.ID))
                        idBox.setText(obj.ID);
                }
            });
        }

        dataBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (obj != null && !s.toString().equals(obj.extraData()))
                    dataBox.setText(obj.extraData());
            }
        });
    }

    private String headerPrefix(boolean adding) {
        return (adding) ? getString(R.string.editor_add) + " " : getString(R.string.editor_view) + " ";
    }

    private void switchToMain() {
        this.startActivity(new Intent(this, MainActivity.class).putExtra("frag", user.getID()).putExtra("refresh", true));
    }
}