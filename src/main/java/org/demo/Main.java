package org.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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

    record URLData (URL url, byte[] response) { }

    List<URLData> retrieveURLs(List<URL> urls) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = urls
                    .stream()
                    .map(url -> executor.submit(() -> fetchUrlData(url)))
                    .toList();
            return tasks.stream().map(this::fromFuture)
                    .map(s -> s instanceof IResult.Success<URLData> d ? d.data : null)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    IResult<URLData> fromFuture(Future<IResult<URLData>> future) {
        try {
            IResult<URLData> result = future.get();
            return switch(result) {
                case IResult.Success<URLData> s -> s;
                case IResult.Failed<URLData> f -> {
                    System.out.println("failed %s".formatted(f.exception.getMessage()));
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
                return new IResult.Success<>(new URLData(url, in.readAllBytes()));
            } catch (Exception e) {
                return new IResult.Failed<>(e);
            }
        } catch (IOException e) {
            return new IResult.Failed<>(e);
        }
    }
    public static void main(String[] args) throws Exception {
        List<URL> urls = Stream.of(
                    "https://www.google.com",
                    "https://www.yahoo.com",
                    "https://www.youtube.com")
                .map(url -> {
                    try {
                        return new URL(url);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();

        List<URLData> result = new Main().retrieveURLs(urls);
        result.forEach(data -> System.out.printf("""
                url: %s,
                data: %d
                %n""", data.url, data.response.length));

    }
}