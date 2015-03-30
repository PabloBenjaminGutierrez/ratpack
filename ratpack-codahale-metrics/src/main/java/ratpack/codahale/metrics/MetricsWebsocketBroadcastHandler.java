/*
 * Copyright 2013 the original author or authors.
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

package ratpack.codahale.metrics;

import ratpack.codahale.metrics.internal.MetricRegistryJsonMapper;
import ratpack.codahale.metrics.internal.MetricsBroadcaster;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import static ratpack.stream.Streams.map;
import static ratpack.websocket.WebSockets.websocketByteBufBroadcast;

/**
 * A Handler that broadcasts metric reports via web sockets.
 * <p>
 * This handler should be bound to an application path, and most likely only for the GET method…
 * <pre class="java-chain-dsl">
 * import ratpack.codahale.metrics.MetricsWebsocketBroadcastHandler;
 * import static org.junit.Assert.*;
 *
 * assertTrue(chain instanceof ratpack.handling.Chain);
 * chain.get("admin/metrics", new MetricsWebsocketBroadcastHandler());
 * </pre>
 */
public class MetricsWebsocketBroadcastHandler implements Handler {

  @Override
  public void handle(final Context context) throws Exception {
    final MetricsBroadcaster broadcaster = context.get(MetricsBroadcaster.class);
    final MetricRegistryJsonMapper mapper = context.get(MetricRegistryJsonMapper.class);

    websocketByteBufBroadcast(
      context,
      map(broadcaster, mapper)
    );
  }


}

