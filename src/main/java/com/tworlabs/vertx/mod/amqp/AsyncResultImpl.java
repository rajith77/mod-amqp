/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tworlabs.vertx.mod.amqp;

import org.vertx.java.core.AsyncResult;

public class AsyncResultImpl<T> implements AsyncResult<T>
{
    private Throwable error;

    private T result;

    public AsyncResultImpl(T r)
    {
        result = r;
    }

    public AsyncResultImpl(Throwable e)
    {
        error = e;
    }

    public java.lang.Throwable cause()
    {
        return error;
    }

    public boolean failed()
    {
        return error != null;
    }

    public T result()
    {
        return result;
    }

    public boolean succeeded()
    {
        return result != null;
    }
}