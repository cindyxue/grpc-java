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

import java.lang.StringBuilder;

/** 
 * Authorization decision of Cel Engine.
 * Decisions are generated based on Envoy Attributes.
 */
public class AuthorizationDecision {
  enum Decision {
    ALLOW,
    DENY,
    UNKNOWN,
  }

  Decision decision;
  String authorizationContext;

  @Override
  public String toString() {
    StringBuilder authDecision = new StringBuilder();
    switch (this.decision) {
      case ALLOW: 
        authDecision.append("Authorization Decision: ALLOW.\n");
        break;
      case DENY: 
        authDecision.append("Authorization Decision: DENY.\n");
        break;
      case UNKNOWN: 
        authDecision.append("Authorization Decision: UNKNOWN.\n");
        break;
      default: 
        // throw new Exception();
        authDecision.append("");
        break;
    }
    authDecision.append(this.authorizationContext);
    return authDecision.toString();
  }
}
