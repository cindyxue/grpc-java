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
  private enum Action {
    ALLOW,
    DENY
  }

  /** Holds conditions of RBAC policies. */
  private class Condition {
    private Expr expr;

    public Condition(Expr expr) {
      this.expr = expr;
    }

    /**
     * Check if arg matches with the condition expr
     * @param args EvaluateArgs that is used to evaluate the conditions.
     * @return whether or not arg matches this expr
     */
    public boolean matches(EvaluateArgs<ReqT, RespT> args) {
      // Extract Envoy Attributes from EvaluateArgs
      ServerCall<ReqT, RespT> call = args.getCall();
      String requestUrlPath = "";
      String requestHost = call.getMethodDescriptor().getServiceName();
      String requestMethod = call.getMethodDescriptor().getFullMethodName();
      String sourceAddress = call.getAttributes()
        .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
      int sourcePort = 0;
      String destinationAddress = call.getAttributes()
        .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR).toString();
      int destinationPort = 0;
      Metadata requestHeaders = args.getHeaders();
      String connectionRequestedServerName = call.getAuthority();
      String connectionUriSanPeerCertificate = "";

      // List<Descriptor> descriptors = new ArrayList<>();
      // RuntimeTypeProvider messageProvider = DescriptorMessageProvider.dynamicMessages(descriptors);
      // Dispatcher dispatcher = DefaultDispatcher.create();
      // Interpreter interpreter = new DefaultInterpreter(messageProvider, dispatcher);
  
      // CheckedExpr checkedResult = CheckedExpr.newBuilder().build();
  
      // Map<String, Object> map = new HashMap<>();
      // map.put("requestUrlPath", new Object());
      // map.put("requestHost", new Object());
      // map.put("requestMethod", new Object());
      // map.put("requestHeaders", new Object());
      // map.put("sourceAddress", new Object());
      // map.put("sourcePort", new Object());
      // map.put("destinationAddress", new Object());
  
      // ImmutableMap<String, Object> apiAttributes = ImmutableMap.copyOf(map);
  
      // Activation activation = Activation.copyOf(apiAttributes);
      // Object result = interpreter.createInterpretable(checkedResult).eval(activation);
      
      // Todo
      return true;
    }
  }

  Action action;
  Map<String, Condition> conditions;

  /**
   * Builds a CEL evaluation engine from Envoy RBAC.
   * @param rbac input envoy RBAC policies.
   */
  public CelEvaluationEngine(@Nullable RBAC rbac) {
    this.action = rbac.getAction() == RBAC.Action.ALLOW ? Action.ALLOW : Action.DENY;
    this.conditions = new HashMap<>();
    for(Map.Entry<String, Policy> entry: rbac.getPolicies().entrySet()) {
        this.conditions.put(entry.getKey(), new Condition(entry.getValue().getCondition()));
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
  public AuthorizationDecision evaluate(EvaluateArgs<ReqT, RespT> args) {
    AuthorizationDecision authDecision = new AuthorizationDecision();
    for (Map.Entry<String, Condition> entry : this.conditions.entrySet()) {
      if (entry.getValue().matches(args)) {
        authDecision.decision = this.action == Action.ALLOW ? 
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