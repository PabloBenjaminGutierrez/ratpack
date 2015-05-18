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

package ratpack.config.internal.module

import com.fasterxml.jackson.databind.JsonNode
import ratpack.config.internal.DefaultConfigDataSpec
import ratpack.server.internal.ServerConfigData
import ratpack.server.internal.ServerEnvironment
import ratpack.test.embed.BaseDirBuilder
import spock.lang.AutoCleanup
import spock.lang.Specification

class ServerConfigDataDeserializerSpec extends Specification {
  @AutoCleanup
  def b1 = BaseDirBuilder.tmpDir()
  def originalClassLoader
  def classLoader = new GroovyClassLoader()
  def serverEnvironment = new ServerEnvironment([:], new Properties())
  def deserializer = new ServerConfigDataDeserializer(serverEnvironment)
  def objectMapper = DefaultConfigDataSpec.newDefaultObjectMapper(serverEnvironment)

  def setup() {
    originalClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = classLoader
  }

  def cleanup() {
    Thread.currentThread().contextClassLoader = originalClassLoader
  }

  def "can specify baseDir"() {
    def dir = b1.dir("p1")

    when:
    def serverConfig = deserialize(objectMapper.createObjectNode().put("baseDir", dir.toString()))

    then:
    serverConfig.baseDir == dir
  }

  def "without baseDir results in no base dir"() {
    when:
    def serverConfig = deserialize(objectMapper.createObjectNode())

    then:
    !serverConfig.baseDir
  }

  def "without any config uses default from server config builder"() {
    when:
    def serverConfig = deserialize(objectMapper.createObjectNode())

    then:
    serverConfig.port == 5050
    !serverConfig.development
    !serverConfig.publicAddress
  }

  private ServerConfigData deserialize(JsonNode node) {
    deserializer.deserialize(node.traverse(objectMapper), objectMapper.deserializationContext)
  }
}
