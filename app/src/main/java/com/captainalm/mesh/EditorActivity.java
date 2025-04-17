package com.captainalm.mesh;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
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
                obj.setID(Provider.base64Decode(idBox.getText().toString()));
                if (obj.valid()) {
                    switch (user) {
                        case AllowedNodes -> db.getAllowedNodeDAO().addAllowedNode((AllowedNode) obj);
                        case BlockedNodes -> db.getBlockedNodeDAO().addBlockedNode((BlockedNode) obj);
                        case AllowedNodeSignatureKeys -> db.getAllowedSignatureDAO().addAllowedSignature((AllowedSignature) obj);
                    }
                }
                switchToMain();
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
                    db.getBlockedNodeDAO().addBlockedNode(new BlockedNode(obj.getID()));
                    db.getPeerRequestDAO().removePeerRequest((PeerRequest) obj);
                    switchToMain();
                });
                closeButton.setText(R.string.allow);
                removeButton.setOnClickListener(v -> {
                    db.getAllowedNodeDAO().addAllowedNode(new AllowedNode(obj.getID()));
                    db.getPeerRequestDAO().removePeerRequest((PeerRequest) obj);
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
                    switchToMain();
                });
                closeButton.setText(R.string.close);
                closeButton.setOnClickListener(v -> switchToMain());
            }
        }
    }

    private String headerPrefix(boolean adding) {
        return (adding) ? getString(R.string.editor_add) + " " : getString(R.string.editor_view) + " ";
    }

    private void switchToMain() {
        startActivity(new Intent(this, MainActivity.class).putExtra("frag", user.getID()));
    }
}