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

    public static Song fetch(String title, String artist, long duration) {
        long id = searchId(title, artist, duration);
        if (id <= 0) {
            return null;
        }
        String lrc = fetchLrc(id);
        if (lrc == null || lrc.trim().isEmpty()) {
            return null;
        }
        List<RichLyricLine> lines = parseLrc(lrc, transMap(fetchTrans(id)));
        if (lines.isEmpty()) {
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
        android.util.Log.d("KAMLyrics", "trans lines=" + tc + "/" + lines.size());
        return song;
    }

    private static long searchId(String title, String artist, long duration) {
        try {
            String keyword = title + (artist == null || artist.isEmpty() ? "" : " " + artist);
            String url = "https://music.163.com/api/search/get/web?type=1&offset=0&limit=8&s="
                    + URLEncoder.encode(keyword, "UTF-8");
            JSONObject root = new JSONObject(httpGet(url));
            JSONObject result = root.optJSONObject("result");
            if (result == null) {
                return -1;
            }
            JSONArray songs = result.optJSONArray("songs");
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

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
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