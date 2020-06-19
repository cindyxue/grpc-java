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

package io.grpc;

import io.envoyproxy.envoy.config.rbac.v2.RBAC;
import io.envoyproxy.envoy.config.rbac.v2.Policy;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import com.google.api.expr.v1alpha1.Expr;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

// A prototype for CelEngine in gRPC-Java

class AuthorizationDecision {
    enum Decision {
        ALLOW,
        DENY,
        UNKNOWN,
    }

    Decision decision;
    String authorizationContext;
}

// Class that holds attribute context
class EvaluateArgs {
    Metadata headers;
    ServerCall call;
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
            // Todo
            return true;
        }
    }

    Action action;
    Map<String, Condition> conditions;

    // Builds a CEL evaluation engine from Envoy RBAC.
    public CelEvaluationEngine(@Nullable RBAC rbac) {
        this.action = rbac.getAction() == RBAC.Action.ALLOW ? Action.ALLOW : Action.DENY;
        this.conditions = new HashMap<>();
        for(Map.Entry<String, Policy> entry: rbac.getPolicies().entrySet()) {
            this.conditions.put(entry.getKey(), new Condition(entry.getValue().getCondition()));
        }
    }

    // Builds a CEL evaluation engine from runtime policy template.
    // public CelEvaluationEngine(RTPolicyTemplate rt_policy) {
    //     // TBD
    // }

    // Evaluates Envoy Attributes and returns an authorization decision.
    public AuthorizationDecision evaluate(EvaluateArgs args) {
        AuthorizationDecision authDecision = new AuthorizationDecision();
        for (Map.Entry<String, Condition> entry : this.conditions.entrySet()) {
            if (entry.getValue().matches(args)) {
                authDecision.decision = this.action == Action.ALLOW ? 
                    AuthorizationDecision.Decision.ALLOW : AuthorizationDecision.Decision.DENY;
                authDecision.authorizationContext = "policy matched: " + entry.getKey();
                return authDecision;
            }
        }

        authDecision.decision = AuthorizationDecision.Decision.UNKNOWN;
        authDecision.authorizationContext = "Unable to decide based on given information - no policies matched";
        return authDecision;
    }

    public void extractFields(EvaluateArgs args) {
        String requestUrlPath = "";
        String requestHost = "";
        String requestMethod = "";
        Map<String, ?> requestHeaders = new HashMap<>();
        String sourceAddress = "";
        int sourcePort = 0;
        String destinationAddress = "";
        int destinationPort = 0;
        Metadata metadata = new Metadata();
        String connectionRequestedServerName = "";
        String connectionUriSanPeerCertificate = "";

        // metadata = args.headers.get(Metadata.Key<T> key);
        // requestUrlPath = metadata.getHost();
    }
}