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

import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.func.Function;

/**
 * Reports on the health of some aspect of the system.
 * <p>
 * Health checks are typically used for reporting and monitoring purposes.
 * The results exposed by health checks can be reported via HTTP by a {@link ratpack.health.HealthCheckHandler}.
 * <p>
 * The actual check is implemented by the {@link #check(ExecControl)} method, that returns a promise for a {@link Result}.
 * <p>
 * The inspiration for health checks in Ratpack comes from the <a href="https://dropwizard.github.io/metrics/3.1.0/manual/healthchecks/">Dropwizard Metrics</a> library.
 * Ratpack health checks are different in that they support non blocking checks, and all health checks require a unique name.
 *
 * @see ratpack.health.HealthCheckHandler
 */
public interface HealthCheck {

  /**
   * The result of a health check.
   *
   * Instances can be created by one of the static methods of this class (e.g. {@link #healthy()}).
   */
  class Result {

    private static final Result HEALTHY = new Result(true, null, null);

    private final boolean healthy;
    private final String message;
    private final Throwable error;

    private Result(boolean healthy, String message, Throwable error) {
      this.healthy = healthy;
      this.message = message;
      this.error = error;
    }

    /**
     * Was the component being checked healthy?
     *
     * @return {@code true} if application component is healthy
     */
    public boolean isHealthy() {
      return healthy;
    }

    /**
     * Any message provided as part of the check, may be {@code null}.
     * <p>
     * A message may be provided with a healthy or unhealthy result.
     * <p>
     * If {@link #getError()} is non null, this message will be the message provided by that exception.
     *
     * @return any message provided as part of the check, may be {@code null}
     */
    @Nullable
    public String getMessage() {
      return message;
    }

    /**
     * The exception representing an unhealthy check, may be {@code null}.
     * <p>
     * Healthy results will never have an associated error.
     *
     * @return the exception representing an unhealthy check, may be {@code null}
     */
    @Nullable
    public Throwable getError() {
      return error;
    }

    /**
     * Creates a healthy result, with no message.
     *
     * @return a healthy result, with no message.
     */
    public static Result healthy() {
      return HEALTHY;
    }

    /**
     * Creates a healthy result, with the given message.
     *
     * @param message a message to accompany the result
     * @return a healthy result, with the given message
     */
    public static Result healthy(String message) {
      return new Result(true, message, null);
    }

    /**
     * Creates a healthy result, with the given message.
     * <p>
     * The message is constructed by {@link String#format(String, Object...)} and the given arguments.
     *
     * @param message the message format strings
     * @param args values to be interpolated into the format string
     * @return a healthy result, with the given message
     */
    public static Result healthy(String message, Object... args) {
      return healthy(String.format(message, args));
    }

    /**
     * Creates an unhealthy result, with the given message.
     *
     * @param message a message to accompany the result
     * @return an unhealthy result, with the given message
     */
    public static Result unhealthy(String message) {
      return new Result(false, message, null);
    }

    /**
     * Creates an unhealthy result, with the given message.
     * <p>
     * The message is constructed by {@link String#format(String, Object...)} and the given arguments.
     *
     * @param message the message format strings
     * @param args values to be interpolated into the format string
     * @return an unhealthy result, with the given message
     */
    public static Result unhealthy(String message, Object... args) {
      return unhealthy(String.format(message, args));
    }

    /**
     * Creates an unhealthy result, with the given exception.
     * <p>
     * The message of the given exception will also be used as the message of the result.
     *
     * @param error an exception thrown during health check
     * @return an unhealthy result, with the given error
     */
    public static Result unhealthy(Throwable error) {
      return new Result(false, error.getMessage(), error);
    }
  }

  /**
   * The <b>unique</b> name of the health check.
   * <p>
   * Each health check within an application must have a unique name.
   *
   * @return the name of the health check
   */
  String getName();

  /**
   * Checks the health of the component, providing a promise for the result.
   * <p>
   * This method returns a promise to allow check implementations to be asynchronous.
   * If the implementation does not need to be asynchronous, the result can be returned via {@link ExecControl#promiseOf(Object)}.
   * <p>
   * If this method throws an exception, it is logically equivalent to returned an unhealthy result with the thrown exception.
   * <p>
   * If the method returns a failed promise, it will be converted to a result using {@link Result#unhealthy(Throwable)}.
   *
   * @param execControl an execution control
   * @return a promise for the result
   * @throws Exception any
   */
  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception;

  /**
   * Convenience factory for health check implementations.
   * <pre class="java">{@code
   * import ratpack.test.exec.ExecHarness;
   * import ratpack.health.HealthCheck;
   * import static org.junit.Assert.*;
   *
   * public class Example {
   *   public static void main(String... args) throws Exception {
   *     HealthCheck.Result result = ExecHarness.yieldSingle(e ->
   *       HealthCheck.of("test", execControl ->
   *         execControl.promiseOf(HealthCheck.Result.healthy())
   *       ).check(e)
   *     ).getValue();
   *
   *     assertTrue(result.isHealthy());
   *   }
   * }
   * }</pre>
   *
   * @param name a name of health check
   * @param func a health check implementation
   * @return a named health check implementation
   */
  static HealthCheck of(String name, Function<? super ExecControl, ? extends Promise<HealthCheck.Result>> func) {
    return new HealthCheck() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Promise<Result> check(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
  }
}
