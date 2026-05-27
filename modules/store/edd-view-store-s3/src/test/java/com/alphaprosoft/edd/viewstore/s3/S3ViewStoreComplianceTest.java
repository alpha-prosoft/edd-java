package com.alphaprosoft.edd.viewstore.s3;

import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.store.compliance.ViewStoreCompliance;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/** Runs the shared {@link ViewStoreCompliance} suite against real S3 (dev01). */
class S3ViewStoreComplianceTest extends ViewStoreCompliance {

  private static S3Client s3;
  private static String bucket;
  private static S3ViewStore store;

  @BeforeAll
  static void connect() {
    Region region = Region.of(System.getenv().getOrDefault("AWS_REGION", "eu-west-1"));
    bucket = System.getenv().getOrDefault("EDD_S3_BUCKET", "edd-java-viewstore-446466402394");
    s3 = S3Client.builder().region(region).build();
    try {
      s3.listBuckets();
    } catch (Exception e) {
      throw new IllegalStateException(
          "S3 not reachable — this compliance suite must run against live S3 (dev01). "
              + "Authenticate (aws login) and bridge credentials before building; the suite must "
              + "not be skipped.",
          e);
    }
    ensureBucket(s3, bucket, region);
    store = S3ViewStore.builder().client(s3).bucket(bucket).build();
  }

  private static void ensureBucket(S3Client s3, String bucket, Region region) {
    try {
      s3.headBucket(b -> b.bucket(bucket));
    } catch (NoSuchBucketException missing) {
      s3.createBucket(
          b ->
              b.bucket(bucket)
                  .createBucketConfiguration(
                      c -> c.locationConstraint(BucketLocationConstraint.fromValue(region.id()))));
      s3.waiter().waitUntilBucketExists(b -> b.bucket(bucket));
    }
  }

  @Override
  protected ViewStore newStore() {
    return store;
  }

  @Override
  protected ViewStore newStore(String service) {
    return S3ViewStore.builder().client(s3).bucket(bucket).service(service).build();
  }
}
