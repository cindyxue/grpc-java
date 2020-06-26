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

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Grpc;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.xds.InterpreterException;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 
 * Cel Evaluation Engine that is used to evaluate 
 * Envoy RBAC condition in gRPC-Java.
 */
public class CelEvaluationEngine<ReqT, RespT> {
  private final RBAC.Action action;
  private final Map<String, Expr> conditions;

  /**
   * Builds a CEL evaluation engine from Envoy RBAC.
   * @param rbac input envoy RBAC policies.
   */
  public CelEvaluationEngine(@Nullable RBAC rbac) {
    this.action = rbac.getAction();
    this.conditions = new HashMap<>();
    for(Map.Entry<String, Policy> entry: rbac.getPolicies().entrySet()) {
        this.conditions.put(entry.getKey(), entry.getValue().getCondition());
    }
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
        authDecision.decision = this.action == RBAC.Action.ALLOW ? 
            AuthorizationDecision.Decision.ALLOW : AuthorizationDecision.Decision.DENY;
        authDecision.authorizationContext = "policy matched: " + entry.getKey();
        return authDecision;
      }
    }

    authDecision.decision = AuthorizationDecision.Decision.UNKNOWN;
    authDecision.authorizationContext = "Unable to decide based on given information - no policies matched";
    return authDecision;
  }
}