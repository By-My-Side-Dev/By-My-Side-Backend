package com.gyeote.gyeote_api.global.storage.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBlobStorageConfig {

    @Bean
    public BlobServiceClient blobServiceClient(
        @Value("${azure.storage.account-name}") String accountName
    ) {
        return new BlobServiceClientBuilder()
                .endpoint(
                        "https://"
                                + accountName
                                + ".blob.core.windows.net"
                )
                .credential(
                        new DefaultAzureCredentialBuilder().build()
                )
                .buildClient();
    }

    @Bean
    public BlobContainerClient blobContainerClient(
        BlobServiceClient blobServiceClient,
        @Value("${azure.storage.container-name}") String containerName
    ) {
        return blobServiceClient.getBlobContainerClient(containerName);
    }
}
