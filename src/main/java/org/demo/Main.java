package org.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    List<URLData> retrieveURLs(URL... urls) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = Arrays.stream(urls)
                    .map(url -> executor.submit(() -> getURL(url)))
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
                case IResult.Failed<URLData> f -> f;
            };
        } catch (InterruptedException | ExecutionException e) {
            return new IResult.Failed<>(e);
        }
    }

    IResult<URLData> getURL(URL url) {
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

        List<URLData> result = new Main().retrieveURLs(new URL("https://www.google.com"));
        result.forEach(data -> System.out.printf("""
                url: %s,
                data: %s
                %n""", data.url, new String(data.response)));

    }
}