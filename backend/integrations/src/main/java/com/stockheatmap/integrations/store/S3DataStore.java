package com.stockheatmap.integrations.store;

import com.stockheatmap.core.model.DataKey;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

/** JSON objects in the private data bucket. */
public class S3DataStore implements DataStore {

  private final S3Client s3;
  private final String bucket;
  private final ObjectMapper mapper;

  public S3DataStore(S3Client s3, String bucket, ObjectMapper mapper) {
    this.s3 = s3;
    this.bucket = bucket;
    this.mapper = mapper;
  }

  @Override
  public <T> Optional<T> get(DataKey key, Class<T> type) {
    try {
      byte[] json = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key.key()).build()).asByteArray();
      return Optional.of(mapper.readValue(json, type));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public void put(DataKey key, Object value) {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key.key()).contentType("application/json").build(),
        RequestBody.fromBytes(mapper.writeValueAsBytes(value)));
  }
}
