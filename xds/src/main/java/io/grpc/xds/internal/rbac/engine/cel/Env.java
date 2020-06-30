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

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Decl.IdentDecl;
import com.google.api.tools.expr.features.ExprFeatures;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Environment used during checking of expressions.
 * Provides name resolution and error reporting.
 */
public class Env {
  /** Type provider responsible for resolving CEL message references to strong types. */
  private final TypeProvider typeProvider;

  /**
   * Stack of declaration groups where each entry in stack represents a scope capable of hinding
   * declarations lower in the stack.
   */
  private final Stack<DeclGroup> decls = new Stack<>();

  /** Object used for error reporting. */
  private final Errors errors;

  /** CEL Feature flags. */
  private final ImmutableSet<ExprFeatures> exprFeatures;

  private Env(
      Errors errors,
      TypeProvider typeProvider,
      DeclGroup declGroup,
      ImmutableSet<ExprFeatures> exprFeatures) {
    this.exprFeatures = exprFeatures;
    this.errors = Preconditions.checkNotNull(errors);
    this.typeProvider = Preconditions.checkNotNull(typeProvider);
    this.decls.push(Preconditions.checkNotNull(declGroup));
  }

  /**
   * Adds a declaration to the environment, based on the Decl proto. Will report errors if the
   * declaration overlaps with an existing one, or clashes with a macro.
   */
  public Env add(Decl decl) {
    return this;
  }

  /** Adds simple name declaration to the environment for a non-function. */
  public Env add(String name, Type type) {
    return add(Decl.newBuilder().build());
  }

  /**
   * Creates an {@code Env} value configured with the standard types, functions, and operators,
   * configured with a custom {@code typeProvider}.
   *
   * <p>Note: standard declarations are configured in an isolated scope, and may be shadowed by
   * subsequent declarations with the same signature.
   */
  public static Env standard(
      Errors errors, TypeProvider typeProvider, ExprFeatures... exprFeatures) {
    return standard(errors, typeProvider, ImmutableSet.copyOf(exprFeatures));
  }

  /**
   * Creates an {@code Env} value configured with the standard types, functions, and operators,
   * configured with a custom {@code typeProvider} and a reference to the set of {@code
   * exprFeatures} enabled in the environment.
   *
   * <p>Note: standard declarations are configured in an isolated scope, and may be shadowed by
   * subsequent declarations with the same signature.
   */
  public static Env standard(
      Errors errors, TypeProvider typeProvider, ImmutableSet<ExprFeatures> exprFeatures) {
    return new Env(errors, typeProvider, new DeclGroup(), exprFeatures);
  }

  /** Object for managing a group of declarations within a scope. */
  public static class DeclGroup {
  }
}
