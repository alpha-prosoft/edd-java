package com.example.sample;

import com.alphaprosoft.edd.aws.EddLambda;
import com.alphaprosoft.edd.aws.LambdaRuntime;

/**
 * AWS Lambda runtime. Handler: {@code com.example.sample.SampleLambda::handleRequest}. Built by
 * {@code mvn package} into {@code target/sample-lambda.jar}; ingests API Gateway requests.
 */
public final class SampleLambda extends EddLambda {

  @Override
  protected LambdaRuntime configure() {
    return LambdaRuntime.builder(SampleApp.build()).api().build();
  }
}
