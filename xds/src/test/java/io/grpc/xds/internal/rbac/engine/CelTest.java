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

package io.grpc.xds.internal.rbac.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.envoyproxy.envoy.config.rbac.v2.RBAC.Action;
import io.grpc.xds.internal.rbac.engine.cel.Activation;
import io.grpc.xds.internal.rbac.engine.cel.InterpreterException;
import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for Cel Evaluation Engine. */
@RunWith(JUnit4.class)
public class CelTest<ReqT, RespT> {
  private CelEvaluationEngine<ReqT,RespT> engine;
  private CelEvaluationEngine<ReqT,RespT> spyEngine;

  private Expr condition1;
  private Expr condition2;
  private Expr condition3;
  private Expr condition4;
  private Expr condition5;
  private Expr condition6;

  private Policy policy1;
  private Policy policy2;
  private Policy policy3;
  private Policy policy4;
  private Policy policy5;
  private Policy policy6;
  
  private RBAC rbacAllow;
  private RBAC rbacDeny;

  @Mock
  private Map<String, Object> attributes;

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock
  private EvaluateArgs<ReqT,RespT> args;

  @Mock
  private Activation activation;

  @Before
  public void setup() throws InterpreterException {
    condition1 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 1").build())
        .build();
    condition2 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 2").build())
        .build();
    condition3 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 3").build())
        .build();
    condition4 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 4").build())
        .build();
    condition5 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 5").build())
        .build();
    condition6 = Expr.newBuilder()
        .setIdentExpr(Ident.newBuilder().setName("Condition 6").build())
        .build();

    policy1 = Policy.newBuilder().setCondition(condition1).build();
    policy2 = Policy.newBuilder().setCondition(condition2).build();
    policy3 = Policy.newBuilder().setCondition(condition3).build();
    policy4 = Policy.newBuilder().setCondition(condition4).build();
    policy5 = Policy.newBuilder().setCondition(condition5).build();
    policy6 = Policy.newBuilder().setCondition(condition6).build();
        
    rbacAllow = RBAC.newBuilder()
        .setAction(Action.ALLOW)
        .putPolicies("Policy 1", policy1)
        .putPolicies("Policy 2", policy2)
        .putPolicies("Policy 3", policy3)
        .build();
    rbacDeny = RBAC.newBuilder()
        .setAction(Action.DENY)
        .putPolicies("Policy 4", policy4)
        .putPolicies("Policy 5", policy5)
        .putPolicies("Policy 6", policy6)
        .build();

    List<RBAC> rbacList = new ArrayList<>(Arrays.asList(new RBAC[] {rbacAllow}));
    engine = new CelEvaluationEngine<>(ImmutableList.copyOf(rbacList));
    spyEngine = Mockito.spy(engine);
    doReturn(ImmutableMap.copyOf(attributes)).when(spyEngine).extractFields(
        ArgumentMatchers.<EvaluateArgs<ReqT,RespT>>any());
    doReturn(false).when(spyEngine).matches(eq(condition1), any(Activation.class));
    doReturn(true).when(spyEngine).matches(eq(condition2), any(Activation.class));
    doReturn(true).when(spyEngine).matches(eq(condition3), any(Activation.class));
  }

  @Test
  public void testEvaluate() throws InterpreterException {
    setup();
    assertEquals(spyEngine.evaluate(args).getDecision(), AuthorizationDecision.Decision.ALLOW);
    assertEquals(spyEngine.evaluate(args).getMatchingPolicyNames().size(), 2);
    assertTrue(spyEngine.evaluate(args).getMatchingPolicyNames().contains("Policy 2"));
    assertTrue(spyEngine.evaluate(args).getMatchingPolicyNames().contains("Policy 3"));
  }
}
