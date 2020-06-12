/*
 * Copyright 2019 The gRPC Authors
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

package io.grpc.xds;

import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.envoyproxy.envoy.config.rbac.v2.Permission;
import io.envoyproxy.envoy.config.rbac.v2.Principal;

import io.envoyproxy.envoy.type.matcher.MetadataMatcher;
import io.envoyproxy.envoy.type.matcher.DoubleMatcher;
import io.envoyproxy.envoy.type.matcher.PathMatcher;
import io.envoyproxy.envoy.type.matcher.ValueMatcher;
import io.envoyproxy.envoy.type.matcher.ListMatcher;

import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.SourcePosition;

import javax.annotation.Nullable;

class XdsRBAC {
    private RBAC rbac;

    public XdsRBAC(@Nullable RBAC rbac) {
        
        Permission.Builder permissionBuilder = Permission.newBuilder();
        Permission.Set.Builder permissionSetBuilder = Permission.Set.newBuilder();

        permissionSetBuilder.addRules(permissionBuilder.build());
        permissionBuilder.setAndRules(permissionSetBuilder.build());
        permissionBuilder.setAny(false);
        // permissionBuilder.setHeader("");


        Principal.Builder principalBuilder = Principal.newBuilder();
        Principal.Authenticated.Builder authBuilder = Principal.Authenticated.newBuilder();

        principalBuilder.setAuthenticated(authBuilder.build());
        principalBuilder.setAny(false);
        principalBuilder.setNotId(principalBuilder.build());

        
        Policy.Builder policyBuilder = Policy.newBuilder();
        Expr.Builder exprBuilder = Expr.newBuilder();
        
        policyBuilder.addPermissions(permissionBuilder.build());
        policyBuilder.addPrincipals(principalBuilder.build());
        policyBuilder.setCondition(exprBuilder.build());


        RBAC.Builder rbacBuilder = RBAC.newBuilder();

        rbacBuilder.setAction(RBAC.Action.ALLOW);
        rbacBuilder.putPolicies("", policyBuilder.build());

        this.rbac = rbacBuilder.build();
    }

    @Nullable
    RBAC getRbac() {
        return rbac;
    }
}