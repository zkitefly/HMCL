/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.io;

import org.jackhuang.hmcl.util.Pair;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.*;

/**
 * @author huangyuhui
 */
public final class NetworkUtils {
    public static final String PARAMETER_SEPARATOR = "&";
    public static final String NAME_VALUE_SEPARATOR = "=";
    private static final int TIME_OUT = 8000;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIME_OUT))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()))
            .build();

    private NetworkUtils() {
    }

    public static String withQuery(String baseUrl, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = true;
        for (Entry<String, String> param : params.entrySet()) {
            if (param.getValue() == null)
                continue;
            if (first) {
                if (!baseUrl.isEmpty()) {
                    sb.append('?');
                }
                first = false;
            } else {
                sb.append(PARAMETER_SEPARATOR);
            }
            sb.append(encodeURL(param.getKey()));
            sb.append(NAME_VALUE_SEPARATOR);
            sb.append(encodeURL(param.getValue()));
        }
        return sb.toString();
    }

    public static List<Pair<String, String>> parseQuery(URI uri) {
        return parseQuery(uri.getRawQuery());
    }

    public static List<Pair<String, String>> parseQuery(String queryParameterString) {
        if (queryParameterString == null) return Collections.emptyList();

        List<Pair<String, String>> result = new ArrayList<>();

        try (Scanner scanner = new Scanner(queryParameterString)) {
            scanner.useDelimiter("&");
            while (scanner.hasNext()) {
                String[] nameValue = scanner.next().split(NAME_VALUE_SEPARATOR);
                if (nameValue.length <= 0 || nameValue.length > 2) {
                    throw new IllegalArgumentException("bad query string");
                }

                String name = decodeURL(nameValue[0]);
                String value = nameValue.length == 2 ? decodeURL(nameValue[1]) : null;
                result.add(pair(name, value));
            }
        }
        return result;
    }

    public static URLConnection createConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(TIME_OUT);
        connection.setReadTimeout(TIME_OUT);
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
        return connection;
    }

    /**
     * @see <a href=
     *      "https://github.com/curl/curl/blob/3f7b1bb89f92c13e69ee51b710ac54f775aab320/lib/transfer.c#L1427-L1461">Curl</a>
     * @param location the url to be URL encoded
     * @return encoded URL
     */
    public static String encodeLocation(String location) {
        StringBuilder sb = new StringBuilder();
        boolean left = true;
        for (char ch : location.toCharArray()) {
            switch (ch) {
                case ' ':
                    if (left)
                        sb.append("%20");
                    else
                        sb.append('+');
                    break;
                case '?':
                    left = false;
                    // fallthrough
                default:
                    if (ch >= 0x80)
                        sb.append(encodeURL(Character.toString(ch)));
                    else
                        sb.append(ch);
                    break;
            }
        }

        return sb.toString();
    }

    public static HttpURLConnection resolveConnection(HttpURLConnection conn) throws IOException {
        return resolveConnection(conn, null);
    }

    /**
     * This method is a work-around that aims to solve problem when "Location" in
     * stupid server's response is not encoded.
     *
     * @see <a href="https://github.com/curl/curl/issues/473">Issue with libcurl</a>
     * @param conn the stupid http connection.
     * @return manually redirected http connection.
     * @throws IOException if an I/O error occurs.
     */
    public static HttpURLConnection resolveConnection(HttpURLConnection conn, List<String> redirects) throws IOException {
        int redirect = 0;
        while (true) {
            conn.setUseCaches(false);
            conn.setConnectTimeout(TIME_OUT);
            conn.setReadTimeout(TIME_OUT);
            conn.setInstanceFollowRedirects(false);
            Map<String, List<String>> properties = conn.getRequestProperties();
            String method = conn.getRequestMethod();
            int code = conn.getResponseCode();
            if (code >= 300 && code <= 307 && code != 306 && code != 304) {
                String newURL = conn.getHeaderField("Location");
                conn.disconnect();

                if (redirects != null) {
                    redirects.add(newURL);
                }
                if (redirect > 20) {
                    throw new IOException("Too much redirects");
                }

                HttpURLConnection redirected = (HttpURLConnection) new URL(conn.getURL(), encodeLocation(newURL))
                        .openConnection();
                properties
                        .forEach((key, value) -> value.forEach(element -> redirected.addRequestProperty(key, element)));
                redirected.setRequestMethod(method);
                conn = redirected;
                ++redirect;
            } else {
                break;
            }
        }
        return conn;
    }

    public static String doGet(URL url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .timeout(Duration.ofMillis(TIME_OUT))
                    .GET()
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            
            return response.body();
        } catch (InterruptedException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public static String doGet(List<URL> urls) throws IOException {
        List<IOException> exceptions = null;
        for (URL url : urls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(url.toURI())
                        .timeout(Duration.ofMillis(TIME_OUT))
                        .GET()
                        .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                
                return response.body();
            } catch (IOException | URISyntaxException | InterruptedException e) {
                if (exceptions == null) {
                    exceptions = new ArrayList<>(1);
                }
                exceptions.add(new IOException(e));
            }
        }

        if (exceptions == null) {
            throw new IOException("No candidate URL");
        } else if (exceptions.size() == 1) {
            throw exceptions.get(0);
        } else {
            IOException exception = new IOException("Failed to doGet");
            for (IOException e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    public static String doPost(URL u, Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet())
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            sb.deleteCharAt(sb.length() - 1);
        }
        return doPost(u, sb.toString());
    }

    public static String doPost(URL u, String post) throws IOException {
        return doPost(u, post, "application/x-www-form-urlencoded");
    }

    public static String doPost(URL url, String post, String contentType) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .timeout(Duration.ofMillis(TIME_OUT))
                    .header("Content-Type", contentType + "; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(post, UTF_8))
                    .build();
                    
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
                    
            return response.body();
        } catch (InterruptedException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public static String detectFileName(URL url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .timeout(Duration.ofMillis(TIME_OUT))
                    .GET()
                    .build();
                    
            HttpResponse<Void> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.discarding());
                    
            int code = response.statusCode();
            if (code / 100 == 4)
                throw new FileNotFoundException();
            if (code / 100 != 2)
                throw new IOException(url + ": response code " + code);

            Optional<String> disposition = response.headers()
                    .firstValue("Content-Disposition");
                    
            if (disposition.isEmpty() || !disposition.get().contains("filename=")) {
                String u = url.toString();
                return decodeURL(substringAfterLast(u, '/'));
            } else {
                return decodeURL(removeSurrounding(
                    substringAfter(disposition.get(), "filename="), "\""));
            }
        } catch (InterruptedException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public static URL toURL(String str) {
        try {
            return new URL(str);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean isURL(String str) {
        try {
            new URL(str);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static boolean urlExists(URL url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI()) 
                    .timeout(Duration.ofMillis(TIME_OUT))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
                    
            HttpResponse<Void> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.discarding());
                    
            return response.statusCode() / 100 == 2;
        } catch (InterruptedException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    // ==== Shortcut methods for encoding/decoding URLs in UTF-8 ====
    public static String encodeURL(String toEncode) {
        try {
            return URLEncoder.encode(toEncode, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error();
        }
    }

    public static String decodeURL(String toDecode) {
        try {
            return URLDecoder.decode(toDecode, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error();
        }
    }
    // ====
    
    // 保留这些方法以保持兼容性
    public static String readData(HttpURLConnection con) throws IOException {
        // 为保持兼容性转换为 HttpClient 调用
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(con.getURL().toURI())
                    .timeout(Duration.ofMillis(TIME_OUT))
                    .method(con.getRequestMethod(), 
                           con.getDoOutput() ? HttpRequest.BodyPublishers.noBody() 
                                           : HttpRequest.BodyPublishers.noBody())
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            
            return response.body();
        } catch (URISyntaxException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static HttpURLConnection createHttpConnection(URL url) throws IOException {
        return (HttpURLConnection) createConnection(url);
    }

    // 新增下载专用的连接池配置
    private static final HttpClient DOWNLOAD_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIME_OUT))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newFixedThreadPool(
                Math.max(Runtime.getRuntime().availableProcessors() * 2, 8)))
            .build();

    // 新增用于下载的工具方法
    public static CompletableFuture<Path> downloadAsync(URL url, Path target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection conn = createHttpConnection(url);
                conn = resolveConnection(conn);
                
                if (conn.getResponseCode() / 100 != 2) {
                    throw new IOException("HTTP " + conn.getResponseCode());
                }

                try (InputStream is = conn.getInputStream()) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                
                return target;
            } catch (Exception e) {
                throw new RuntimeException(e); // 替换 CompletionException 为 RuntimeException
            }
        });
    }

    // 用于批量下载的工具方法
    public static List<CompletableFuture<Path>> downloadAllAsync(
            List<Pair<URL, Path>> downloads) {
        return downloads.stream()
                .map(pair -> downloadAsync(pair.getKey(), pair.getValue()))
                .collect(Collectors.toList());
    }
}
