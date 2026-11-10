package com.example.ovpnclient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

public class ServerListActivity extends Activity {
    private static final int REQ_VPN = 1;
    private static final String PROFILE_PREFIX = "ovpnclient · ";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<ServerRepo.Server> servers = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView statusView, infoView;
    private ServerRepo.Server pending;
    private VpnStatus.StateListener stateListener;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setFitsSystemWindows(true);

        statusView = new TextView(this);
        statusView.setTextSize(18);
        statusView.setText("Status: Disconnected");
        infoView = new TextView(this);

        LinearLayout row = new LinearLayout(this);
        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> refresh());
        Button disc = new Button(this);
        disc.setText("Disconnect");
        disc.setOnClickListener(v -> disconnect());
        row.addView(refresh);
        row.addView(disc);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> connect(servers.get(pos)));

        root.addView(statusView);
        root.addView(infoView);
        root.addView(row);
        root.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        stateListener = new VpnStatus.StateListener() {
            @Override public void updateState(String state, String msg, int resId,
                                              ConnectionStatus level, Intent intent) {
                final String text = level == ConnectionStatus.LEVEL_CONNECTED ? "Connected"
                    : level == ConnectionStatus.LEVEL_NOTCONNECTED ? "Disconnected"
                    : String.valueOf(state);
                runOnUiThread(() -> statusView.setText("Status: " + text));
            }
            @Override public void setConnectedVPN(String uuid) {}
        };
        VpnStatus.addStateListener(stateListener);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
        }
        show(ServerRepo.cached(this));
        RefreshJob.schedule(this);
        refresh();
    }

    @Override protected void onDestroy() {
        VpnStatus.removeStateListener(stateListener);
        io.shutdownNow();
        super.onDestroy();
    }

    private void show(List<ServerRepo.Server> list) {
        servers.clear();
        servers.addAll(list);
        labels.clear();
        for (ServerRepo.Server s : list) {
            labels.add(s.country + " · " + s.ip + "\n" + s.speed + " Mbps · " + s.ping + " ms");
        }
        adapter.notifyDataSetChanged();
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }

    private void refresh() {
        infoView.setText("Refreshing…");
        io.execute(() -> {
            try {
                final List<ServerRepo.Server> l = ServerRepo.refresh(this);
                runOnUiThread(() -> { show(l); infoView.setText(l.size() + " servers"); });
            } catch (Exception e) {
                runOnUiThread(() -> infoView.setText("Refresh failed: " + e.getMessage()));
            }
        });
    }

    private void connect(ServerRepo.Server s) {
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            pending = s;
            startActivityForResult(prep, REQ_VPN);
        } else {
            startVpn(s);
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN && res == RESULT_OK && pending != null) startVpn(pending);
    }

    private void startVpn(ServerRepo.Server s) {
        statusView.setText("Status: Fetching config…");
        io.execute(() -> {
            try {
                String cfg = ServerRepo.fetchOvpn(s.url);
                ConfigParser cp = new ConfigParser();
                cp.parseConfig(new StringReader(cfg));
                VpnProfile vp = cp.convertProfile();
                vp.mName = PROFILE_PREFIX + s.name;

                ProfileManager pm = ProfileManager.getInstance(this);
                for (VpnProfile old : new ArrayList<>(pm.getProfiles())) {
                    if (old.mName != null && old.mName.startsWith(PROFILE_PREFIX)) pm.removeProfile(this, old);
                }
                pm.addProfile(vp);
                pm.saveProfile(this, vp);
                pm.saveProfileList(this);
                runOnUiThread(() ->
                    VPNLaunchHelper.startOpenVpn(vp, getApplicationContext(), "ServerList", true));
            } catch (Exception e) {
                toast("Connect failed: " + e.getMessage());
            }
        });
    }

    private void disconnect() {
        Intent i = new Intent(this, OpenVPNService.class);
        i.setAction(OpenVPNService.START_SERVICE);
        bindService(i, new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName n, IBinder b) {
                try { IOpenVPNServiceInternal.Stub.asInterface(b).stopVPN(false); }
                catch (RemoteException ignored) {}
                unbindService(this);
            }
            @Override public void onServiceDisconnected(ComponentName n) {}
        }, BIND_AUTO_CREATE);
    }
}
