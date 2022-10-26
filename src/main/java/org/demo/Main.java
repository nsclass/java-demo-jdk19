package org.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Date ${DATE}
 *
 * @author Nam Seob Seo
 */

public class Main {

    sealed interface IResult<T> {
        record Success<T>(T data) implements IResult<T> {}
        record Failed<T>(Exception exception) implements IResult<T> {}
    }

    record URLData(URL url, byte[] response, long durationMs) { }

    IResult<URLData> fromFuture(Future<IResult<URLData>> future) {
        try {
            IResult<URLData> result = future.get();
            return switch(result) {
                case IResult.Success<URLData> s -> s;
                case IResult.Failed<URLData> f -> {
                    System.out.println("failed: %s".formatted(f.exception.getMessage()));
                    yield f;
                }
            };
        } catch (InterruptedException | ExecutionException e) {
            return new IResult.Failed<>(e);
        }
    }

    IResult<URLData> fetchUrlData(URL url) {
        try (InputStream in = url.openStream()) {
            try {
                Instant start = Instant.now();
                System.out.println("started: %s".formatted(url.toString()));
                var bytes = in.readAllBytes();
                var duration = Duration.between(start, Instant.now()).toMillis();
                System.out.println("finished: %s(%dms)".formatted(url.toString(), duration));
                return new IResult.Success<>(new URLData(url, bytes, duration));
            } catch (Exception e) {
                return new IResult.Failed<>(e);
            }
        } catch (IOException e) {
            return new IResult.Failed<>(e);
        }
    }
    List<URLData> retrieveURLs(List<URL> urls) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = urls
                    .stream()
                    .map(url -> executor.submit(() -> fetchUrlData(url)))
                    .toList();
            System.out.println("%d tasks have been created".formatted(tasks.size()));
            return tasks.stream().map(this::fromFuture)
                    .map(s -> s instanceof IResult.Success<URLData>(URLData d) ? d : null)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public static void main(String[] args) {
        List<URL> urls = Stream.of(
                    "https://www.google.com",
                        "https://www.youtube.com",
                        "https://www.yahoo.com",
                        "https://www.github.com",
                        "https://www.linkedin.com",
                        "https://www.amazon.com",
                        "https://www.bing.com",
                        "https://www.reddit.com",
                        "https://www.facebook.com",
                        "https://www.ebay.com",
                        "https://www.twitter.com",
                        "https://www.cloudflare.com",
                        "https://www.datadoghq.com")
                .map(url -> {
                    try {
                        return new URL(url);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();

        List<URLData> result = new Main().retrieveURLs(urls);
        result.stream()
            .sorted(Comparator.comparingLong(x -> x.durationMs))
            .forEach(data ->
                System.out.printf("""
                    %s, %d(ms) %d(KB)
                    """, data.url,
                    data.durationMs,
                    data.response.length / 1024));

    }
}