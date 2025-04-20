package com.captainalm.mesh.ui.settings;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.captainalm.lib.mesh.routing.graphing.GraphNode;
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
        if (app == null || binding == null || container == null)
            return;
        binding.switchEnabler.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                save();
                if (container instanceof MainActivity ma)
                    ma.startVPN(binding.switchOnion.isChecked());
                refresh();
            } else
                app.stopService(container);
        });
        binding.buttonSettingsNewKeys.setOnClickListener(v -> {
            app.regenerateKeys();
            refresh();
        });
        binding.buttonSettingsNewCircuit.setOnClickListener(v -> {
            app.regenerateCircuit();
            refresh();
        });
        binding.switchOnion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                app.settings.etherealPrivateKeyKEM = null;
                app.settings.etherealPrivateKeyDSA = null;
            }
            refreshAddresses();
        });
        binding.switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !app.getBluetoothEnabled() && container instanceof MainActivity ma) {
                save();
                ma.triggerBluetoothEnable();
            }
        });
        binding.buttonDiscoverable.setOnClickListener(v -> {
            if (container instanceof MainActivity ma) {
                binding.buttonDiscoverable.setEnabled(false);
                ma.triggerBluetoothDiscoverable();
                //TODO: Trigger Wi-Fi direct discoverable
            }
        });
        //TODO: Add events / support for permission requests
    }

    private void refreshAddresses() {
        GraphNode ce = app.getThisEtherealNode();
        binding.textViewSettingsIPv4.setText(app.ipv4ToIP((ce == null || !app.serviceActive) ? app.thisNode.getIPv4Address() : ce.getIPv4Address()));
        binding.textViewSettingsIPv6.setText(app.ipv6HexToIP((ce == null || !app.serviceActive) ? app.thisNode.getIPv6AddressString() : ce.getIPv6AddressString()));
    }

    protected void refresh() {
        if (app == null || binding == null || app.settings == null || app.thisNode == null)
            return;
        binding.buttonDiscoverable.setEnabled(app.serviceActive && container instanceof MainActivity ma && !ma.isDiscovering());
        Node c = new Node(app.thisNode);
        binding.textViewSettingsCheckCode.setText(Integer.toString(c.getCheckCode()));
        binding.textViewSettingsID.setText(c.ID);
        refreshAddresses();
        binding.switchEnabler.setChecked(app.serviceActive);
        binding.editTextNumberPacketCache.setText(Integer.toString(app.settings.packetChargeSize));
        binding.editTextNumberMaxTTL.setText(Integer.toString(app.settings.maxTTL));
        binding.spinnerEncMode.setSelection(app.settings.encryptionMode);
        binding.editTextExcAddr.setText(app.settings.excludedAddresses);
        binding.editTextRecKey.setText(app.settings.recommendedSigPublicKey);
        binding.editTextRecSig.setText(app.settings.recommendedSig);
        binding.switchGateway.setChecked(app.settings.gatewayMode());
        binding.switchOnion.setChecked(app.settings.etherealPrivateKeyKEM != null && app.settings.etherealPrivateKeyDSA != null);
        binding.switchBluetooth.setChecked(app.settings.enabledBluetooth() && app.getBluetoothEnabled());
        binding.switchWiFiDirect.setChecked(app.settings.enabledWiFiDirect() && app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
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
        binding.switchBluetooth.setEnabled(!app.serviceActive && app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        binding.switchWiFiDirect.setEnabled(!app.serviceActive && app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
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
            app.settings.setBluetooth(binding.switchBluetooth.isChecked());
            app.settings.setWiFiDirect(binding.switchWiFiDirect.isChecked());
        } catch (NumberFormatException e) {
            app.showException(e);
        }
        app.database.getSettingsDAO().updateSettings(app.settings);
    }

    @Override
    public void onDestroyView() {
        save();
        super.onDestroyView();
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
