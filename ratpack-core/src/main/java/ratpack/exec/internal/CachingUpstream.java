/*
 * Copyright 2014 the original author or authors.
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

package ratpack.exec.internal;

import ratpack.exec.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CachingUpstream<T> implements Upstream<T> {

  private final Upstream<? extends T> upstream;
  private final AtomicBoolean fired = new AtomicBoolean();
  private final Queue<Job> waiting = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final AtomicReference<ExecResult<T>> result = new AtomicReference<>();

  public CachingUpstream(Upstream<? extends T> upstream) {
    this.upstream = upstream;
  }

  private class Job {
    final Downstream<? super T> downstream;
    final ExecutionBacking.StreamHandle streamHandle;

    private Job(Downstream<? super T> downstream, ExecutionBacking.StreamHandle streamHandle) {
      this.downstream = downstream;
      this.streamHandle = streamHandle;
    }
  }

  private void tryDrain() {
    if (draining.compareAndSet(false, true)) {
      try {
        ExecResult<T> result = this.result.get();

        Job job = waiting.poll();
        while (job != null) {
          Job finalJob = job;
          job.streamHandle.complete(() -> finalJob.downstream.accept(result));
          job = waiting.poll();
        }
      } finally {
        draining.set(false);
      }
    }
    if (!draining.get() && !waiting.isEmpty()) {
      tryDrain();
    }
  }

  @Override
  public void connect(Downstream<? super T> downstream) throws Exception {
    if (fired.compareAndSet(false, true)) {
      upstream.connect(new Downstream<T>() {
        @Override
        public void error(Throwable throwable) {
          result.set(new ResultBackedExecResult<>(Result.<T>error(throwable), Execution.execution()));
          doDrainInNewSegment();
          downstream.error(throwable);
        }

        @Override
        public void success(T value) {
          result.set(new ResultBackedExecResult<>(Result.success(value), Execution.execution()));
          doDrainInNewSegment();
          downstream.success(value);
        }

        @Override
        public void complete() {
          result.set(new CompleteExecResult<>(Execution.execution()));
          doDrainInNewSegment();
          downstream.complete();
        }
      });
    } else {
      ExecutionBacking.require().streamSubscribe((streamHandle) -> {
        waiting.add(new Job(downstream, streamHandle));
        if (result.get() != null) {
          tryDrain();
        }
      });
    }
  }

  private void doDrainInNewSegment() {
    ExecutionBacking.require().getEventLoop().execute(this::tryDrain);
  }

}

