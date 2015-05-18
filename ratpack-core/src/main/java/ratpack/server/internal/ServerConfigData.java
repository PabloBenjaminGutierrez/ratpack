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

package ratpack.server.internal;

import ratpack.server.ServerConfig;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerConfigData {

  private Path baseDir;
  private int port;
  private InetAddress address;
  private boolean development;
  private int threads = ServerConfig.DEFAULT_THREADS;
  private URI publicAddress;
  private SSLContext sslContext;
  private int maxContentLength = ServerConfig.DEFAULT_MAX_CONTENT_LENGTH;

  public ServerConfigData(ServerEnvironment serverEnvironment) {
    this.port = serverEnvironment.getPort();
    this.development = serverEnvironment.isDevelopment();
    this.publicAddress = serverEnvironment.getPublicAddress();
  }

  public int getPort() {
    return port;
  }

  public InetAddress getAddress() {
    return address;
  }

  public boolean isDevelopment() {
    return development;
  }

  public int getThreads() {
    return threads;
  }

  public URI getPublicAddress() {
    return publicAddress;
  }

  public int getMaxContentLength() {
    return maxContentLength;
  }

  public void setBaseDir(String baseDir) {
    setBaseDir(Paths.get(baseDir));
  }

  public void setBaseDir(Path baseDir) {
    this.baseDir = baseDir;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setAddress(InetAddress address) {
    this.address = address;
  }

  public void setAddress(String host) throws UnknownHostException {
    setAddress(InetAddress.getByName(host));
  }

  public void setDevelopment(boolean development) {
    this.development = development;
  }

  public void setThreads(int threads) {
    this.threads = threads;
  }

  public void setPublicAddress(URI publicAddress) {
    this.publicAddress = publicAddress;
  }

  public void setPublicAddress(String publicAddress) throws URISyntaxException {
    this.publicAddress = new URI(publicAddress);
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  public void setMaxContentLength(int maxContentLength) {
    this.maxContentLength = maxContentLength;
  }

  public Path getBaseDir() {
    return baseDir;
  }
}
