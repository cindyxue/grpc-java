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

import com.google.api.expr.v1alpha1.Expr;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.grpc.xds.InterpreterException;
import java.util.HashMap;
import java.util.Map;

/** 
 * Cel Evaluation Engine that is used to evaluate 
 * Envoy RBAC condition in gRPC-Java.
 */
public class CelEvaluationEngine<ReqT, RespT> {
  private final RBAC.Action action;
  private final ImmutableMap<String, Expr> conditions;

  /**
   * Builds a CEL evaluation engine from Envoy RBAC.
   * @param rbac input Envoy RBAC policies.
   */
  public CelEvaluationEngine(RBAC rbac) {
    Map<String, Expr> conditions = new HashMap<>();
    for (Map.Entry<String, Policy> entry: rbac.getPolicies().entrySet()) {
      conditions.put(entry.getKey(), entry.getValue().getCondition());
    }

    this.action = Preconditions.checkNotNull(rbac.getAction());
    this.conditions = Preconditions.checkNotNull(ImmutableMap.copyOf(conditions));
  }

  // Builds a CEL evaluation engine from runtime policy template.
  // public CelEvaluationEngine(RTPolicyTemplate rt_policy) {
  //   // TBD
  // }

  /**
   * Evaluates Envoy Attributes and returns an authorization decision.
   * @param args EvaluateArgs that is used to evaluate the conditions.
   * @return an AuthorizationDecision.
   */
  public AuthorizationDecision evaluate(EvaluateArgs<ReqT, RespT> args) 
    throws InterpreterException {
    AuthorizationDecision authDecision = new AuthorizationDecision();
    for (Map.Entry<String, Expr> entry : this.conditions.entrySet()) {
      if (Evaluator.matches(entry.getValue(), args)) {
        if (this.action == RBAC.Action.ALLOW) {
          authDecision.decision = AuthorizationDecision.Decision.ALLOW;
        } else {
          authDecision.decision = AuthorizationDecision.Decision.DENY;
        }
        authDecision.authorizationContext = "Policy matched: " + entry.getKey();
        return authDecision;
      }
    }

    authDecision.decision = AuthorizationDecision.Decision.UNKNOWN;
    authDecision.authorizationContext = 
      "Unable to decide based on given information - no policies matched";
    return authDecision;
  }
}
