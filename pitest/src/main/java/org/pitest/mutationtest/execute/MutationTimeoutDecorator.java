/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.execute;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.pitest.extension.common.TestUnitDecorator;
import org.pitest.functional.SideEffect;
import org.pitest.mutationtest.TimeoutLengthStrategy;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.Unchecked;

import de.unisb.cs.st.javaslicer.tracer.Tracer;
import edu.columbia.cs.psl.testprof.TracerConnector;

public final class MutationTimeoutDecorator extends TestUnitDecorator {

  private final TimeoutLengthStrategy timeOutStrategy;
  private final SideEffect            timeOutSideEffect;
  private final long                  executionTime;

  public MutationTimeoutDecorator(final TestUnit child,
      final SideEffect timeOutSideEffect,
      final TimeoutLengthStrategy timeStrategy, final long executionTime) {
    super(child);
    this.timeOutSideEffect = timeOutSideEffect;
    this.executionTime = executionTime;
    this.timeOutStrategy = timeStrategy;
  }

  @Override
  public void execute(final ClassLoader loader, final ResultCollector rc) {
    if(Tracer.isAvailable())
    {
        String logName = TracerConnector.allMutations.size() +"."+this.getDescription().getName();
        Tracer.getInstance().writeOutAndStartFresh(logName);
    }
    final long maxTime = this.timeOutStrategy
        .getAllowedTime(this.executionTime);

    final FutureTask<?> future = createFutureForChildTestUnit(loader, rc);
    executeFutureWithTimeOut(maxTime, future, rc);
    try {
        if(Tracer.isAvailable())
            Tracer.getInstance().finish();
    } catch (IOException e) {
        e.printStackTrace();
    }
    if (!future.isDone()) {
      this.timeOutSideEffect.apply();
    }

  }

  private void executeFutureWithTimeOut(final long maxTime,
      final FutureTask<?> future, final ResultCollector rc) {
    try {
      future.get(maxTime, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException ex) {
      // swallow
    } catch (final InterruptedException e) {
      // swallow
    } catch (final ExecutionException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  private FutureTask<?> createFutureForChildTestUnit(final ClassLoader loader,
      final ResultCollector rc) {
    final FutureTask<?> future = new FutureTask<Object>(createRunnable(loader,
        rc), null);
    final Thread thread = new Thread(future);
    thread.setDaemon(true);
    thread.setName("mutationTestThread");
    thread.start();
    return future;
  }

  private Runnable createRunnable(final ClassLoader loader,
      final ResultCollector rc) {
    return new Runnable() {

      @Override
      public void run() {
        try {
          child().execute(loader, rc);
        } catch (final Throwable ex) {
          rc.notifyEnd(child().getDescription(), ex);
        }

      }
    };
  }

}
