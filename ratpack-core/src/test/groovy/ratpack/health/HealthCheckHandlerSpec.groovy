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

package ratpack.health

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import ratpack.exec.ExecControl
import ratpack.exec.Promise
import ratpack.func.Block
import ratpack.http.MediaType
import ratpack.registry.Registry
import ratpack.render.Renderer
import ratpack.test.internal.RatpackGroovyDslSpec

import java.util.concurrent.CountDownLatch

class HealthCheckHandlerSpec extends RatpackGroovyDslSpec {

  static class HealthCheckFooHealthy implements HealthCheck {
    String getName() { return "foo" }

    Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
      return execControl.promise { f ->
        f.success(HealthCheck.Result.healthy())
      }
    }
  }

  static class HealthCheckBarHealthy implements HealthCheck {
    String getName() { return "bar" }

    Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
      return execControl.promise { f ->
        f.success(HealthCheck.Result.healthy())
      }
    }
  }

  static class HealthCheckFooUnhealthy implements HealthCheck {
    String getName() { return "foo" }

    Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
      return execControl.promise { f ->
        f.success(HealthCheck.Result.unhealthy("EXECUTION TIMEOUT"))
      }
    }
  }

  static class HealthCheckFooUnhealthy2 implements HealthCheck {
    String getName() { return "foo" }

    Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
      throw new Exception("EXCEPTION PROMISE CREATION")
    }
  }

  static class HealthCheckParallel implements HealthCheck {
    private final String name
    private CountDownLatch waitingFor
    private CountDownLatch finalized
    private List<String> output

    HealthCheckParallel(String name, CountDownLatch waitingFor, CountDownLatch finalized, List<String> output) {
      this.name = name
      this.waitingFor = waitingFor
      this.finalized = finalized
      this.output = output
    }

    String getName() { return this.name }

    Promise<HealthCheck.Result> check(ExecControl execControl, Registry registry) throws Exception {
      return execControl.promise { f ->
        if (waitingFor) {
          waitingFor.await()
        }
        output << name
        f.success(HealthCheck.Result.healthy())
        if (finalized) {
          finalized.countDown()
        }
      }
    }
  }

  def "render healthy check"() {
    when:
    bindings {
      bind HealthCheckFooHealthy
    }
    handlers {
      register {
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    assert result.startsWith("foo")
    assert result.contains("HEALTHY")
  }

  def "render unhealthy check"() {
    when:
    bindings {
      bind HealthCheckFooUnhealthy
    }
    handlers {
      register {
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    assert result.startsWith("foo")
    assert result.contains("UNHEALTHY")
    assert result.contains("EXECUTION TIMEOUT")
  }

  def "render unhealthy check while promise itself throwning exception"() {
    when:
    bindings {
      bindInstance(HealthCheck, HealthCheck.of("bar") { execControl, r ->
        execControl.promise { f ->
          throw new Exception("EXCEPTION FROM PROMISE")
        }
      })
    }
    handlers {
      register {
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    assert result.startsWith("bar")
    assert result.contains("UNHEALTHY")
    assert result.contains("EXCEPTION FROM PROMISE")
    assert result.contains("Exception")
  }

  def "render unhealthy check while promise creation throwning exception"() {
    when:
    bindings {
      bind HealthCheckFooUnhealthy2
    }
    handlers {
      register {
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    assert result.startsWith("foo")
    assert result.contains("UNHEALTHY")
    assert result.contains("EXCEPTION PROMISE CREATION")
  }

  def "render nothing if no health check in registry"() {
    when:
    handlers {
      register {
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    assert getText("health-checks").isEmpty()
  }

  def "render healthy check results for more health checks"() {
    when:
    bindings {
      bind HealthCheckFooHealthy
      bind HealthCheckBarHealthy
    }
    handlers {
      register {
        add HealthCheck.of("baz") { ec, r ->
          ec.promise { f ->
            f.success(HealthCheck.Result.healthy())
          }
        }
        add HealthCheck.of("quux") { ec, r ->
          ec.promise { f ->
            f.success(HealthCheck.Result.healthy())
          }
        }
      }
      get("health-checks", new HealthCheckHandler())
      get("health-checks/:name") { ctx ->
        new HealthCheckHandler(pathTokens["name"]).handle(ctx)
      }
    }

    then:
    def result = getText("health-checks")
    String[] results = result.split("\n")
    assert results.length == 4
    assert results[0].startsWith("bar")
    assert results[0].contains("HEALTHY")
    assert results[1].startsWith("baz")
    assert results[1].contains("HEALTHY")
    assert results[2].startsWith("foo")
    assert results[2].contains("HEALTHY")
    assert results[3].startsWith("quux")
    assert results[3].contains("HEALTHY")
  }

  def "health checks run in parallel"() {
    given:
    CountDownLatch latch = new CountDownLatch(1)

    when:
    handlers {
      register {
        add HealthCheck.of("baz") { ec, r ->
          ec.promise { f ->
            latch.await()
            f.success(HealthCheck.Result.healthy())
          }
        }
        add HealthCheck.of("quux") { ec, r ->
          ec.promise { f ->
            latch.countDown()
            f.success(HealthCheck.Result.healthy())
          }
        }
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    String[] results = result.split("\n")
    assert results[0].startsWith("baz")
    assert results[0].contains("HEALTHY")
    assert results[1].startsWith("quux")
    assert results[1].contains("HEALTHY")
  }

  def "duplicated health checks renders only once"() {
    when:
    bindings {
      bind HealthCheckFooHealthy
      bind HealthCheckFooHealthy
    }
    handlers {
      register {
        add HealthCheck.of("foo") { ec, r ->
          ec.promise { f ->
            f.success(HealthCheck.Result.unhealthy("Unhealthy"))
          }
        }
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks")
    String[] results = result.split("\n")
    assert results.size() == 1
    assert results[0].startsWith("foo")
    assert results[0].contains("HEALTHY")
  }

  def "render health check by token if more health checks in registry"() {
    when:
    bindings {
      bind HealthCheckFooHealthy
      bind HealthCheckBarHealthy
    }
    handlers {
      register {
        add HealthCheck.of("baz") { ec ->
          ec.promise { f ->
            f.success(HealthCheck.Result.unhealthy("Unhealthy"))
          }
        }
        add HealthCheck.of("quux") { ec ->
          ec.promise { f ->
            f.success(HealthCheck.Result.healthy())
          }
        }
      }
      get("health-checks/:name", new HealthCheckHandler())
    }

    then:
    def result = getText("health-checks/foo")
    String[] results = result.split("\n")
    assert results.length == 1
    assert results[0].startsWith("foo")
    assert results[0].contains("HEALTHY")

    then:
    def result2 = getText("health-checks/baz")
    String[] results2 = result2.split("\n")
    assert results.length == 1
    assert results2[0].startsWith("baz")
    assert results2[0].contains("UNHEALTHY")
  }

  def "render json health check results for custom renderer"() {
    given:
    def json = new JsonSlurper()

    when:
    bindings {
      bind HealthCheckFooHealthy
      bind HealthCheckBarHealthy
    }
    handlers {
      register {
        add(Renderer.of(HealthCheckResults) { ctx, r ->
          ctx.byContent {
            it.json({ ->
              ctx.render(JsonOutput.toJson(r.results))
            } as Block)
          }
        })
        add HealthCheck.of("baz") { ec, r ->
          ec.promise { f ->
            f.success(HealthCheck.Result.unhealthy("Unhealthy"))
          }
        }
      }
      get("health-checks", new HealthCheckHandler())
    }

    then:
    requestSpec { spec ->
      spec.headers.add("Accept", "application/json")
    }
    def result = get("health-checks")
    assert result.body.contentType.toString() == MediaType.APPLICATION_JSON
    def results = json.parse(result.body.inputStream)
    assert results.foo.healthy == true
    assert results.bar.healthy == true
    assert results.baz.healthy == false
    assert results.baz.message == "Unhealthy"
  }

}
