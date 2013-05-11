/*
 * Copyright 2012 the original author or authors.
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

package org.ratpackframework.file.internal;

import org.ratpackframework.file.FileSystemContext;
import org.ratpackframework.routing.Exchange;
import org.ratpackframework.routing.Handler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;

public class DirectoryStaticAssetRequestHandler implements Handler {

  private final List<String> indexFiles;
  private final Handler delegate;

  public DirectoryStaticAssetRequestHandler(List<String> indexFiles, Handler delegate) {
    this.delegate = delegate;
    this.indexFiles = new ArrayList<>(indexFiles);
  }

  @Override
  public void handle(Exchange exchange) {
    FileSystemContext fileSystemContext = exchange.get(FileSystemContext.class);
    File targetFile = fileSystemContext.getFile();
    if (targetFile.isDirectory()) {
      for (String indexFileName : indexFiles) {
        File file = new File(targetFile, indexFileName);
        if (file.isFile()) {
          exchange.nextWithContext(fileSystemContext.context(file), delegate);
          return;
        }
      }
      exchange.getResponse().status(FORBIDDEN.code(), FORBIDDEN.reasonPhrase()).send();
    } else {
      exchange.next(delegate);
    }
  }
}
