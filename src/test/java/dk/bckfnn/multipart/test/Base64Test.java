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
package dk.bckfnn.multipart.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

import dk.bckfnn.multipart.Base64DecodeHandler;

public class Base64Test {

    @Test
    public void testSimple() {
        String[] body = {
                "VGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZy4=",
        };
        test(body, new Buffer("The quick brown fox jumps over the lazy dog."), null);
    }

    @Test
    public void testTralingData() {
        String[] body = {
                "VGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZy4=XX",
        };
        test(body, new Buffer("The quick brown fox jumps over the lazy dog."), new RuntimeException("Illegal trailing base64 data"));
    }

    @Test
    public void testIllegaleBase64() {
        String[] body = {
                "V%GhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZy4=XX",
        };
        test(body, new Buffer(""), new RuntimeException("Illegal base64 char %"));
    }

    public void test(String[] body, Buffer expected, Exception exc) {
        final Buffer out = new Buffer();
        final List<Exception> exceptions = new ArrayList<>();

        Buffer buf = new Buffer();
        for (String l : body) {
            buf.appendString(l);
            buf.appendString("\r\n");
        }

        TestReadStream istr = new TestReadStream();
        final Base64DecodeHandler b64Handler = new Base64DecodeHandler(istr);

        b64Handler.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer buf) {
                out.appendBuffer(buf);
            }
        });
        b64Handler.endHandler(new Handler<Void>() {
            public void handle(Void arg) {
            }
        });

        b64Handler.exceptionHandler(new Handler<Exception>() {
            @Override
            public void handle(Exception exc) {
                exceptions.add(exc);
            }
        });

        istr.process(buf);

        //System.out.println(out);
        //System.out.println(exceptions);
        Assert.assertEquals(expected, out);
        if (exc != null) {
            Assert.assertEquals(1, exceptions.size());
            Assert.assertEquals(exc.toString(), exceptions.get(0).toString());
        }
    }
}
