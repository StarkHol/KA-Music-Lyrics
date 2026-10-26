package com.ka.music.lyrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.proify.lyricon.lyric.model.RichLyricLine;
import io.github.proify.lyricon.lyric.model.Song;

/**
 * 在线歌词：搜索歌曲 → 拉取 LRC → 转成 Lyricon 的行模型。
 */
public final class LyricRepo {

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    private LyricRepo() {
    }

    private static final java.util.LinkedHashMap<String, Song> CACHE =
            new java.util.LinkedHashMap<String, Song>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Song> eldest) {
                    return size() > 32;
                }
            };

    public static Song fetch(String title, String artist, long duration) {
        android.util.Log.i("KAMLyrics", "fetch " + title);
        String key = title + "|" + artist + "|" + duration;
        synchronized (CACHE) {
            Song hit = CACHE.get(key);
            if (hit != null) {
                android.util.Log.i("KAMLyrics", "cache hit " + title);
                return hit;
            }
        }
        Song song = netease(title, artist, duration);
        if (song == null) {
            android.util.Log.i("KAMLyrics", "netease miss, try kugou");
            song = kugou(title, artist, duration);
        }
        if (song == null) {
            //偶尔会被接口限流，停两秒再整一轮
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
            }
            song = netease(title, artist, duration);
            if (song == null) {
                song = kugou(title, artist, duration);
            }
            android.util.Log.i("KAMLyrics", "retry got=" + (song != null) + " " + title);
        }
        if (song != null) {
            synchronized (CACHE) {
                CACHE.put(key, song);
            }
        }
        return song;
    }

    private static Song netease(String title, String artist, long duration) {
        long id = searchId(title, artist, duration);
        if (id <= 0) {
            android.util.Log.i("KAMLyrics", "netease no id for " + title);
            return null;
        }
        String lrc = fetchLrc(id);
        if (lrc == null || lrc.trim().isEmpty()) {
            android.util.Log.i("KAMLyrics", "netease no lrc for " + title + " id=" + id);
            return null;
        }
        List<RichLyricLine> lines = parseLrc(lrc, transMap(fetchTrans(id)));
        if (lines.isEmpty()) {
            android.util.Log.i("KAMLyrics", "netease parse empty for " + title + " id=" + id);
            return null;
        }
        Song song = new Song();
        song.setId(String.valueOf(id));
        song.setName(title);
        song.setArtist(artist);
        long total = duration;
        if (total <= 0) {
            total = lines.get(lines.size() - 1).getEnd();
        }
        song.setDuration(total);
        song.setLyrics(lines);
        int tc = 0;
        for (RichLyricLine l : lines) {
            if (l.getTranslation() != null && !l.getTranslation().isEmpty()) {
                tc++;
            }
        }
        android.util.Log.i("KAMLyrics", "trans lines=" + tc + "/" + lines.size());
        return song;
    }

    /** 网易云没有的冷门歌、同人曲，拿酷狗兜底。 */
    private static Song kugou(String title, String artist, long duration) {
        try {
            String kw = title + (artist == null || artist.isEmpty() ? "" : " " + artist);
            JSONArray info = kgSearch(kw);
            if (info == null || info.length() == 0) {
                info = kgSearch(title);
            }
            if (info == null || info.length() == 0) {
                android.util.Log.i("KAMLyrics", "kugou miss: no song");
                return null;
            }
            String bestHash = null;
            long bestAid = 0;
            int bestScore = -1;
            for (int i = 0; i < info.length(); i++) {
                JSONObject s = info.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                String name = s.optString("songname", "");
                String singer = s.optString("singername", "");
                long dur = s.optLong("duration", 0L) * 1000L;
                int score = 0;
                if (name.equalsIgnoreCase(title)) {
                    score += 3;
                } else if (name.toLowerCase().contains(title.toLowerCase())
                        || title.toLowerCase().contains(name.toLowerCase())) {
                    score += 1;
                }
                if (artist != null && !artist.isEmpty() && !singer.isEmpty()
                        && (singer.contains(artist) || artist.contains(singer))) {
                    score += 3;
                }
                if (dur > 0 && duration > 0) {
                    long diff = Math.abs(dur - duration);
                    if (diff < 3000) {
                        score += 2;
                    } else if (diff < 8000) {
                        score += 1;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestHash = s.optString("hash", "");
                    bestAid = s.optLong("album_audio_id", 0L);
                }
            }
            if (bestHash == null || bestHash.isEmpty()) {
                return null;
            }
            String u2 = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash="
                    + bestHash + "&album_audio_id=" + bestAid;
            JSONArray cands = new JSONObject(httpGet(u2)).optJSONArray("candidates");
            if (cands == null || cands.length() == 0) {
                return null;
            }
            String lid = null;
            String accesskey = null;
            long bestDiff = Long.MAX_VALUE;
            for (int i = 0; i < cands.length(); i++) {
                JSONObject c = cands.optJSONObject(i);
                if (c == null) {
                    continue;
                }
                long d = c.optLong("duration", 0L);
                if (d > 0 && d < 1000L) {
                    d *= 1000L;
                }
                long diff = (duration > 0 && d > 0) ? Math.abs(d - duration) : 0L;
                if (lid == null || diff < bestDiff) {
                    bestDiff = diff;
                    lid = c.optString("id", "");
                    accesskey = c.optString("accesskey", "");
                }
            }
            if (lid == null || lid.isEmpty()) {
                return null;
            }
            String u3 = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + lid
                    + "&accesskey=" + accesskey + "&fmt=lrc&charset=utf8";
            String content = new JSONObject(httpGet(u3)).optString("content", "");
            if (content.isEmpty()) {
                return null;
            }
            String lrc = new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT),
                    StandardCharsets.UTF_8);
            List<RichLyricLine> lines = parseLrc(lrc, null);
            if (lines.isEmpty()) {
                return null;
            }
            Song song = new Song();
            song.setId("kg:" + lid);
            song.setName(title);
            song.setArtist(artist);
            song.setDuration(duration > 0 ? duration : lines.get(lines.size() - 1).getEnd());
            song.setLyrics(lines);
            android.util.Log.i("KAMLyrics", "kugou hit lines=" + lines.size());
            return song;
        } catch (Throwable t) {
            android.util.Log.i("KAMLyrics", "kugou failed " + t);
            return null;
        }
    }

    private static JSONArray kgSearch(String keyword) {
        try {
            String url = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&page=1&pagesize=8&keyword="
                    + URLEncoder.encode(keyword, "UTF-8");
            JSONObject data = new JSONObject(httpGet(url)).optJSONObject("data");
            return data == null ? null : data.optJSONArray("info");
        } catch (Throwable t) {
            android.util.Log.i("KAMLyrics", "kugou search err " + t);
            return null;
        }
    }

    private static JSONArray searchRaw(String path, String keyword) {
        try {
            String url = "https://music.163.com" + path + "?type=1&offset=0&limit=8&s="
                    + URLEncoder.encode(keyword, "UTF-8");
            String body = httpGet(url);
            JSONObject root = new JSONObject(body);
            JSONObject result = root.optJSONObject("result");
            if (result == null) {
                android.util.Log.i("KAMLyrics", "search no result " + path + " body="
                        + (body.length() > 90 ? body.substring(0, 90) : body));
                return null;
            }
            return result.optJSONArray("songs");
        } catch (Throwable t) {
            android.util.Log.i("KAMLyrics", "search err " + path + " " + t);
            return null;
        }
    }

    private static long searchId(String title, String artist, long duration) {
        try {
            String keyword = title + (artist == null || artist.isEmpty() ? "" : " " + artist);
            // 连续快速切歌时 /web 这个接口经常返回空，换回不带 /web 的老接口，空了再补一次
            JSONArray songs = searchRaw("/api/search/get", keyword);
            if (songs == null || songs.length() == 0) {
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException ignored) {
                }
                songs = searchRaw("/api/search/get/web", keyword);
            }
            if (songs == null || songs.length() == 0) {
                return -1;
            }
            long bestId = -1;
            int bestScore = -1;
            for (int i = 0; i < songs.length(); i++) {
                JSONObject s = songs.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                String name = s.optString("name", "");
                String singer = "";
                JSONArray arr = s.optJSONArray("artists");
                if (arr != null && arr.length() > 0) {
                    JSONObject a0 = arr.optJSONObject(0);
                    if (a0 != null) {
                        singer = a0.optString("name", "");
                    }
                }
                long dur = s.optLong("duration", 0L);
                int score = 0;
                if (name.equals(title)) {
                    score += 3;
                } else if (name.contains(title) || title.contains(name)) {
                    score += 1;
                }
                if (artist != null && !artist.isEmpty() && !singer.isEmpty()) {
                    if (singer.contains(artist) || artist.contains(singer)) {
                        score += 3;
                    }
                }
                if (dur > 0 && duration > 0) {
                    long diff = Math.abs(dur - duration);
                    if (diff < 3000) {
                        score += 2;
                    } else if (diff < 8000) {
                        score += 1;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestId = s.optLong("id", -1L);
                }
            }
            return bestId;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String fetchLrc(long id) {
        try {
            String url = "https://music.163.com/api/song/lyric?id=" + id + "&lv=-1&kv=-1&tv=-1";
            JSONObject root = new JSONObject(httpGet(url));
            JSONObject lrc = root.optJSONObject("lrc");
            if (lrc == null) {
                return null;
            }
            return lrc.optString("lyric", null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 取网易云的翻译歌词（tlyric）。 */
    private static String fetchTrans(long id) {
        try {
            String url = "https://music.163.com/api/song/lyric?id=" + id + "&lv=-1&kv=-1&tv=-1";
            JSONObject root = new JSONObject(httpGet(url));
            JSONObject tr = root.optJSONObject("tlyric");
            if (tr == null) {
                return null;
            }
            String s = tr.optString("lyric", null);
            return (s == null || s.trim().isEmpty()) ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把翻译 LRC 转成 时间(毫秒) -> 翻译文本 的映射。 */
    private static java.util.Map<Long, String> transMap(String raw) {
        java.util.Map<Long, String> map = new java.util.HashMap<Long, String>();
        if (raw == null || raw.trim().isEmpty()) {
            return map;
        }
        for (String line : raw.split("\n")) {
            String s = line == null ? "" : line.trim();
            if (s.isEmpty()) {
                continue;
            }
            Matcher m = TIME_TAG.matcher(s);
            if (!m.find()) {
                continue;
            }
            String text = s.substring(m.end()).trim();
            if (text.isEmpty()) {
                continue;
            }
            map.put(msOf(m), text);
        }
        return map;
    }

    private static long msOf(Matcher m) {
        int min = Integer.parseInt(m.group(1));
        int sec = Integer.parseInt(m.group(2));
        int frac = 0;
        String f = m.group(3);
        if (f != null) {
            if (f.length() == 1) {
                frac = Integer.parseInt(f) * 100;
            } else if (f.length() == 2) {
                frac = Integer.parseInt(f) * 10;
            } else {
                frac = Integer.parseInt(f.substring(0, 3));
            }
        }
        return min * 60000L + sec * 1000L + frac;
    }

    private static final class Row {
        long begin;
        String text;
    }

    private static List<RichLyricLine> parseLrc(String raw, java.util.Map<Long, String> trans) {
        List<Row> rows = new ArrayList<>();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            String s = line == null ? "" : line.trim();
            if (s.isEmpty()) {
                continue;
            }
            Matcher m = TIME_TAG.matcher(s);
            if (!m.find()) {
                continue;
            }
            int min = Integer.parseInt(m.group(1));
            int sec = Integer.parseInt(m.group(2));
            int frac = 0;
            String f = m.group(3);
            if (f != null) {
                if (f.length() == 1) {
                    frac = Integer.parseInt(f) * 100;
                } else if (f.length() == 2) {
                    frac = Integer.parseInt(f) * 10;
                } else {
                    frac = Integer.parseInt(f.substring(0, 3));
                }
            }
            String text = s.substring(m.end()).trim();
            if (text.isEmpty()) {
                continue;
            }
            Row row = new Row();
            row.begin = min * 60000L + sec * 1000L + frac;
            row.text = text;
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return Long.compare(a.begin, b.begin);
            }
        });
        List<RichLyricLine> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            long end = (i + 1 < rows.size()) ? rows.get(i + 1).begin : row.begin + 8000L;
            if (end <= row.begin) {
                end = row.begin + 2000L;
            }
            String tr = (trans == null) ? null : trans.get(Long.valueOf(row.begin));
            out.add(new RichLyricLine(row.begin, end, end - row.begin, false,
                    null, row.text, null, null, null, tr, null, null));
        }
        return out;
    }

    private static javax.net.ssl.SSLSocketFactory sTrustAll;

    private static javax.net.ssl.SSLSocketFactory trustAll() {
        if (sTrustAll != null) {
            return sTrustAll;
        }
        try {
            javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }
                    }
            };
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, tm, new java.security.SecureRandom());
            sTrustAll = ctx.getSocketFactory();
        } catch (Throwable ignored) {
        }
        return sTrustAll;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.HttpsURLConnection https = (javax.net.ssl.HttpsURLConnection) conn;
                javax.net.ssl.SSLSocketFactory f = trustAll();
                if (f != null) {
                    https.setSSLSocketFactory(f);
                }
                https.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String host, javax.net.ssl.SSLSession session) {
                        return true;
                    }
                });
            }
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            conn.setRequestProperty("Accept", "*/*");
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            in.close();
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}