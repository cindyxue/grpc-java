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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import io.grpc.xds.InterpreterException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DefaultInterpreter}. */
@RunWith(JUnit4.class)
public class CelInterfaceTest {
  private RuntimeTypeProvider messageProvider;
  private Dispatcher dispatcher;
  private Interpreter interpreter;
  private Env env;
  private CheckedExpr checkedResult;
  private Activation activation;
  private Object result;

  @Test
  public void setup() throws InterpreterException {
    List<Descriptor> descriptors = new ArrayList<>();
    messageProvider = DescriptorMessageProvider.dynamicMessages(descriptors);
    dispatcher = DefaultDispatcher.create();
    interpreter = new DefaultInterpreter(messageProvider, dispatcher);

    Errors errors = new Errors("source_location", null);
    TypeProvider typeProvider = new DescriptorTypeProvider();
    env = Env.standard(errors, typeProvider);
    env.add("requestUrlPath", Type.newBuilder().build());
    env.add("requestHost", Type.newBuilder().build());
    env.add("requestMethod", Type.newBuilder().build());

    Expr conditions = Expr.newBuilder().build();
    ParsedExpr parsedConditions = ParsedExpr.newBuilder()
        .setExpr(conditions)
        .setSourceInfo(SourceInfo.newBuilder().build())
        .build();
    checkedResult = ExprChecker.check(env, "", parsedConditions);

    Map<String, Object> map = new HashMap<>();
    map.put("requestUrlPath", new Object());
    map.put("requestHost", new Object());
    map.put("requestMethod", new Object());
    map.put("requestHeaders", new Object());
    map.put("sourceAddress", new Object());
    map.put("sourcePort", new Object());
    map.put("destinationAddress", new Object());
    ImmutableMap<String, Object> apiAttributes = ImmutableMap.copyOf(map);

    activation = Activation.copyOf(apiAttributes);
    result = interpreter.createInterpretable(checkedResult).eval(activation);
  }

  @Test
  public void testCelInterface() {
    try {
      setup();
    } catch (InterpreterException e) {
      System.out.println(e.toString());
    }
    
    assertThat(messageProvider).isNotNull();
    assertThat(dispatcher).isNotNull();
    assertThat(interpreter).isNotNull();
    assertThat(env).isNotNull();
    assertThat(checkedResult).isNotNull();
    assertThat(activation).isNotNull();
    assertThat(result).isNotNull();
  }
}