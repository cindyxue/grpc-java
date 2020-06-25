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
  private RBAC.Action action;
  private Map<String, Expr> conditions;
  private Map<String, Object> attributes;

  /**
   * Builds a CEL evaluation engine from Envoy RBAC.
   * @param rbac input envoy RBAC policies.
   */
  public CelEvaluationEngine(@Nullable RBAC rbac) {
    this.action = rbac.getAction();
    this.conditions = new HashMap<>();
    this.attributes = new HashMap<>();

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
      if (CelEvaluationEngine.matches(entry.getValue(), args)) {
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

  /**
   * Determines if a condition matches the given EvaluateArgs using Cel library
   * @param <ReqT>
   * @param <RespT>
   * @param conditions
   * @param args
   * @return
   * @throws InterpreterException
   */
  private static <ReqT, RespT> boolean matches(Expr conditions, EvaluateArgs<ReqT, RespT> args) 
    throws InterpreterException {
    CheckedExpr checkedResult = CelEvaluationEngine.parseConditions(conditions);
    ImmutableMap<String, Object> apiAttributes = CelEvaluationEngine.extractFields(args);
    Object result = CelEvaluationEngine.celEvaluate(checkedResult, apiAttributes);

    if(result instanceof Boolean) {
      return Boolean.parseBoolean(result.toString());
    }

    return false;
  }

  /**
   * Helper function for cel evaluate.
   * @param <ReqT>
   * @param <RespT>
   * @param checkedResult
   * @param apiAttributes
   * @return
   * @throws InterpreterException
   */
  private static <ReqT, RespT> Object celEvaluate(CheckedExpr checkedResult, 
    ImmutableMap<String, Object> apiAttributes) throws InterpreterException {
    List<Descriptor> descriptors = new ArrayList<>();
    RuntimeTypeProvider messageProvider = DescriptorMessageProvider.dynamicMessages(descriptors);
    Dispatcher dispatcher = DefaultDispatcher.create();
    Interpreter interpreter = new DefaultInterpreter(messageProvider, dispatcher);

    Activation activation = Activation.copyOf(apiAttributes);
    Object result = interpreter.createInterpretable(checkedResult).eval(activation);

    return result;
  }

  /**
   * Convert conditions from Expr type into CheckedExpr type
   * @param conditions
   * @return
   */
  private static CheckedExpr parseConditions(Expr conditions) {
    if(conditions == null) {
      return null;
    }

    ParsedExpr parsedConditions = ParsedExpr.newBuilder()
    .setExpr(conditions)
    .setSourceInfo(SourceInfo.newBuilder().build())
    .build();

    return CheckedExpr.newBuilder().build();
  }

  /**
   * Extract Envoy Attributes from EvaluateArgs
   * @param args
   * @return 
   */
  private static <ReqT, RespT> ImmutableMap<String, Object> extractFields(
    EvaluateArgs<ReqT, RespT> args) {
    if(args == null) {
      return null;
    }

    ServerCall<ReqT, RespT> call = args.getCall();
    String requestUrlPath = "";
    String requestHost = call.getMethodDescriptor().getServiceName();
    String requestMethod = call.getMethodDescriptor().getFullMethodName();
    Metadata requestHeaders = args.getHeaders();
    String sourceAddress = call.getAttributes()
      .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
    int sourcePort = 0;
    String destinationAddress = call.getAttributes()
      .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR).toString();
    int destinationPort = 0;
    String connectionRequestedServerName = call.getAuthority();
    String connectionUriSanPeerCertificate = "";

    Map<String, Object> map = new HashMap<>();
    // map.put("requestUrlPath", requestUrlPath);
    map.put("requestHost", requestHost);
    map.put("requestMethod", requestMethod);
    map.put("requestHeaders", requestHeaders);
    map.put("sourceAddress", sourceAddress);
    // map.put("sourcePort", sourcePort);
    map.put("destinationAddress", destinationAddress);
    // map.put("destinationPort", destinationPort);
    map.put("connectionRequestedServerName", connectionRequestedServerName);
    // map.put("connectionUriSanPeerCertificate", connectionUriSanPeerCertificate);

    return ImmutableMap.copyOf(map);
  }

  private ImmutableMap<String, Object> extractAttributes(EvaluateArgs<ReqT, RespT> args) {
    ServerCall<ReqT, RespT> call = args.getCall();
    setRequestHost(call);
    setRequestMethod(call);

    return ImmutableMap.copyOf(this.attributes);
  }

  private void setRequestHost(ServerCall<ReqT, RespT> call) {
    String requestHost = call.getMethodDescriptor().getServiceName();
    if(requestHost == null || requestHost.length() == 0) {
      return;
    }
    this.attributes.put("requestHost", requestHost);
  }

  private void setRequestMethod(ServerCall<ReqT, RespT> call) {
    String requestMethod = call.getMethodDescriptor().getFullMethodName();
    if(requestMethod == null || requestMethod.length() == 0) {
      return;
    }
    this.attributes.put("requestMethod", requestMethod);
  }
}