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

class Evaluator<ReqT, RespT> {
    /**
   * Determines if a condition matches the given EvaluateArgs using Cel library
   * @param <ReqT>
   * @param <RespT>
   * @param conditions
   * @param args
   * @return
   * @throws InterpreterException
   */
  protected static <ReqT, RespT> boolean matches(Expr conditions, EvaluateArgs<ReqT, RespT> args) 
    throws InterpreterException {
    CheckedExpr checkedResult = Evaluator.parseConditions(conditions);
    ImmutableMap<String, Object> apiAttributes = Evaluator.extractFields(args);
    Object result = Evaluator.celEvaluate(checkedResult, apiAttributes);

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
  protected static <ReqT, RespT> Object celEvaluate(CheckedExpr checkedResult, 
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
  public static CheckedExpr parseConditions(Expr conditions) {
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
  protected static <ReqT, RespT> ImmutableMap<String, Object> extractFields(
    EvaluateArgs<ReqT, RespT> args) {
    if(args == null) {
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

  // private static <ReqT, RespT> void setRequestUrlPath(EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
  //   String requestUrlPath = "";
  //   if(requestUrlPath == null || requestUrlPath.length() == 0) {
  //     return;
  //   }
  //   attributes.put("requestUrlPath", requestUrlPath);
  // }

  private static <ReqT, RespT> void setRequestHost(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String requestHost = args.getCall().getMethodDescriptor().getServiceName();
    if(requestHost == null || requestHost.length() == 0) {
      return;
    }
    attributes.put("requestHost", requestHost);
  }

  private static <ReqT, RespT> void setRequestMethod(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String requestMethod = args.getCall().getMethodDescriptor().getFullMethodName();
    if(requestMethod == null || requestMethod.length() == 0) {
      return;
    }
    attributes.put("requestMethod", requestMethod);
  }

  private static <ReqT, RespT> void setRequestHeaders(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    Metadata requestHeaders = args.getHeaders();
    if(requestHeaders == null) {
      return;
    }
    attributes.put("requestHeaders", requestHeaders);
  }

  private static <ReqT, RespT> void setSourceAddress(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String sourceAddress = args.getCall().getAttributes()
      .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
    if(sourceAddress == null || sourceAddress.length() == 0) {
      return;
    }
    attributes.put("sourceAddress", sourceAddress);
  }

  // private static <ReqT, RespT> void setSourcePort(EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
  //   Integer sourcePort = 0;
  //   if(sourcePort == null) {
  //     return;
  //   }
  //   attributes.put("sourcePort", sourcePort);
  // }

  private static <ReqT, RespT> void setDestinationAddress(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String destinationAddress = args.getCall().getAttributes()
      .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR).toString();
    if(destinationAddress == null || destinationAddress.length() == 0) {
      return;
    }
    attributes.put("destinationAddress", destinationAddress);
  }

  // private static <ReqT, RespT> void setDestinationPort(EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
  //   Integer destinationPort = 0;
  //   if(destinationPort == null) {
  //     return;
  //   }
  //   attributes.put("destinationPort", destinationPort);
  // }

  private static <ReqT, RespT> void setConnectionRequestedServerName(
    EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
    String connectionRequestedServerName = args.getCall().getAuthority();
    if(connectionRequestedServerName == null || connectionRequestedServerName.length() == 0) {
      return;
    }
    attributes.put("connectionRequestedServerName", connectionRequestedServerName);
  }

  // private static <ReqT, RespT> void setConnectionUriSanPeerCertificate(EvaluateArgs<ReqT, RespT> args, Map<String, Object> attributes) {
  //   String connectionUriSanPeerCertificate = "";
  //   if(connectionUriSanPeerCertificate == null || connectionUriSanPeerCertificate.length() == 0) {
  //     return;
  //   }
  //   attributes.put("connectionUriSanPeerCertificate", connectionUriSanPeerCertificate);
  // }
}