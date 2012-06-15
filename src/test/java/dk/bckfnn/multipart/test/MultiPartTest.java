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

import dk.bckfnn.multipart.MultipartHandler;
import dk.bckfnn.multipart.MultipartHandler.FieldInfo;
import dk.bckfnn.multipart.MultipartHandler.RecordParser;

public class MultiPartTest {
    static String longString;
    static {
        for (int i = 0; i < (4 * 1024); i++) {
            longString += Math.random();
        }
    }

    @Test
    public void testRecordParser() {
        RecordParser p;

        p = new RecordParser();
        p.add(new Buffer("12345abc"));
        Assert.assertEquals(new Buffer("12345"), p.parseDelimited("abc".getBytes(), 100));
        Assert.assertTrue(p.delimiterFound);

        p = new RecordParser();
        p.add(new Buffer("12345abc"));
        Assert.assertEquals(new Buffer("1234"), p.parseDelimited("abc".getBytes(), 4));
        Assert.assertFalse(p.delimiterFound);
        Assert.assertEquals(new Buffer("5"), p.parseDelimited("abc".getBytes(), 4));
        Assert.assertTrue(p.delimiterFound);

        p = new RecordParser();
        p.add(new Buffer("12345a"));
        Assert.assertEquals(null, p.parseDelimited("abc".getBytes(), 100));
        Assert.assertFalse(p.delimiterFound);
        p.add(new Buffer("bc"));
        Assert.assertEquals(new Buffer("12345"), p.parseDelimited("abc".getBytes(), 100));
        Assert.assertTrue(p.delimiterFound);

        p = new RecordParser();
        p.add(new Buffer("--boundary\r\n"));
        Assert.assertEquals(new Buffer(""), p.parseDelimited("--boundary".getBytes(), 100));
        Assert.assertTrue(p.delimiterFound);
        Assert.assertEquals(new Buffer(""), p.parseDelimited("\r\n".getBytes(), 100));
        Assert.assertTrue(p.delimiterFound);

    }

    @Test
    public void testSimple() {
        String body[] = new String[] {
                "--AaB03x",
                "Content-Disposition: form-data; name=\"submit-name\"",
                "",
                "Larry",
                "",
                "--AaB03x",
                "Content-Disposition: form-data; name=\"files\"; filename=\"file1.txt\"",
                "Content-Type: text/plain",
                "",
                "... contents of file1.txt ...",
                "--AaB03x--"
        };
        test("AaB03x", body,
                new FileInfo().name("submit-name").content("Larry\r\n"),
                new FileInfo().name("files").filename("file1.txt").content("... contents of file1.txt ...").contentType("text/plain"),
                "success");
    }

    @Test
    public void testPreamble() {
        // content before the first boundary
        String[] body = {
                "blerg",
                "--boundary",
                "",
                "blarggghhh",
                "--boundary--",
        };
        test("boundary", body,
                new FileInfo().content("blarggghhh"),
                "success");
    }

    @Test
    public void testLongPreamble() {
        // content before the first boundary
        String[] body = {
                longString,
                "--boundary",
                "",
                "blarggghhh",
                "--boundary--",
        };
        test("boundary", body,
                new RuntimeException("Preamble too long"));
    }

    @Test
    public void testLongHeader() {
        // test unreasonably long header.
        String[] body = {
                "--boundary",
                "content-type: " + longString,
                longString,
        };
        test("boundary", body,
                new RuntimeException("Header line too long"));
    }

    @Test
    public void testMissingCrAfterBoundary() {
        // test missing CRLF after the boundary.
        String[] body = {
                "--boundary" + longString,
        };
        test("boundary", body,
                new RuntimeException("Trailing data after delimiter"));
    }

    @Test
    public void testFirstHeaderContinuation() {
        String[] body = {
                "--boundary",
                "  fail: blahrg",
                "",
        };
        test("boundary", body,
                new RuntimeException("Illegal continuation header   fail: blahrg"));
    }

    @Test
    public void testBase64() {
        String[] body = {
                "--AaB03x",
                "content-disposition: form-data; name=\"reply\"",
                "",
                "yes",
                "--AaB03x",
                "content-disposition: form-data; name=\"fileupload\"; filename=\"hello world.txt\"",
                "Content-Type: image/jpeg",
                "Content-Transfer-Encoding: base64",
                "",
                "VGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZy4gVGhlIHF1aWNrIGJy",
                "b3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZy4gVGhlIHF1aWNrIGJyb3duIGZveCBqdW1w",
                "cyBvdmVyIHRoZSBsYXp5IGRvZy4gVGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBs",
                "YXp5IGRvZy4g",
                "--AaB03x--",
                ""
        };
        String t = "The quick brown fox jumps over the lazy dog. ";
        test("AaB03x", body,
                new FileInfo().name("reply").content("yes"),
                new FileInfo().name("fileupload").filename("hello world.txt").contentType("image/jpeg").content(t + t + t + t),
                "success");
    }

    @Test
    public void testNestedMixed() {
        // Test taken from https://github.com/isaacs/multipart-js/blob/master/test/fixture.js
        String[] body = {
                "--outer",
                "Content-Type: multipart/mixed; boundary=inner1",
                "",
                "--inner1",
                "Content-type: multipart/mixed; boundary=inner2",
                "",
                "--inner2",
                "Content-type: text/plain",
                "content-disposition: inline; filename=\"hello.txt\"",
                "",
                "hello, world",
                "--inner2",
                "content-type: text/plain",
                "content-disposition: inline; filename=\"hello2.txt\"",
                "",
                "hello to the world",
                "--inner2--",
                "--inner1",
                "Content-type: multipart/mixed; boundary=inner3",
                "",
                "--inner3",
                "Content-type: text/plain",
                "content-disposition: inline; filename=\"hello3.txt\"",
                "",
                "hello, free the world",
                "--inner3",
                "content-type: text/plain",
                "content-disposition: inline; filename=\"hello4.txt\"",
                "",
                "hello for the world",
                "--inner3--",
                "--inner1",
                "Content-type: text/plain",
                "content-disposition: inline; filename=\"hello-outer.txt\"",
                "",
                "hello, outer world",
                "--inner1--",
                "--outer--"
        };

        test("outer", body,
                new FileInfo().filename("hello.txt").content("hello, world").contentType("text/plain"),
                new FileInfo().filename("hello2.txt").content("hello to the world").contentType("text/plain"),
                new FileInfo().filename("hello3.txt").content("hello, free the world").contentType("text/plain"),
                new FileInfo().filename("hello4.txt").content("hello for the world").contentType("text/plain"),
                new FileInfo().filename("hello-outer.txt").content("hello, outer world").contentType("text/plain"),
                "success");
    }

    public void test(String boundary, String[] body, Object... expected) {
        final List<Object> files = new ArrayList<>();

        Buffer buf = new Buffer();
        for (String l : body) {
            buf.appendString(l);
            buf.appendString("\r\n");
        }

        TestReadStream istr = new TestReadStream();
        final MultipartHandler partHandler = new MultipartHandler(boundary.getBytes(), istr);

        partHandler.setFileHandler(new Handler<FieldInfo>() {
            @Override
            public void handle(FieldInfo field) {
                final FileInfo file = new FileInfo();
                file.name = field.getName();
                file.filename = field.getFilename();
                file.contentType = field.getContentType();
                file.content = new Buffer();

                field.dataHandler(new Handler<Buffer>() {
                    public void handle(Buffer buf) {
                        file.content.appendBuffer(buf);
                    }
                });
                field.endHandler(new Handler<Void>() {
                    public void handle(Void arg) {
                        files.add(file);
                    }
                });
            }
        });
        partHandler.exceptionHandler(new Handler<Exception>() {
            @Override
            public void handle(Exception exc) {
                files.add(exc);
            }
        });
        partHandler.endHandler(new Handler<Void>() {
            public void handle(Void arg) {
                files.add("success");
            }
        });

        istr.process(buf);

        System.out.println(files);
        Assert.assertEquals(expected.length, files.size());
        for (int i = 0; i < files.size(); i++) {
            Assert.assertEquals(expected[i].getClass(), files.get(i).getClass());
            Assert.assertEquals(expected[i].toString(), files.get(i).toString());
        }
    }

    static class FileInfo {
        String name;
        String filename;
        String contentType;
        Buffer content;

        public FileInfo filename(String name) {
            this.filename = name;
            return this;
        }

        public FileInfo name(String name) {
            this.name = name;
            return this;
        }

        public FileInfo contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public FileInfo content(String content) {
            this.content = new Buffer(content);
            return this;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "FileInfo [filename=" + filename + ", contentType=" + contentType + ", name=" + name + ", content=" + content + "]";
        }
    }
}
