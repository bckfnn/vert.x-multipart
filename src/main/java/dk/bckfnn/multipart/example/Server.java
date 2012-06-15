package dk.bckfnn.multipart.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.deploy.Verticle;

import dk.bckfnn.multipart.BaseReadStream;
import dk.bckfnn.multipart.MultipartHandler;
import dk.bckfnn.multipart.MultipartHandler.FieldInfo;

public class Server extends Verticle {
    @Override
    public void start() throws Exception {
        //final Logger log = container.getLogger();

        HttpServer server = vertx.createHttpServer();

        server.requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest request) {
                System.out.println("A request has arrived on the server! " + request.path);
                if (request.path.equals("/form.html")) {
                    request.response.sendFile("form.html");
                    return;
                }
                if (request.path.equals("/submit")) {
                    MultipartHandler multipartHandler = new MultipartHandler(request);
                    multipartHandler.setFileHandler(new Handler<FieldInfo>() {
                        @Override
                        public void handle(final FieldInfo field) {
                            ReadStream md5 = new Md5Handler(field);
                            md5.dataHandler(new Handler<Buffer>() {
                                @Override
                                public void handle(Buffer buf) {
                                    System.out.println("md5:" + field.getName() + " " + buf);
                                }
                            });
                        }
                    });
                    multipartHandler.endHandler(new Handler<Void>() {
                        @Override
                        public void handle(Void arg0) {
                            System.out.println("form processed");
                            request.response.end("form processed");
                        }
                    });
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> header : request.headers().entrySet()) {
                    sb.append(header.getKey()).append(": ").append(header.getValue()).append("\n");
                }
                request.response.putHeader("Content-Type", "text/plain");
                request.response.end(sb.toString());
            }
        });

        server.listen(8080, "localhost");
    }

    public static class Md5Handler extends BaseReadStream {
        MessageDigest m;

        Md5Handler(ReadStream input) {
            super(input);
            try {
                m = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException exc) {
                throw new RuntimeException(exc);
            }
        }

        @Override
        protected void handleData(Buffer buf) {
            m.update(buf.getBytes());
        }

        protected void handleEnd() {
            super.handleData(new Buffer(m.digest()));
            super.handleEnd();
        }
    }
}
