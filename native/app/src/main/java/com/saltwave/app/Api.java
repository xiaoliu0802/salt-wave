package com.saltwave.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 Subsonic / Navidrome 客户端。
 * 认证方式：u + t=md5(password+salt) + s=salt（token 认证）。
 */
public class Api {

    public static Api current;

    private final String base;
    private final String user;
    private final String token;
    private final String salt;

    public Api(String server, String user, String pass) {
        String b = server == null ? "" : server.trim();
        if (!b.startsWith("http://") && !b.startsWith("https://")) {
            b = "http://" + b;
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!b.startsWith("http://") && !b.startsWith("https://")) {
            throw new IllegalArgumentException("服务器地址无效");
        }
        if (b.endsWith("/rest")) {
            b = b.substring(0, b.length() - 5);
        }
        this.base = b;
        this.user = user == null ? "" : user.trim();
        this.salt = "sw" + Long.toHexString(System.currentTimeMillis());
        this.token = md5((pass == null ? "" : pass) + salt);
    }

    public String base() {
        return base;
    }

    public void ping() throws Exception {
        call("ping");
    }

    // ---------------- 网络 ----------------

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }

    private String url(String method, String... kv) {
        StringBuilder sb = new StringBuilder();
        sb.append(base).append("/rest/").append(method)
          .append("?u=").append(enc(user))
          .append("&t=").append(token)
          .append("&s=").append(salt)
          .append("&v=1.16.1&c=SaltWave&f=json");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] == null) continue;
            sb.append('&').append(kv[i]).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    private JSONObject call(String method, String... kv) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url(method, kv)).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "SaltWave/1.1");
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(is);
            JSONObject root = new JSONObject(body);
            JSONObject r = root.optJSONObject("subsonic-response");
            if (r == null) throw new Exception("这个地址看起来不是 Subsonic 服务");
            if (!"ok".equals(r.optString("status"))) {
                JSONObject err = r.optJSONObject("error");
                String msg = err == null ? "认证失败" : err.optString("message", "认证失败");
                String codeStr = err == null ? "" : (" (" + err.optInt("code") + ")");
                throw new Exception(msg + codeStr);
            }
            return r;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    // ---------------- 业务 ----------------

    public List<Album> albums(int size) throws Exception {
        List<Album> out = new ArrayList<Album>();
        JSONObject r = call("getAlbumList2", "type", "alphabeticalByName", "size", String.valueOf(size), "offset", "0");
        JSONObject holder = r.optJSONObject("albumList2");
        JSONArray arr = holder == null ? null : holder.optJSONArray("album");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(album(arr.optJSONObject(i)));
            }
        }
        return out;
    }

    public List<Album> starred() throws Exception {
        List<Album> out = new ArrayList<Album>();
        JSONObject r = call("getStarred2");
        JSONObject holder = r.optJSONObject("starred2");
        JSONArray arr = holder == null ? null : holder.optJSONArray("album");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) out.add(album(arr.optJSONObject(i)));
        }
        return out;
    }

    public List<Playlist> playlists() throws Exception {
        List<Playlist> out = new ArrayList<Playlist>();
        JSONObject r = call("getPlaylists");
        JSONObject holder = r.optJSONObject("playlists");
        JSONArray arr = holder == null ? null : holder.optJSONArray("playlist");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Playlist p = new Playlist();
                p.id = o.optString("id");
                p.name = o.optString("name", "未命名歌单");
                p.count = o.optInt("songCount", 0);
                String cv = o.optString("coverArt", "");
                p.cover = cover(cv.length() > 0 ? cv : p.id, 420);
                out.add(p);
            }
        }
        return out;
    }

    public List<Track> albumSongs(String albumId) throws Exception {
        List<Track> out = new ArrayList<Track>();
        JSONObject r = call("getAlbum", "id", albumId);
        JSONObject holder = r.optJSONObject("album");
        JSONArray arr = holder == null ? null : holder.optJSONArray("song");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) out.add(song(arr.optJSONObject(i)));
        }
        return out;
    }

    public List<Track> playlistSongs(String plId) throws Exception {
        List<Track> out = new ArrayList<Track>();
        JSONObject r = call("getPlaylist", "id", plId);
        JSONObject holder = r.optJSONObject("playlist");
        JSONArray arr = holder == null ? null : holder.optJSONArray("entry");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) out.add(song(arr.optJSONObject(i)));
        }
        return out;
    }

    public String cover(String coverArtId, int size) {
        if (coverArtId == null || coverArtId.length() == 0) return null;
        return url("getCoverArt", "id", coverArtId, "size", String.valueOf(size));
    }

    public String stream(Track t) {
        return url("stream", "id", t.id);
    }

    // ---------------- 解析 ----------------

    private Album album(JSONObject o) {
        Album a = new Album();
        if (o == null) return a;
        a.id = o.optString("id");
        a.name = o.optString("name", "未命名专辑");
        String ar = o.optString("artist", "");
        if (ar.length() == 0) ar = o.optString("albumArtist", "");
        a.artist = ar;
        a.count = o.optInt("songCount", 0);
        String cv = o.optString("coverArt", "");
        a.cover = cover(cv.length() > 0 ? cv : a.id, 420);
        return a;
    }

    private Track song(JSONObject o) {
        Track t = new Track();
        if (o == null) return t;
        t.id = o.optString("id");
        t.title = o.optString("title", "未命名");
        t.artist = o.optString("artist", "");
        t.album = o.optString("album", "");
        t.duration = o.optInt("duration", 0);
        String cv = o.optString("coverArt", "");
        t.cover = cover(cv.length() > 0 ? cv : null, 500);
        return t;
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
