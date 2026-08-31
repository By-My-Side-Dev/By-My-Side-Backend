package com.gyeote.gyeote_api.global.storage.azure;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.gyeote.gyeote_api.global.storage.ObjectUploadRequest;
import com.gyeote.gyeote_api.global.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class AzureBlobObjectStorage implements ObjectStorage {

    private static final Duration MAX_USER_DELEGATION_TTL = Duration.ofDays(7);

    private final BlobServiceClient blobServiceClient;
    private final BlobContainerClient blobContainerClient;

    @Override
    public void upload(ObjectUploadRequest request) {
        BlobClient blobClient = blobContainerClient.getBlobClient(request.objectKey());
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(request.contentType());
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(request.contentStream())
                .setHeaders(headers);

        blobClient.uploadWithResponse(options, null, Context.NONE);
    }

    @Override
    public void delete(String objectKey) {
        blobContainerClient.getBlobClient(objectKey).deleteIfExists();
    }

    @Override
    public URI getUrl(String objectKey, Duration ttl) {
        validateTtl(ttl);

        BlobClient blobClient = blobContainerClient.getBlobClient(objectKey);
        OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime expiryTime = OffsetDateTime.now().plus(ttl);

        UserDelegationKey delegationKey = blobServiceClient.getUserDelegationKey(
                startTime,
                expiryTime
        );

        BlobSasPermission permission = new BlobSasPermission()
                .setReadPermission(true);
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(
                expiryTime,
                permission
        ).setStartTime(startTime);

        String sasToken = blobClient.generateUserDelegationSas(
                sasValues,
                delegationKey
        );

        return URI.create(blobClient.getBlobUrl() + "?" + sasToken);
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("SAS URL TTL must be positive.");
        }
        if (ttl.compareTo(MAX_USER_DELEGATION_TTL) > 0) {
            throw new IllegalArgumentException("SAS URL TTL must not exceed 7 days.");
        }
    }
}
