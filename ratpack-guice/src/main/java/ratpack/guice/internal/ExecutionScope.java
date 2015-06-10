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

package ratpack.guice.internal;

import com.google.common.collect.Maps;
import com.google.inject.*;
import ratpack.exec.Execution;
import ratpack.exec.UnmanagedThreadException;

import java.util.Map;

public class ExecutionScope implements Scope {

  private static class Store {
    private final Map<Key<?>, Object> map = Maps.newHashMap();
  }

  public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
    return () -> {
      Map<Key<?>, Object> scopedObjects = getScopedObjectMap(key);

      @SuppressWarnings("unchecked")
      T current = (T) scopedObjects.get(key);
      if (current == null && !scopedObjects.containsKey(key)) {
        current = unscoped.get();

        // don't remember proxies; these exist only to serve circular dependencies
        if (Scopes.isCircularProxy(current)) {
          return current;
        }

        scopedObjects.put(key, current);
      }
      return current;
    };
  }

  private <T> Map<Key<?>, Object> getScopedObjectMap(Key<T> key) {
    try {
      Execution execution = Execution.execution();
      return execution.maybeGet(Store.class).orElseGet(() -> {
        Store store = new Store();
        execution.add(Store.class, store);
        return store;
      }).map;
    } catch (UnmanagedThreadException e) {
      throw new OutOfScopeException("Cannot access " + key + " outside of an execution");
    }
  }

}
