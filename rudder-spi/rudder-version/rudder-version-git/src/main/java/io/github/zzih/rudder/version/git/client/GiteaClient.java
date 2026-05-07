/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.version.git.client;

import io.github.zzih.rudder.version.git.GiteaProperties;
import io.github.zzih.rudder.version.git.model.GiteaCommit;
import io.github.zzih.rudder.version.git.model.GiteaFileContent;
import io.github.zzih.rudder.version.git.model.GiteaFileResponse;
import io.github.zzih.rudder.version.git.model.GiteaRepo;
import io.github.zzih.rudder.version.git.model.GiteaTag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gitea REST 客户端。每份 Gitea provider 配置对应一个实例。
 * <p>无状态（仅持有 URL + token），org 和 repo 按调用传入 —— 每个工作空间对应一个 org。
 */
@Slf4j
@RequiredArgsConstructor
public class GiteaClient {

    private final GiteaProperties properties;
    private final ObjectMapper objectMapper;

    /** 全局 request 超时，防止 Gitea 无响应时 pin 住 Server 的 @Scheduled 线程。 */
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(30);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    // ==================== Org Management ====================

    /** Check if the given organization exists. */
    public boolean orgExists(String org) {
        String url = buildUrl("orgs", org);
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        return response.statusCode() == 200;
    }

    /** Create the organization if it does not exist. */
    public void ensureOrgExists(String org) {
        if (orgExists(org)) {
            return;
        }
        String url = buildUrl("orgs");
        Map<String, Object> body = Map.of(
                "username", org,
                "full_name", org,
                "visibility", "private");
        HttpRequest request = postRequest(url, body);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 409 || response.statusCode() == 422) {
            log.debug("Gitea org already exists: {}", org);
            return;
        }
        checkResponse(response, 201);
        log.info("Created Gitea organization: {}", org);
    }

    // ==================== Repo Management ====================

    /** Create a repository under the given organization. Idempotent — returns existing repo on 409. */
    public GiteaRepo createRepo(String org, String repoName, String description) {
        String url = buildUrl("orgs", org, "repos");
        Map<String, Object> body = Map.of(
                "name", repoName,
                "description", description,
                "auto_init", true);
        HttpRequest request = postRequest(url, body);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 409) {
            log.debug("Gitea repo already exists: {}/{}", org, repoName);
            return getRepo(org, repoName);
        }
        checkResponse(response, 201);
        return parseJson(response.body(), GiteaRepo.class);
    }

    /** Delete a repository. */
    public void deleteRepo(String org, String repoName) {
        String url = buildUrl("repos", org, repoName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "token " + properties.getToken())
                .DELETE()
                .build();
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 204);
    }

    /** Get repository info. Returns null if not found (404). */
    public GiteaRepo getRepo(String org, String repoName) {
        String url = buildUrl("repos", org, repoName);
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 404) {
            return null;
        }
        checkResponse(response, 200);
        return parseJson(response.body(), GiteaRepo.class);
    }

    /** Check if a repository exists. */
    public boolean repoExists(String org, String repoName) {
        return getRepo(org, repoName) != null;
    }

    // ==================== File Operations ====================

    /**
     * Get file content at a specific ref (branch name or commit SHA).
     * Returns null if not found (404).
     */
    public GiteaFileContent getFileContent(String org, String repo, String path, String ref) {
        String url = buildUrl("repos", org, repo, "contents", path);
        if (ref != null && !ref.isBlank()) {
            url += "?ref=" + ref;
        }
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 404) {
            return null;
        }
        checkResponse(response, 200);
        return parseJson(response.body(), GiteaFileContent.class);
    }

    /**
     * 列出目录下的文件。返回 GiteaFileContent 列表（type="file" 或 "dir"）。
     * 目录不存在返回空列表。
     */
    public List<GiteaFileContent> listDirectory(String org, String repo, String dirPath, String ref) {
        String url = buildUrl("repos", org, repo, "contents", dirPath);
        if (ref != null && !ref.isBlank()) {
            url += "?ref=" + ref;
        }
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 404) {
            return List.of();
        }
        checkResponse(response, 200);
        try {
            return objectMapper.readValue(response.body(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, GiteaFileContent.class));
        } catch (Exception e) {
            log.warn("Failed to parse directory listing for {}/{}/{}", org, repo, dirPath);
            return List.of();
        }
    }

    /**
     * Create or update a file. Each call results in a git commit.
     * Gitea API: POST = create new file, PUT = update existing file (requires sha).
     */
    public GiteaFileResponse createOrUpdateFile(String org, String repo, String path,
                                                String content, String commitMessage) {
        String url = buildUrl("repos", org, repo, "contents", path);
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = new HashMap<>();
        body.put("content", base64Content);
        body.put("message", commitMessage);

        GiteaFileContent existing = getFileContent(org, repo, path, null);
        HttpRequest request;
        if (existing != null) {
            body.put("sha", existing.getSha());
            request = putRequest(url, body);
        } else {
            request = postRequest(url, body);
        }

        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200, 201);
        return parseJson(response.body(), GiteaFileResponse.class);
    }

    /**
     * 多文件单次提交（Gitea 1.20+ API）。一次 API 调用 = 一个 Git commit。
     * POST /repos/{org}/{repo}/contents
     *
     * @return commit SHA
     */
    public String commitMultipleFiles(String org, String repo, String commitMessage,
                                      List<io.github.zzih.rudder.version.git.model.FileChange> files) {
        String url = buildUrl("repos", org, repo, "contents");

        List<Map<String, Object>> fileEntries = new ArrayList<>();
        for (var fc : files) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("operation", fc.getOperation());
            entry.put("path", fc.getPath());
            if (fc.getContent() != null) {
                entry.put("content", Base64.getEncoder().encodeToString(
                        fc.getContent().getBytes(StandardCharsets.UTF_8)));
            }
            if (fc.getSha() != null) {
                entry.put("sha", fc.getSha());
            }
            fileEntries.add(entry);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("files", fileEntries);

        HttpRequest request = postRequest(url, body);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 201);

        try {
            var root = objectMapper.readTree(response.body());
            if (root.has("commit") && root.get("commit").has("sha")) {
                return root.get("commit").get("sha").asText();
            }
            if (root.has("sha")) {
                return root.get("sha").asText();
            }
            throw new RuntimeException("Failed to extract commit SHA from Gitea multi-file response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse commit SHA from multi-file response", e);
        }
    }

    /** Delete a file. Requires the current file SHA. */
    public void deleteFile(String org, String repo, String path, String commitMessage) {
        String url = buildUrl("repos", org, repo, "contents", path);

        GiteaFileContent existing = getFileContent(org, repo, path, null);
        if (existing == null) {
            log.warn("File not found for deletion: {}/{}/{}", org, repo, path);
            return;
        }

        Map<String, Object> body = Map.of(
                "message", commitMessage,
                "sha", existing.getSha());
        HttpRequest request = deleteRequestWithBody(url, body);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200);
    }

    // ==================== Version History ====================

    /** List commits for a file path with pagination. */
    public List<GiteaCommit> listCommits(String org, String repo, String filePath, int page, int limit) {
        String url = buildUrl("repos", org, repo, "git", "commits")
                + "?path=" + filePath + "&page=" + page + "&limit=" + limit;
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200);
        return parseJsonList(response.body(), GiteaCommit.class);
    }

    /** Get a single commit by SHA. */
    public GiteaCommit getCommit(String org, String repo, String sha) {
        String url = buildUrl("repos", org, repo, "git", "commits", sha);
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200);
        return parseJson(response.body(), GiteaCommit.class);
    }

    // ==================== Tags (for publishing) ====================

    /** Create a tag on the repository. */
    public GiteaTag createTag(String org, String repo, String tagName, String message, String target) {
        String url = buildUrl("repos", org, repo, "tags");
        Map<String, Object> body = Map.of(
                "tag_name", tagName,
                "message", message,
                "target", target);
        HttpRequest request = postRequest(url, body);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 201);
        return parseJson(response.body(), GiteaTag.class);
    }

    /** List all tags for a repository. */
    public List<GiteaTag> listTags(String org, String repo) {
        String url = buildUrl("repos", org, repo, "tags");
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200);
        return parseJsonList(response.body(), GiteaTag.class);
    }

    /** Get a single tag by name. Returns null if not found (404). */
    public GiteaTag getTag(String org, String repo, String tagName) {
        String url = buildUrl("repos", org, repo, "tags", tagName);
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() == 404) {
            return null;
        }
        checkResponse(response, 200);
        return parseJson(response.body(), GiteaTag.class);
    }

    // ==================== Diff ====================

    /** Get the raw diff for a commit. */
    public String getCommitDiff(String org, String repo, String sha) {
        String url = buildUrl("repos", org, repo, "git", "commits", sha) + ".diff";
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = sendRequest(request);
        checkResponse(response, 200);
        return response.body();
    }

    // ==================== Private Helpers ====================

    private HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            log.debug("Gitea API request: {} {}", request.method(), request.uri());
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Gitea API response: {} {}", response.statusCode(), request.uri());
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Gitea API request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gitea API request interrupted: " + request.uri(), e);
        }
    }

    private String buildUrl(String... pathSegments) {
        String base = properties.getUrl().replaceAll("/+$", "");
        return base + "/api/v1/" + String.join("/", pathSegments);
    }

    private HttpRequest getRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "token " + properties.getToken())
                .header("Content-Type", "application/json")
                .GET()
                .build();
    }

    private HttpRequest postRequest(String url, Map<String, Object> body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "token " + properties.getToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    private HttpRequest putRequest(String url, Map<String, Object> body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "token " + properties.getToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    private HttpRequest deleteRequestWithBody(String url, Map<String, Object> body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "token " + properties.getToken())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    private void checkResponse(HttpResponse<String> response, int... expectedCodes) {
        int statusCode = response.statusCode();
        for (int expected : expectedCodes) {
            if (statusCode == expected) {
                return;
            }
        }
        log.error("Gitea API error: status={} body={}", statusCode, response.body());
        throw new RuntimeException("Gitea API call failed with status " + statusCode);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Gitea API response", e);
        }
    }

    private <T> List<T> parseJsonList(String json, Class<T> elementType) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Gitea API response list", e);
        }
    }
}
