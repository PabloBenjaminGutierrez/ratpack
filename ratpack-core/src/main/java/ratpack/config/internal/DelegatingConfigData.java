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

package ratpack.config.internal;

import ratpack.config.ConfigData;
import ratpack.config.ConfigObject;
import ratpack.registry.Registry;
import ratpack.server.StartEvent;
import ratpack.server.StopEvent;

public class DelegatingConfigData implements ConfigData {
  private final ConfigData delegate;

  public DelegatingConfigData(ConfigData delegate) {
    this.delegate = delegate;
  }

  @Override
  public <O> O get(String pointer, Class<O> type) {
    return delegate.get(pointer, type);
  }

  @Override
  public <O> ConfigObject<O> getAsConfigObject(String pointer, Class<O> type) {
    return delegate.getAsConfigObject(pointer, type);
  }

  @Override
  public boolean shouldReload(Registry registry) {
    return delegate.shouldReload(registry);
  }

  @Override
  public <O> O get(Class<O> type) {
    return delegate.get(type);
  }

  @Override
  public void onStart(StartEvent event) throws Exception {
    delegate.onStart(event);
  }

  @Override
  public void onStop(StopEvent event) throws Exception {
    delegate.onStop(event);
  }
}
