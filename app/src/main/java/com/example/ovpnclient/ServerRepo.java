package com.example.ovpnclient;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class ServerRepo {
    public static final String LIST_URL =
        "https://raw.githubusercontent.com/onfarooq0008-eng/Working-Ovpn/refs/heads/main/servers.json";

    public static final class Server {
        public final String name, ip, country, ping, url;
        public final double speed;
        Server(JSONObject o) {
            name = o.optString("name"); ip = o.optString("ip");
            country = o.optString("country"); ping = o.optString("ping_ms", "?");
            url = o.optString("url"); speed = o.optDouble("speed_mbps", 0);
        }
    }

    static String get(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static List<Server> parse(String text) throws Exception {
        JSONArray arr = new JSONObject(text).getJSONArray("servers");
        List<Server> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(new Server(arr.getJSONObject(i)));
        return out;
    }

    /** Download the list, validate it, cache it, return it. */
    public static List<Server> refresh(Context ctx) throws Exception {
        String text = get(LIST_URL);
        List<Server> list = parse(text);
        try (FileWriter w = new FileWriter(new File(ctx.getFilesDir(), "servers.json"))) { w.write(text); }
        return list;
    }

    public static List<Server> cached(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), "servers.json");
            if (!f.exists()) return new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return parse(sb.toString());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String fetchOvpn(String url) throws IOException { return get(url); }
}
