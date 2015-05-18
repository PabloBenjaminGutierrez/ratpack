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

package ratpack.health;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.exec.Throttle;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.registry.Registry;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A handler that executes {@link HealthCheck health checks} and renders the results.
 * <p>
 * The handler obtains health checks via the context's registry.
 * Typically, health checks are added to the server registry.
 * <pre class="java">{@code
 * import ratpack.exec.ExecControl;
 * import ratpack.exec.Promise;
 * import ratpack.registry.Registry;
 * import ratpack.health.HealthCheck;
 * import ratpack.health.HealthCheckHandler;
 * import ratpack.test.embed.EmbeddedApp;
 *
 * import static org.junit.Assert.*;
 *
 * public class Example {
 *
 *   static class ExampleHealthCheck implements HealthCheck {
 *     public String getName() {
 *       return "example"; // must be unique within the application
 *     }
 *
 *     public Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
 *       return execControl.promiseOf(HealthCheck.Result.healthy());
 *     }
 *   }
 *
 *   public static void main(String... args) throws Exception {
 *     EmbeddedApp.of(s -> s
 *       .registryOf(r -> r
 *         .add(new ExampleHealthCheck())
 *         .add(HealthCheck.of("inline", (execControl, registry) -> // alternative way to implement health checks
 *           execControl.promiseOf(HealthCheck.Result.unhealthy("FAILED"))
 *         ))
 *       )
 *       .handlers(c -> c
 *         .get("health/:name?", new HealthCheckHandler())
 *       )
 *     ).test(httpClient -> {
 *       // Run all health checks
 *       String result = httpClient.getText("health");
 *       String[] results = result.split("\n");
 *       assertEquals(2, results.length);
 *       assertEquals("example : HEALTHY", results[0]);
 *       assertEquals("inline : UNHEALTHY [FAILED]", results[1]);
 *
 *       // Run a single health check
 *       result = httpClient.getText("health/example");
 *       assertEquals("example : HEALTHY", result);
 *     });
 *   }
 * }
 * }</pre>
 *
 * <h3>The output format</h3>
 * <p>
 * The handler creates a {@link HealthCheckResults} object with the results of running the health checks and {@link Context#render(Object) renders} it.
 * Ratpack provides a default renderer for {@link HealthCheckResults} objects, that renders results as plain text one per line with the format:
 * <pre>{@code name : HEALTHY|UNHEALTHY [message] [exception]}</pre>
 * <p>
 * To change the output format, simply add your own renderer for this type to the registry.
 *
 * <h3>Concurrency</h3>
 * <p>
 * The concurrency of the health check execution is managed by a {@link Throttle}.
 * By default, an {@link Throttle#unlimited() unlimited throttle} is used.
 * A sized throttle can be exlicit given to the constructor.
 *
 * <h3>Rendering single health checks</h3>
 * <p>
 * The handler checks for the presence of a {@link Context#getPathTokens() path token} to indicate the name of an individual check to execute.
 * By default, the token is named {@value #DEFAULT_NAME_TOKEN}.
 * If a path token is present, but indicates the name of a non-existent health check, a {@code 404} {@link Context#clientError(int) client error} will be raised.
 *
 * @see ratpack.health.HealthCheck
 * @see ratpack.health.HealthCheckResults
 */
public class HealthCheckHandler implements Handler {

  /**
   * The default path token name that indicates the individual health check to run.
   *
   * Value: {@value}
   *
   * @see #HealthCheckHandler(String, Throttle)
   */
  public static final String DEFAULT_NAME_TOKEN = "name";

  private static final TypeToken<HealthCheck> HEALTH_CHECK_TYPE_TOKEN = TypeToken.of(HealthCheck.class);

  private final String name;
  private final Throttle throttle;

  /**
   * Uses the default values of {@link #DEFAULT_NAME_TOKEN} and an unlimited throttle.
   *
   * @see #HealthCheckHandler(String, Throttle)
   */
  public HealthCheckHandler() {
    this(DEFAULT_NAME_TOKEN, Throttle.unlimited());
  }

  /**
   * Uses an unlimited throttle and the given name for the health check identifying path token.
   *
   * @param pathTokenName health check name
   * @see #HealthCheckHandler(String, Throttle)
   */
  public HealthCheckHandler(String pathTokenName) {
    this(pathTokenName, Throttle.unlimited());
  }

  /**
   * Uses the {@link #DEFAULT_NAME_TOKEN} and the given throttle.
   *
   * @param throttle the throttle for health check execution
   * @see #HealthCheckHandler(String, Throttle)
   */
  public HealthCheckHandler(Throttle throttle) {
    this(DEFAULT_NAME_TOKEN, throttle);
  }

  /**
   * Constructor.
   * <p>
   * The path token name parameter specifies the name of the path token that, if present, indicates the single health check to execute.
   * If this path token is not present, all checks will be run.
   * <p>
   * The throttle controls the concurrency of health check execution.
   * The actual <i>parallelism</i> of the health check executions is ultimately determined by the size of the application event loop in conjunction with the throttle size.
   * Generally, an {@link Throttle#unlimited() unlimited throttle} is appropriate unless there are many health checks that are executed frequently as this may degrade the performance of other requests.
   * To serialize health check execution, use a throttle of size 1.
   *
   * @param pathTokenName the name of health check
   * @param throttle the throttle for health check execution
   */
  protected HealthCheckHandler(String pathTokenName, Throttle throttle) {
    this.name = pathTokenName;
    this.throttle = throttle;
  }

  /**
   * The throttle for executing health checks.
   *
   * @return the throttle for executing health checks.
   */
  public Throttle getThrottle() {
    return throttle;
  }

  /**
   * The name of the path token that may indicate a particular health check to execute.
   *
   * @return the name of the path token that may indicate a particular health check to execute
   */
  public String getName() {
    return name;
  }

  /**
   * Renders health checks.
   *
   * @param ctx the request context
   */
  @Override
  public void handle(Context ctx) {
    ctx.getResponse().getHeaders()
      .add("Cache-Control", "no-cache, no-store, must-revalidate")
      .add("Pragma", "no-cache")
      .add("Expires", 0);

    try {
      String checkName = ctx.getPathTokens().get(name);
      if (checkName != null) {
        Optional<HealthCheck> first = ctx.first(HEALTH_CHECK_TYPE_TOKEN, healthCheck -> healthCheck.getName().equals(checkName) ? healthCheck : null);
        if (first.isPresent()) {
          ctx.render(execute(ctx, ctx, Collections.singleton(first.get())));
        } else {
          ctx.clientError(404);
        }
      } else {
        ctx.render(execute(ctx, ctx, ctx.getAll(HEALTH_CHECK_TYPE_TOKEN)));
      }
    } catch (Exception e) {
      ctx.error(e);
    }
  }

  private Promise<HealthCheck.Result> execute(ExecControl execControl, Registry registry, HealthCheck healthCheck) {
    return execControl.wrap(() -> healthCheck.check(execControl, registry)).mapError(HealthCheck.Result::unhealthy);
  }

  private Promise<HealthCheckResults> execute(ExecControl execControl, Registry registry, Iterable<? extends HealthCheck> healthChecks) {
    Iterator<? extends HealthCheck> iterator = healthChecks.iterator();
    if (!iterator.hasNext()) {
      return execControl.promiseOf(new HealthCheckResults(ImmutableSortedMap.of()));
    }

    return execControl.<Map<String, HealthCheck.Result>>promise(f -> {
      AtomicInteger counter = new AtomicInteger();
      Map<String, HealthCheck.Result> results = Maps.newConcurrentMap();
      while (iterator.hasNext()) {
        counter.incrementAndGet();
        HealthCheck healthCheck = iterator.next();
        execControl.exec().start(e ->
            execute(e, registry, healthCheck)
              .throttled(throttle)
              .then(r -> {
                results.put(healthCheck.getName(), r);
                if (counter.decrementAndGet() == 0 && !iterator.hasNext()) {
                  f.success(results);
                }
              })
        );
      }
    })
      .map(ImmutableSortedMap::copyOf)
      .map(HealthCheckResults::new);
  }
}
