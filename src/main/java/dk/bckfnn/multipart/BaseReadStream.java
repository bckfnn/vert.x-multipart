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

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;

/**
 * A convenient base class for ReadStream filters. The output ReadStream can be
 * specified in the constructor or by calling input(ReadStream).
 */
public class BaseReadStream implements ReadStream {

    protected ReadStream input;

    protected Handler<Buffer> dataHandler;
    protected Handler<Void> endHandler;
    protected Handler<Exception> exceptionHandler;
    // An exception have occurred.
    protected boolean exception;

    public BaseReadStream() {
    }

    public BaseReadStream(ReadStream input) {
        input(input);
    }

    public void input(ReadStream input) {
        this.input = input;
        this.input.dataHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer buf) {
                if (!exception) {
                    handleData(buf);
                }
            }
        });
        this.input.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void arg) {
                if (!exception) {
                    handleEnd();
                }
            }
        });
        this.input.exceptionHandler(new Handler<Exception>() {
            @Override
            public void handle(Exception exc) {
                handleException(exc);
            }
        });

    }

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
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void pause() {
        input.pause();
    }

    @Override
    public void resume() {
        input.resume();
    }

    protected void handleData(Buffer buf) {
        if (dataHandler != null) {
            dataHandler.handle(buf);
        }
    }

    protected void handleEnd() {
        if (endHandler != null) {
            endHandler.handle(null);
        }
    }

    protected void handleException(Exception exc) {
        exception = true;
        if (exceptionHandler != null) {
            exceptionHandler.handle(exc);
        }
    }
}
