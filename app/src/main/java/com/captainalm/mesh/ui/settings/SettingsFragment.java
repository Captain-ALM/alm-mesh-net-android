package com.captainalm.mesh.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.captainalm.mesh.FragmentIndicator;
import com.captainalm.mesh.IRefreshable;
import com.captainalm.mesh.MainActivity;
import com.captainalm.mesh.TheApplication;
import com.captainalm.mesh.databinding.FragmentSettingsBinding;
import com.captainalm.mesh.db.Node;
import com.google.android.material.snackbar.Snackbar;

/**
 * Provides a Settings fragment.
 *
 * @author Alfred Manville
 */
public class SettingsFragment extends Fragment implements IRefreshable {
    private TheApplication app;
    private FragmentSettingsBinding binding;
    private Context container;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity();
        if (context != null && context.getApplicationContext() instanceof TheApplication ta)
            app = ta;

        container = context;
        if (container instanceof MainActivity ma)
            ma.addRefreshable(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        refresh();
        addEvents();
        return binding.getRoot();
    }

    private void addEvents() {
        if (app == null || binding == null)
            return;
        binding.switchEnabler.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                save();
                app.invokeService(binding.switchOnion.isChecked());
                refresh();
            } else
                app.stopService();
        });
        binding.buttonSettingsNewKeys.setOnClickListener(v -> {
            app.regenerateKeys();
            refresh();
        });
        binding.buttonSettingsNewCircuit.setOnClickListener(v -> {
            app.regenerateCircuit();
            refresh();
        });
    }

    protected void refresh() {
        if (app == null || binding == null || app.settings == null || app.thisNode == null)
            return;
        Node c = new Node(app.thisNode);
        binding.textViewSettingsCheckCode.setText(Integer.toString(c.getCheckCode()));
        binding.textViewSettingsID.setText(c.ID);
        binding.textViewSettingsIPv4.setText(ipv4ToIP(app.thisNode.getIPv4Address()));
        binding.textViewSettingsIPv6.setText(ipv6HexToIP(app.thisNode.getIPv6AddressString()));
        binding.switchEnabler.setChecked(app.serviceActive);
        binding.editTextNumberPacketCache.setText(Integer.toString(app.settings.packetChargeSize));
        binding.editTextNumberMaxTTL.setText(Integer.toString(app.settings.maxTTL));
        binding.spinnerEncMode.setSelection(app.settings.encryptionMode);
        binding.editTextExcAddr.setText(app.settings.excludedAddresses);
        binding.editTextRecKey.setText(app.settings.recommendedSigPublicKey);
        binding.editTextRecSig.setText(app.settings.recommendedSig);
        binding.switchGateway.setChecked(app.settings.gatewayMode());
        binding.switchOnion.setChecked(app.settings.etherealPrivateKeyKEM != null && app.settings.etherealPrivateKeyDSA != null);
        binding.buttonSettingsNewCircuit.setEnabled(app.serviceActive && binding.switchOnion.isChecked());
        binding.buttonSettingsNewKeys.setEnabled(!app.serviceActive);
        binding.editTextNumberPacketCache.setEnabled(!app.serviceActive);
        binding.editTextNumberMaxTTL.setEnabled(!app.serviceActive);
        binding.spinnerEncMode.setEnabled(!app.serviceActive);
        binding.editTextExcAddr.setEnabled(!app.serviceActive);
        binding.editTextRecKey.setEnabled(!app.serviceActive);
        binding.editTextRecSig.setEnabled(!app.serviceActive);
        binding.switchGateway.setEnabled(!app.serviceActive);
        binding.switchOnion.setEnabled(!app.serviceActive);
    }

    private String ipv4ToIP(byte[] addr) {
        return  ((addr[0] < 0) ? (int) addr[0] + 128 : addr[0]) + "." +
                ((addr[1] < 0) ? (int) addr[1] + 128 : addr[1]) + "." +
                ((addr[2] < 0) ? (int) addr[2] + 128 : addr[2]) + "." +
                ((addr[3] < 0) ? (int) addr[3] + 128 : addr[3]);
    }

    private String ipv6HexToIP(String hex) {
        String address = "[";
        int remaining = hex.length();
        int pos = 0;
        int remainder = 0;
        while (remaining > 0) {
            if (remaining > 3) {
                address += hex.substring(pos, pos + 4) + ":";
                pos += 4;
                remaining -= 4;
            } else {
                address += hex.substring(pos, pos + remaining);
                pos += remaining;
                remainder = 4 - remaining;
                if (remainder == 0)
                    address += ":";
                remaining = 0;
            }
        }
        if (remainder > 0) {
            while (remainder > 0) {
                remainder--;
                address += "0";
            }
            address += ":";
        }
        return address.substring(0, address.length() - 1) + "]";
    }
    private void save() {
        if (app == null || binding == null || app.settings == null || app.serviceActive)
            return;
        try {
            app.settings.packetChargeSize = Integer.parseInt(binding.editTextNumberPacketCache.getText().toString());
            if (app.settings.packetChargeSize < 4)
                app.settings.packetChargeSize = 4;
            app.settings.maxTTL = Integer.parseInt(binding.editTextNumberMaxTTL.getText().toString());
            if (app.settings.maxTTL < 1)
                app.settings.maxTTL = 1;
            if (app.settings.maxTTL > 254)
                app.settings.maxTTL = 254;
            app.settings.encryptionMode = binding.spinnerEncMode.getSelectedItemPosition();
            app.settings.excludedAddresses = binding.editTextExcAddr.getText().toString();
            app.settings.recommendedSigPublicKey = binding.editTextRecSig.getText().toString();
            app.settings.gatewayOn = (binding.switchGateway.isChecked()) ? 1 : 0;
        } catch (NumberFormatException e) {
            app.showException(e);
        }
        app.database.getSettingsDAO().updateSettings(app.settings);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        save();
        binding = null;
    }

    @Override
    public void onDestroy() {
        if (container instanceof MainActivity ma)
            ma.removeRefreshable(this);
        super.onDestroy();
        app = null;
        container = null;
    }

    @Override
    public void refresh(FragmentIndicator fragRefreshing) {
        if (fragRefreshing == FragmentIndicator.Unknown)
            refresh();
    }
}
