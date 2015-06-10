/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.session.internal;

import com.google.common.cache.Cache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import ratpack.exec.ExecControl;
import ratpack.exec.Operation;
import ratpack.exec.Promise;
import ratpack.server.StopEvent;
import ratpack.session.SessionStore;

import java.util.concurrent.atomic.AtomicLong;

public class LocalMemorySessionStore implements SessionStore {

  private final ExecControl execControl;
  private final Cache<AsciiString, ByteBuf> cache;
  private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

  public LocalMemorySessionStore(Cache<AsciiString, ByteBuf> cache, ExecControl execControl) {
    this.cache = cache;
    this.execControl = execControl;
  }

  @Override
  public Operation store(AsciiString sessionId, ByteBuf sessionData) {
    return execControl.operation(() -> {
      maybeCleanup();
      ByteBuf retained = Unpooled.unmodifiableBuffer(sessionData);
      cache.put(sessionId, retained);
    });
  }

  @Override
  public Promise<ByteBuf> load(AsciiString sessionId) {
    return execControl.promiseFrom(() -> {
      maybeCleanup();
      ByteBuf value = cache.getIfPresent(sessionId);
      if (value != null) {
        return Unpooled.unreleasableBuffer(value.slice());
      } else {
        return Unpooled.buffer(0, 0);
      }
    });
  }

  @Override
  public Promise<Long> size() {
    return execControl.promiseFrom(cache::size);
  }

  @Override
  public Operation remove(AsciiString sessionId) {
    return execControl.operation(() -> {
      maybeCleanup();
      cache.invalidate(sessionId);
    });
  }

  @Override
  public void onStop(StopEvent event) throws Exception {
    cache.invalidateAll();
  }

  private void maybeCleanup() {
    long now = System.currentTimeMillis();
    long last = lastCleanup.get();
    if (now - last > 1000 * 10) {
      if (lastCleanup.compareAndSet(last, now)) {
        cache.cleanUp();
      }
    }
  }
}
