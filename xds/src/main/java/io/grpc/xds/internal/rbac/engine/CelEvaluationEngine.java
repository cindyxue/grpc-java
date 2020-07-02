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
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.WellKnownType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.xds.InterpreterException;
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
  @SuppressWarnings("unused")
  private final ImmutableMap<String, Expr> conditions;
  private final ImmutableMap<String, CheckedExpr> parsedConditions;

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
    this.parsedConditions = Preconditions.checkNotNull(
      ImmutableMap.copyOf(parseConditions(conditions)));
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
    for (Map.Entry<String, CheckedExpr> entry : this.parsedConditions.entrySet()) {
      if (matches(entry.getValue(), args)) {
        if (this.action == RBAC.Action.ALLOW) {
          authDecision.decision = AuthorizationDecision.Decision.ALLOW;
        } else {
          authDecision.decision = AuthorizationDecision.Decision.DENY;
        }
        authDecision.authorizationContext = "Policy matched: " + entry.getKey();
        return authDecision;
      }
    }

    if (this.action == RBAC.Action.ALLOW) {
      authDecision.decision = AuthorizationDecision.Decision.DENY;
    } else {
      authDecision.decision = AuthorizationDecision.Decision.ALLOW;
    }
    authDecision.authorizationContext = "No policies matched.";
    return authDecision;
  }

  private Map<String, CheckedExpr> parseConditions(Map<String, Expr> conditions) {
    Env env = envSetup();
    Map<String, CheckedExpr> parsedConditions = new HashMap<>();
    for (Map.Entry<String, Expr> entry : conditions.entrySet()) {
      parsedConditions.put(entry.getKey(), exprToCheckedExpr(entry.getValue(), env));
    }
    return parsedConditions;
  }

  /**
   * Determines if a condition matches the given EvaluateArgs using Cel library.
   * @param <ReqT> Request type.
   * @param <RespT> Response type.
   * @param conditions RBAC conditions.
   * @param args EvaluateArgs.
   * @return if a condition matches the EvaluateArgs.
   * @throws InterpreterException if something goes wrong.
   */
  private boolean matches(CheckedExpr parsedCondition, EvaluateArgs<ReqT, RespT> args) 
    throws InterpreterException {
    ImmutableMap<String, Object> apiAttributes = extractFields(args);
    Object result = celEvaluate(parsedCondition, apiAttributes);

    if (result instanceof Boolean) {
      return Boolean.parseBoolean(result.toString());
    }

    return false;
  }

  /**
   * Helper function for cel evaluate.
   * @param <ReqT> Request type.
   * @param <RespT> Response type.
   * @param conditions RBAC conditions.
   * @param apiAttributes Envoy attributes map.
   * @return the evaluate result.
   * @throws InterpreterException if something goes wrong.
   */
  private Object celEvaluate(
      CheckedExpr parsedCondition, ImmutableMap<String, Object> apiAttributes) 
      throws InterpreterException {
    List<Descriptor> descriptors = new ArrayList<>();
    RuntimeTypeProvider messageProvider = DescriptorMessageProvider.dynamicMessages(descriptors);
    Dispatcher dispatcher = DefaultDispatcher.create();
    Interpreter interpreter = new DefaultInterpreter(messageProvider, dispatcher);

    Activation activation = Activation.copyOf(apiAttributes);
    Object result = interpreter.createInterpretable(parsedCondition).eval(activation);
    return result;
  }

  /** 
   * Set up environment needed to convert Expr to CheckedExpr.
   * @return setted environment
   */
  private Env envSetup() {
    // Define used Type.
    final Type stringType = Type.newBuilder()
        .setPrimitive(PrimitiveType.STRING)
        .build();
    final Type intType = Type.newBuilder()
        .setPrimitive(PrimitiveType.INT64)
        .build();
    final Type unknownType = Type.newBuilder()
        .setWellKnown(WellKnownType.WELL_KNOWN_TYPE_UNSPECIFIED)
        .build();
    final Type stringMapType = Type.newBuilder()
        .setMapType(
            MapType.newBuilder()
                .setKeyType(stringType)
                .setValueType(unknownType))
        .build();

    Errors errors = new Errors("source_location", null);
    TypeProvider typeProvider = new DescriptorTypeProvider();
    Env env = Env.standard(errors, typeProvider);

    env.add("requestUrlPath", stringType);
    env.add("requestHost", stringType);
    env.add("requestMethod", stringType);
    env.add("requestHeaders", stringMapType);
    env.add("sourceAddress", stringType);
    env.add("sourcePort", intType);
    env.add("destinationAddress", stringType);
    env.add("destinationPort", intType);
    env.add("connectionRequestedServerName", stringType);
    env.add("onnectionUriSanPeerCertificate", stringType);

    if (errors.getErrorCount() > 0) {
      throw new RuntimeException(errors.getAllErrorsAsString());
    }
    
    return env;
  }

  /**
   * Convert conditions from Expr type into CheckedExpr type.
   * @param conditions RBAC conditions.
   * @return a CheckedExpr converted from Expr.
   */
  private CheckedExpr exprToCheckedExpr(Expr conditions, Env env) {
    ParsedExpr parsedConditions = ParsedExpr.newBuilder()
        .setExpr(conditions)
        .setSourceInfo(SourceInfo.newBuilder().build())
        .build();

    CheckedExpr checkedResult = ExprChecker.check(env, "", parsedConditions);

    return checkedResult;
  }

  /**
   * Extract Envoy Attributes from EvaluateArgs.
   * @param args evaluateArgs.
   * @return an immuatable map contains String and the corresponding object.
   */
  private ImmutableMap<String, Object> extractFields(
      EvaluateArgs<ReqT, RespT> args) {
    if (args == null) {
      return null;
    }

    Map<String, Object> attributes = new HashMap<>();
    setRequestHost(args, attributes);
    setRequestMethod(args, attributes);
    setRequestHeaders(args, attributes);
    setSourceAddress(args, attributes);
    setDestinationAddress(args, attributes);
    setConnectionRequestedServerName(args, attributes);
    
    return ImmutableMap.copyOf(attributes);
  }

  // TBD
  // private void setRequestUrlPath(
  //    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {}

  private void setRequestHost(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String requestHost = args.getCall().getMethodDescriptor().getServiceName();
    if (requestHost == null || requestHost.length() == 0) {
      return;
    }
    attributes.put("requestHost", requestHost);
  }

  private void setRequestMethod(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String requestMethod = args.getCall().getMethodDescriptor().getFullMethodName();
    if (requestMethod == null || requestMethod.length() == 0) {
      return;
    }
    attributes.put("requestMethod", requestMethod);
  }

  private void setRequestHeaders(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    Metadata requestHeaders = args.getHeaders();
    if (requestHeaders == null) {
      return;
    }
    attributes.put("requestHeaders", requestHeaders);
  }

  private void setSourceAddress(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String sourceAddress = args.getCall().getAttributes()
        .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
    if (sourceAddress == null || sourceAddress.length() == 0) {
      return;
    }
    attributes.put("sourceAddress", sourceAddress);
  }

  // TBD
  // private void setSourcePort(
  //    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {}

  private void setDestinationAddress(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String destinationAddress = args.getCall().getAttributes()
        .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR).toString();
    if (destinationAddress == null || destinationAddress.length() == 0) {
      return;
    }
    attributes.put("destinationAddress", destinationAddress);
  }

  // TBD
  // private void setDestinationPort(
  //    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {}

  private void setConnectionRequestedServerName(
      EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String connectionRequestedServerName = args.getCall().getAuthority();
    if (connectionRequestedServerName == null || connectionRequestedServerName.length() == 0) {
      return;
    }
    attributes.put("connectionRequestedServerName", connectionRequestedServerName);
  }

  // TBD
  // private void setConnectionUriSanPeerCertificate(
  //    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {}
}
