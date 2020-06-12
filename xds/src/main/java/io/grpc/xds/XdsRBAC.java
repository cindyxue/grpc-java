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
import com.google.api.expr.v1alpha1.Expr;
// import com.github.udpa.udpa.service.orca.v1.OrcaLoadReportRequest;


import javax.annotation.Nullable;

class XdsRBAC {
    private RBAC rbac;

    public XdsRBAC(@Nullable RBAC rbac) {
        RBAC.Builder builder = RBAC.newBuilder()
            .setAction(RBAC.Action.ALLOW)
            .putPolicies("", 
                Policy.newBuilder()
                    .addPermissions(Permission.newBuilder().build())
                    .addPrincipals(Principal.newBuilder().build())
                    // .setCondition(Expr.newBuilder.build())
                    .build());

        this.rbac = builder.build();
    }

    @Nullable
    RBAC getRbac() {
        return rbac;
    }
}