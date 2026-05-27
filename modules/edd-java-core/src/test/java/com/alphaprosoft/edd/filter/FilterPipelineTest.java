package com.alphaprosoft.edd.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alphaprosoft.edd.core.RequestMeta;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterPipelineTest {

  @Test
  void filtersRunInOrderAroundTheTerminal() {
    List<String> trace = new ArrayList<>();
    Filter a =
        (req, chain) -> {
          trace.add("a-before");
          Object r = chain.proceed(req);
          trace.add("a-after");
          return r;
        };
    Filter b =
        (req, chain) -> {
          trace.add("b-before");
          Object r = chain.proceed(req);
          trace.add("b-after");
          return r;
        };
    FilterChain terminal =
        req -> {
          trace.add("dispatch");
          return "ok";
        };

    Object result =
        new FilterPipeline(List.of(a, b), terminal)
            .run(new EddRequest(null, RequestMeta.newRequest()));

    assertEquals("ok", result);
    assertEquals(List.of("a-before", "b-before", "dispatch", "b-after", "a-after"), trace);
  }

  @Test
  void filterCanTransformBodyBeforeDispatch() {
    Filter decode = (req, chain) -> chain.proceed(req.body("decoded:" + req.body()));
    FilterChain terminal = req -> req.body();

    Object result =
        new FilterPipeline(List.of(decode), terminal)
            .run(new EddRequest("raw", RequestMeta.newRequest()));

    assertEquals("decoded:raw", result);
  }

  @Test
  void filterCanShortCircuit() {
    Filter cache = (req, chain) -> "cached"; // never proceeds
    FilterChain terminal =
        req -> {
          throw new AssertionError("terminal must not run when a filter short-circuits");
        };

    Object result =
        new FilterPipeline(List.of(cache), terminal)
            .run(new EddRequest("x", RequestMeta.newRequest()));

    assertEquals("cached", result);
  }
}
