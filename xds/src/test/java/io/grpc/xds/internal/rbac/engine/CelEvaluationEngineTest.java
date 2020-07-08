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

import com.google.api.expr.v1alpha1.Expr;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.grpc.xds.InterpreterException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for Cel Evaluation Engine. */
@RunWith(JUnit4.class)
public class CelEvaluationEngineTest<ReqT, RespT> {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyRbacList() {
    List<RBAC> rbacList = new ArrayList<>();
    CelEvaluationEngine<ReqT,RespT> engine = new CelEvaluationEngine<>(rbacList);
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid RBAC list size, must provide either one RBAC or two RBACs. ");
  }

  // @Test
  // public void testSingleRbac() {
  //   RBAC rbac = RBAC.newBuilder().build();
  //   List<RBAC> rbacList = new ArrayList<>(rbac);
  //   try {
  //     CelEvaluationEngine engine = new CelEvaluationEngine(rbacList);
  //   } catch (IllegalArgumentException e) {
  //     assertThat(e).isNull();
  //   }
  // }

  // @Test
  // public void testRbacPairWithCorrectActions() {
  //   RBAC rbac1 = RBAC.newBuilder().build();
  //   RBAC rbac2 = RBAC.newBuilder().build();
  // }
  
  // @Test
  // public void testRbacPairWithWrongActions() {
  //   RBAC rbac1 = RBAC.newBuilder().build();
  //   RBAC rbac2 = RBAC.newBuilder().build();
  // }

  // @Test
  // public void testInvalidRbacList() throws IllegalArgumentException {

  // }
}
