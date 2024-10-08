package org.kirill.space.cell.crpt_api.coding.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public final class Http2 {

    private static void usage() {
        System.err.println("Usage: HTTPCheck <URL> <mode>");
        System.err.println("       mode: 'sync', 'async' or 'push'. Defaults to 'sync'");
        System.exit(1);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException {
        if (args.length < 1) {
            usage();
        }

        var location = args[0];

        var mode = "sync";
        if (args.length > 1) {
            mode = args[1];
        }

        try {
            // To test H2 push, use the local server from:
            //   https://github.com/GoogleChromeLabs/simplehttp2server
            //
            // Because it runs on localhost we need some configuration on
            // HttpClient to trust it.

            var trustEveryone = new TrustManager[] { new TrustEveryone() };
            var sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustEveryone, new SecureRandom());

            var uri = new URI(location);
            var req = HttpRequest.newBuilder(uri).build();

            var client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

            if (mode.equals("sync")) {
                var response = client.send(req,
                        HttpResponse.BodyHandlers.ofString(Charset.defaultCharset()));

                System.out.println(location + ": " + response.version());
            } else if (mode.equals("async")) {
                var handler = HttpResponse.BodyHandlers.ofString();
                CompletableFuture.allOf(
                        client.sendAsync(req, handler)
                                .thenAccept((resp) -> System.out.println("First")),
                        client.sendAsync(req, handler)
                                .thenAccept((resp) -> System.out.println("Second")),
                        client.sendAsync(req, handler)
                                .thenAccept((resp) -> System.out.println("Third"))
                ).join();

            } else if (mode.equals("push")) {
                var futures = new ArrayList<CompletableFuture>();
                HttpResponse.PushPromiseHandler<String> pushPromiseHandler =
                        (initiatingRequest, pushPromiseRequest, acceptor) -> {
                            futures.add(
                                    acceptor.apply(HttpResponse.BodyHandlers.ofString())
                                            .thenAccept(response -> {
                                                System.out.println(" Pushed response: " + response.uri());
                                            }));
                        };

                futures.add(client.sendAsync(req,
                                HttpResponse.BodyHandlers.ofString(),
                                pushPromiseHandler)
                        .thenAccept((response) -> {
                            System.out.println(location + ": " + response.version());
                        }));

                // Hang out to see whether the server sends us any pushes...
                Thread.sleep(1000);

                // Wait until we're done
                CompletableFuture.allOf(futures.stream()
                                .toArray(CompletableFuture<?>[]::new))
                        .join();
            }
        } catch (URISyntaxException urix) {
            System.err.println("Location: "+ location +" is not valid");
            usage();
        } catch (InterruptedException intx) {
            System.err.println("Contacting "+ location +" interrupted.");
        } catch (IOException iox) {
            System.err.println("Contacting "+ location +" failed:");
            iox.printStackTrace();
        }
    }

    static class TrustEveryone implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
