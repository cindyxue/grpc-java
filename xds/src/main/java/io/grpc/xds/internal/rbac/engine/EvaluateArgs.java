/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds.internal;

import io.grpc.Metadata;
import io.grpc.ServerCall;

/** Args for Cel evaluation, which contains necessary information on Envoy Attributes. */
public class EvaluateArgs<ReqT, RespT> {
  private Metadata headers;
  private ServerCall<ReqT, RespT> call;

  public EvaluateArgs() {
    this.headers = null;
    this.call = null;
  }

  public EvaluateArgs(ServerCall<ReqT, RespT> call) {
    this.headers = null;
    this.call = call;
  }

  public EvaluateArgs(Metadata headers, ServerCall<ReqT, RespT> call) {
    this.headers = headers;
    this.call = call;
  }

  public Metadata getHeaders() {
    return headers;
  }

  public ServerCall<ReqT, RespT> getCall() {
    return call;
  }
}
