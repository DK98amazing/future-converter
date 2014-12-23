/**
 * Copyright 2009-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.futureconverter.java8common;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Registry for Consumer callbacks
 *
 * <p>Inspired by {@code org.springframework.util.concurrent.ListenableFutureCallbackRegistry}
 *
 */
class ListenableCallbackRegistry<T> {

	private final Queue<CallbackExecutorPair<? super T>> successCallbacks = new LinkedList<>();
	private final Queue<CallbackExecutorPair<Throwable>> failureCallbacks = new LinkedList<>();

	private State state = State.NEW;

	private T result = null;

	private Throwable failure = null;

	private final Object mutex = new Object();


	private <S> void callCallback(Consumer<S> callback, S value, Executor executor) {
		executor.execute(() -> callback.accept(value));
	}

	private <S> void callCallback(CallbackExecutorPair<S> callbackExecutorPair, S result) {
		callCallback(callbackExecutorPair.getCallback(), result, callbackExecutorPair.getExecutor());
	}


    /**
	 * Adds the given callbacks to this registry.
	 */
	public void addCallbacks(Consumer<? super T> successCallback, Consumer<Throwable> failureCallback, Executor executor) {
		Objects.requireNonNull(successCallback, "'successCallback' must not be null");
		Objects.requireNonNull(failureCallback, "'failureCallback' must not be null");
		Objects.requireNonNull(executor, "'executor' must not be null");

		synchronized (mutex) {
			switch (state) {
				case NEW:
                    successCallbacks.add(new CallbackExecutorPair<>(successCallback, executor));
                    failureCallbacks.add(new CallbackExecutorPair<>(failureCallback, executor));
					break;
				case SUCCESS:
					callCallback(successCallback, result, executor);
					break;
				case FAILURE:
					callCallback(failureCallback, failure, executor);
					break;
			}
		}
	}

	public void success(T result) {
		synchronized (mutex) {
			state = State.SUCCESS;
			this.result = result;

			while (!successCallbacks.isEmpty()) {
				callCallback(successCallbacks.poll(), result);
			}
            failureCallbacks.clear();
		}
	}

	public void failure(Throwable t) {
		synchronized (mutex) {
			state = State.FAILURE;
			this.failure = t;

			while (!failureCallbacks.isEmpty()) {
				callCallback(failureCallbacks.poll(), failure);
			}
            successCallbacks.clear();
		}
	}

	private enum State {NEW, SUCCESS, FAILURE}

	private static class CallbackExecutorPair<S> {
		private final Consumer<S> callback;
		private final Executor executor;

		private CallbackExecutorPair(Consumer<S> callback, Executor executor) {
			this.callback = callback;
			this.executor = executor;
		}

		public Consumer<S> getCallback() {
			return callback;
		}

		public Executor getExecutor() {
			return executor;
		}
	}

}
