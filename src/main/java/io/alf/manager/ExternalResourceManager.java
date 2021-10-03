package io.alf.manager;

import com.google.gson.reflect.TypeToken;
import io.alf.App;
import io.alf.model.ResultWithHeaders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ExternalResourceManager {

    private final HttpClient httpClient =  HttpClient.newHttpClient();

    <T> ResultWithHeaders<T> get(String url, String apiKey, Long timeoutMilli, Class<T> clazz) {
        System.err.println("calling get url " + url);
        try {
            var reqB = HttpRequest.newBuilder().uri(URI.create(url))
                    .GET()
                    .header("Content-Type", "application/json")
                    .header("Authorization", "ApiKey " + apiKey);
            if (timeoutMilli != null) {
               reqB = reqB.timeout(Duration.ofMillis(timeoutMilli));
            }
            var req = reqB.build();
            System.err.println("calling send");
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.err.println("ending send");
            return new ResultWithHeaders<>(App.JSON.fromJson(res.body(), clazz), res.headers().map());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    <T> ResultWithHeaders<T> post(String url, String apiKey, Long timeoutMilli, Object payload, TypeToken<T> tt) {
        System.err.println("calling post url " + url);
        try {
            var reqB = HttpRequest.newBuilder().uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(App.JSON.toJson(payload), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "ApiKey " + apiKey);
            if (timeoutMilli != null) {
                reqB = reqB.timeout(Duration.ofMillis(timeoutMilli));
            }
            var req = reqB.build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ResultWithHeaders<>(App.JSON.fromJson(res.body(), tt.getType()), res.headers().map());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
