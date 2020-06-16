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
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

class AuthorizationDecision {
    enum Decision {
        ALLOW,
        DENY,
        UNKNOWN,
    }

    private Decision decision;
    private String authorizationContext;
}

Class that holds attribute context
class EvaluateArgs {
    final Metadata headers;
    final ServerCall<ReqT, RespT> call;
}

class CelEvaluationEngine {
    enum Action {
        ALLOW,
        DENY
    }
    class Condition {
        private Expr expr;

        public Condition(Expr expr) {
            this.expr = expr;
        }

        // returns whether or not args matches this expr
        public boolean matches(EvaluateArgs args) {
            return true;
        }
    }

    Action action;
  	Map<String, Condition> conditions;

    public CelEvaluationEngine(@Nullable RBAC rbac) {
        this.action = rbac.getAction() == RBAC.Action.ALLOW ? Action.ALLOW : Action.DENY;
        this.conditions = new HashMap<>();
        for(Map.Entry<String, Policy> entry: rbac.getPolicies().entrySet()) {
            this.conditions.put(entry.getKey(), new Condition(entry.getValue().getCondition()));
        }
    }

    public AuthorizationDecision evaluate(EvaluateArgs args) {
        AuthorizationDecision authDecision = new AuthorizationDecision();
        for (Map.Entry<String, Condition> entry : this.conditions.entrySet()) {
            if (entry.getValue().matches(args)) {
                authDecision.decision = rbac.getAction() == Action.ALLOW ? Decision.ALLOW : Decision.DENY;
                authDecision.authorizationContext = "policy matched: " + entry.getKey();
                return authDecision;
            }
        }

        authDecision.decision = rbac.getAction() == Action.ALLOW ? Decision.DENY : Decision.ALLOW;
        authDecision.authorizationContext = "no policies matched";
        return authDecision;
    }
}