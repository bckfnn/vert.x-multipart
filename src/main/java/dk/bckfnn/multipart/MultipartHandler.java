/*
 * Copyright 2011-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.bckfnn.multipart;

import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.streams.ReadStream;

public class MultipartHandler extends BaseReadStream {
    final static byte[] CRLF = new byte[] { '\r', '\n' };
    final static byte[] MINUSMINUS = new byte[] { '-', '-' };

    private RecordParser parser = new RecordParser();
    private Part currentPart = null;

    private String header;
    private Handler<FieldInfo> fileHandler;

    enum State {
        PREAMPLE,
        PREHEADERS,
        HEADERS,
        BODY,
        END,
    }

    State state = State.PREAMPLE;

    public MultipartHandler(HttpServerRequest request) {
        String contentType = request.headers().get("Content-Type");
        System.out.println(contentType);
        Header h = new Header("content-type:" + contentType);
        System.out.println(h.value);
        if (h.value.equals("multipart/form-data")) {
            input(request);
            currentPart = new Part(null);
            currentPart.boundary = h.params.get("boundary").getBytes();
        } else {
            throw new RuntimeException("request is not multipart/form-data");
        }
    }

    public MultipartHandler(final byte[] boundary, ReadStream inputStream) {
        super(inputStream);
        currentPart = new Part(null);
        currentPart.boundary(boundary);
    }

    public MultipartHandler fieldHandler(Handler<FieldInfo> fileHandler) {
        this.fileHandler = fileHandler;
        return this;
    }

    private boolean parse() {
        Buffer b;

        switch (state) {
        case PREAMPLE:
            b = parser.parseDelimited(currentPart.boundary, 4096);
            if (b == null) {
                return false;
            }
            if (!parser.delimiterFound) {
                handleException(new RuntimeException("Preamble too long"));
            }
            state = State.PREHEADERS;
            break;
        case PREHEADERS:
            b = parser.parseDelimited(CRLF, 4096);
            if (b == null) {
                return false;
            }
            if (!parser.delimiterFound) {
                handleException(new RuntimeException("Trailing data after delimiter"));
                break;
            }
            if (b.length() == 2 && b.getByte(0) == '-' && b.getByte(1) == '-') {
                state = State.END;
            } else if (b.length() == 0) {
                currentPart.headers.clear();
                state = State.HEADERS;
            } else {
                handleException(new RuntimeException("Corrupt multipart"));
            }
            break;
        case HEADERS:
            b = parser.parseDelimited(CRLF, 4096);
            if (b == null) {
                return false;
            }
            if (!parser.delimiterFound) {
                handleException(new RuntimeException("Header line too long"));
            }
            if (b.length() > 0 && (b.getByte(0) == ' ' || b.getByte(0) == '\t')) {
                if (header == null) {
                    handleException(new RuntimeException("Illegal continuation header " + b.toString()));
                }
                header = header + b.toString();
                return true;
            }
            if (b.length() == 0) {
                currentPart.addHeader(header);
                header = null;

                Header contentType = currentPart.headers.get("content-type");
                if (contentType != null && contentType.value.equals("multipart/mixed")) {
                    currentPart = new Part(currentPart);
                    currentPart.boundary(contentType.params.get("boundary").getBytes());
                    state = State.PREAMPLE;
                } else {
                    makeFile();
                    Header contentTransferEncoding = currentPart.headers.get("content-transfer-encoding");
                    if (contentTransferEncoding != null && contentTransferEncoding.value.equals("base64")) {
                        Base64DecodeHandler b64 = new Base64DecodeHandler();
                        b64.dataHandler(currentPart.dataHandler);
                        b64.endHandler(currentPart.endHandler);
                        b64.exceptionHandler(currentPart.exceptionHandler);
                        b64.input(currentPart);
                    }

                    state = State.BODY;
                }
            } else if (b.getByte(0) == ' ' || b.getByte(0) == '\t') {
                header = header + b.toString();
            } else {
                currentPart.addHeader(header);
                header = b.toString();
            }
            break;
        case BODY:
            b = parser.parseDelimited(currentPart.bodyBoundary(), 4096);
            if (b == null) {
                return false;
            }
            //System.out.println("file data: " + b);
            currentPart.handleData(b);

            if (parser.delimiterFound) {
                currentPart.handleEnd();
                state = State.PREHEADERS;
            }
            break;
        case END:
            if (currentPart.parent != null) {
                currentPart = currentPart.parent;
                state = State.PREAMPLE;
            } else {
                return false;
            }
            break;
        }
        return true;
    }

    private void makeFile() {
        fileHandler.handle(currentPart);
    }

    @Override
    public void handleData(Buffer buffer) {
        parser.add(buffer);
        while (parse() && !exception) {
            //System.out.println("State:" + state + " " + currentPart);
        }
    }

    @Override
    public void handleEnd() {
        if (state != State.END) {
            handleException(new RuntimeException("illegal end state"));
        } else {
            super.handleEnd();
        }
    }

    public interface FieldInfo extends ReadStream {
        public String getName();

        public String getFilename();

        public String getContentType();

        public Header getHeader(String headerName);
    }

    /**
     * A part current being parsed. The parts form a stack with a pointer to
     * the parent part.
     */
    class Part extends BaseReadStream implements FieldInfo {
        public Part(Part parent) {
            this.parent = parent;
        }

        private Map<String, Header> headers = new HashMap<>();
        private byte[] boundary;
        private byte[] bodyBoundary;
        private Part parent;

        /**
         * Set the boundary for remaining parts. Adds a -- in front of the
         * boundary.
         * 
         * @param boundary
         */
        void boundary(byte[] boundary) {
            this.boundary = new byte[boundary.length + 2];
            System.arraycopy(MINUSMINUS, 0, this.boundary, 0, 2);
            System.arraycopy(boundary, 0, this.boundary, 2, boundary.length);
        }

        /**
         * Return the end boundary for the file part. Add a CRNL in front of the
         * boundary
         * because the
         * 
         * @return
         */
        byte[] bodyBoundary() {
            if (bodyBoundary == null) {
                bodyBoundary = new byte[boundary.length + 2];
                System.arraycopy(CRLF, 0, bodyBoundary, 0, 2);
                System.arraycopy(boundary, 0, bodyBoundary, 2, boundary.length);
            }
            return bodyBoundary;
        }

        private void addHeader(String header) {
            if (header == null) {
                return;
            }

            Header h = new Header(header);

            Header old = headers.get(h.name);
            if (old != null) {
                old.value = "," + h.value;
            } else {
                headers.put(h.name, h);
            }
        }

        public String toString() {
            return new String(boundary) + " " + headers;
        }

        @Override
        public String getName() {
            Header contentDisposition = headers.get("content-disposition");
            if (contentDisposition != null) {
                return contentDisposition.params.get("name");
            }
            return null;
        }

        @Override
        public String getFilename() {
            Header contentDisposition = headers.get("content-disposition");
            if (contentDisposition != null) {
                return contentDisposition.params.get("filename");
            }
            return null;
        }

        @Override
        public String getContentType() {
            Header contentType = headers.get("content-type");
            if (contentType != null) {
                return contentType.value;
            }
            return null;
        }

        @Override
        public Header getHeader(String headerName) {
            return headers.get(headerName);
        }

        @Override
        public void pause() {
            MultipartHandler.this.pause();
        }

        @Override
        public void resume() {
            MultipartHandler.this.resume();
        }
    }

    /**
     * Utility class for parsing headers keys and values. It can parse and
     * return the tokens that makes up a header.
     */
    public static class Header {
        String name;
        String value;
        Map<String, String> params = new HashMap<>();

        String header;
        int pos;

        public Header(String header) {
            this.header = header;
            this.pos = 0;

            skipWs();
            name = getToken(':').toLowerCase();
            skip(':');

            skipWs();
            value = getValue(',', ';');
            skipWs();

            while (has(',', ';')) {
                skip(',', ';');
                skipWs();
                String paramName = getToken('=', ',', ';');
                String paramValue = null;
                skipWs();
                if (has('=')) {
                    skip('=');
                    skipWs();
                    paramValue = getValue(',', ';');
                }
                params.put(paramName, paramValue);
            }
        }

        public String getToken(char... sep) {
            int s = pos;
            for (int l = header.length(); pos < l; pos++) {
                char ch = header.charAt(pos);
                if (Character.isWhitespace(ch) || oneOf(ch, sep)) {
                    break;
                }
            }
            return header.substring(s, pos);
        }

        public String getValue(char... sep) {

            if (header.charAt(pos) == '"') {
                pos++;
                StringBuilder sb = new StringBuilder();
                for (int l = header.length(); pos < l; pos++) {
                    char ch = header.charAt(pos);
                    if (ch == '"') {
                        pos++;
                        return sb.toString();
                    }
                    if (ch == '\\') {
                        pos++;
                    }
                    sb.append(header.charAt(pos));
                }
                throw new RuntimeException("missing end quote in " + header);
            }
            return getToken(sep);
        }

        public boolean oneOf(char ch, char... sep) {
            for (int i = 0; i < sep.length; i++) {
                if (ch == sep[i]) {
                    return true;
                }
            }
            return false;
        }

        public void skip(char... ch) {
            if (oneOf(header.charAt(pos), ch)) {
                pos++;
            } else {
                throw new RuntimeException("Expected " + new String(ch) + " at pos " + pos + " in " + header);
            }
        }

        public void skipWs() {
            for (int l = header.length(); pos < l; pos++) {
                if (!Character.isWhitespace(header.charAt(pos))) {
                    break;
                }
            }
        }

        public boolean has(char... sep) {
            return pos < header.length() && oneOf(header.charAt(pos), sep);
        }

        public String remaining() {
            return header.substring(pos);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(value);
            sb.append(params);
            return sb.toString();
        }
    }

    /**
     * A simple copy of RecordParser, it only does delimited but allow for
     * sending output packages
     * when the size of the buffer reach the max.
     * 
     * the flag delimiterFound indicate if the package was a result of a
     * delimiter of a full
     * buffer.
     */
    public static class RecordParser {
        private Buffer buff;

        private int pos; // Current position in buffer
        private int start; // Position of beginning of current record
        private int delimPos; // Position of current match in delimiter array
        public boolean delimiterFound = false;

        public void add(Buffer buffer) {
            if (buff == null) {
                this.buff = buffer;
            } else {
                this.buff.appendBuffer(buffer);
            }
        }

        public Buffer parseDelimited(byte[] delim, int max) {
            int len = buff.length();
            for (; pos < len && pos - start < max; pos++) {
                if (buff.getByte(pos) == delim[delimPos]) {
                    delimPos++;
                    if (delimPos == delim.length) {
                        Buffer ret = buff.getBuffer(start, pos - delim.length + 1);
                        start = pos + 1;
                        delimPos = 0;
                        delimiterFound = true;
                        return ret;
                    }
                }
            }
            if (pos - start >= max) {
                delimiterFound = false;
                Buffer ret = buff.getBuffer(start, pos);
                start = pos;
                delimPos = 0;
                return ret;
            }
            return null;
        }
    }
}
