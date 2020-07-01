package io.grpc.xds.internal;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import io.grpc.Metadata;
import io.grpc.xds.InterpreterException;
import java.lang.RuntimeException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
  public static void main(String[] args) throws InterpreterException {
    List<Descriptor> descriptors = new ArrayList<>();
    RuntimeTypeProvider messageProvider = DescriptorMessageProvider.dynamicMessages(descriptors);
    Dispatcher dispatcher = DefaultDispatcher.create();
    Interpreter interpreter = new DefaultInterpreter(messageProvider, dispatcher);

    Errors errors = new Errors("source_location", null);
    TypeProvider typeProvider = new DescriptorTypeProvider();
    Env env = Env.standard(errors, typeProvider);
    env.add("requestUrlPath", Type.newBuilder().build());
    env.add("requestHost", Type.newBuilder().build());
    env.add("requestMethod", Type.newBuilder().build());

    Expr conditions = Expr.newBuilder().build();
    ParsedExpr parsedConditions = ParsedExpr.newBuilder()
      .setExpr(conditions)
      .setSourceInfo(SourceInfo.newBuilder().build())
      .build();
    CheckedExpr checkedResult = ExprChecker.check(env, "", parsedConditions);

    if (errors.getErrorCount() > 0) {
      throw new RuntimeException(errors.getAllErrorsAsString());
    }    

    Map<String, Object> map = new HashMap<>();
    map.put("requestUrlPath", new Object());
    map.put("requestHost", new Object());
    map.put("requestMethod", new Object());
    ImmutableMap<String, Object> apiAttributes = ImmutableMap.copyOf(map);

    Activation activation = Activation.copyOf(apiAttributes);
    Object result = interpreter.createInterpretable(checkedResult).eval(activation);
  }
}
