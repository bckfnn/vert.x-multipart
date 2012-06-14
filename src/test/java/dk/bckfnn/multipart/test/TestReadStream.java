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

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;

public class TestReadStream implements ReadStream {
    private Handler<Buffer> dataHandler;
    private Handler<Void> endHandler;

    //private Handler<Exception> exceptionHandler;

    @Override
    public void dataHandler(Handler<Buffer> dataHandler) {
        this.dataHandler = dataHandler;
    }

    @Override
    public void endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
    }

    @Override
    public void exceptionHandler(Handler<Exception> exceptionHandler) {
        //this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    public void process(byte[] data) {
        dataHandler.handle(new Buffer(data));
        endHandler.handle(null);
    }

    public void process(Buffer buf) {
        dataHandler.handle(buf);
        endHandler.handle(null);
    }
}