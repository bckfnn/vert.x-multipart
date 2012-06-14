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

import java.util.Arrays;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;

/**
 * An ReadStream that will Base64 decode the input and pass the decoded binary
 * to the ReadStream pass in the constructor.
 */
public class Base64DecodeHandler extends BaseReadStream {
    final static char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    final static int[] toint = new int[255];

    static {
        Arrays.fill(toint, -2);
        for (int i = 0; i < alphabet.length; i++) {
            toint[alphabet[i]] = i;
        }
        toint['='] = -1;
        toint['\r'] = -1;
        toint['\n'] = -1;
    }

    private int start;
    private Buffer buffer;

    /**
     * Constructor. Pass in the output ReadStream by calling input(ReadStream).
     */
    public Base64DecodeHandler() {
        super();
    }

    /**
     * Constructor. Send the output the readStream.
     * 
     * @param readStream the output ReadStream.
     */
    public Base64DecodeHandler(ReadStream readStream) {
        super(readStream);
    }

    @Override
    protected void handleData(Buffer buf) {
        if (buffer == null) {
            buffer = buf;
        } else {
            buffer.appendBuffer(buf);
        }

        int len = buffer.length();
        Buffer out = new Buffer(len * 3 / 4 + 1);
        while (len - start >= 4) {
            int v0 = buffer.getByte(start++);
            int n0 = toint[v0];
            if (n0 == -1) {
                continue;
            }
            if (n0 == -2) {
                handleException(new RuntimeException("Illegal base64 char " + (char) v0));
                return;
            }

            int v1 = buffer.getByte(start++);
            int n1 = toint[v1];
            if (n1 == -2) {
                handleException(new RuntimeException("Illegal base64 char " + (char) v1));
                return;
            }

            int v2 = buffer.getByte(start++);
            int n2 = toint[v2];
            if (n2 == -2) {
                handleException(new RuntimeException("Illegal base64 char " + (char) v2));
                return;
            }

            int v3 = buffer.getByte(start++);
            int n3 = toint[v3];
            if (n3 == -2) {
                handleException(new RuntimeException("Illegal base64 char " + (char) v3));
                return;
            }

            out.appendByte((byte) (n0 << 2 | n1 >> 4));
            if (v2 == '=' && v3 == '=') {
                break;
            }
            out.appendByte((byte) (n1 << 4 | n2 >> 2));
            if (v3 == '=') {
                break;
            }
            out.appendByte((byte) (n2 << 6 | n3));
        }
        if (start < len) {
            buffer = buffer.getBuffer(start, len);
        } else {
            buffer = null;
        }
        start = 0;
        super.handleData(out);
    }

    @Override
    protected void handleEnd() {
        if (buffer != null) {
            int len = buffer.length();

            while (start < len) {
                int v0 = buffer.getByte(start++);
                int n0 = toint[v0];
                if (n0 != -1) {
                    handleException(new RuntimeException("Illegal trailing base64 data"));
                    break;
                }
            }
        }
        // TODO Auto-generated method stub
        super.handleEnd();
    }

}
