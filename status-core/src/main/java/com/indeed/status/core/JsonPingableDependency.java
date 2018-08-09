package com.indeed.status.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.indeed.status.core.CheckStatus;
import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.Urgency;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * This class expands on the idea of a pingable dependency by assuming that the
 * class to be pinged will respond with the standard JSON representation. When
 * queried, it makes an http request to the configured url, parsese the response,
 * and throws if it is in the status MAJOR or OUTAGE, or returns a non-2xx status
 * code.
 */
@ThreadSafe
public class JsonPingableDependency extends PingableDependency {
    @Nonnull private final URI uri;
    @Nonnull private final int httpConnectionTimeoutMillis;
    @Nonnull private final int httpSocketTimeoutMillis;
    @Nonnull private final Urgency urgency;
    @Nonnull private final String id;
    @Nonnull private final String documentationUrl;
    @Nonnull private final Charset charset;
    @Nonnull private final JsonFactory jsonFactory = new JsonFactory();

    /**
     * Private, as we prefer customers to use the builder rather than a constructor.
     */
    private JsonPingableDependency(@Nonnull final Builder builder) {
        super(builder);

        final String id = builder.getId();
        Preconditions.checkState(!Strings.isNullOrEmpty(id), "Cannot build a dependency with an empty ID");
        this.id = id;

        final String documentationUrl = builder.getDocumentationUrl();
        Preconditions.checkState(!Strings.isNullOrEmpty(documentationUrl), "Cannot build a dependency with an empty documentationUrl");
        this.documentationUrl = documentationUrl;

        final String description = builder.getDescription();
        Preconditions.checkState(!Strings.isNullOrEmpty(description), "Cannot build a dependency with an empty description");

        final Urgency urgency = builder.getUrgency();
        this.urgency = Preconditions.checkNotNull(urgency, "Cannot build a dependency without an urgency");

        final Charset charset = builder.getCharset();
        this.charset = Preconditions.checkNotNull(charset, "Cannot build a JsonPingableDependency without a charset");

        this.uri = Preconditions.checkNotNull(builder.getUri(), "Cannot build a dependency with no URI");
        this.httpConnectionTimeoutMillis = builder.getHttpConnectionTimeoutMillis();
        this.httpSocketTimeoutMillis = builder.getHttpSocketTimeoutMillis();

    }

    /**
     * Cloned from the PingableService interface, this is a convenience wrapper for the usual pattern of creating
     * dependency checkers that return nothing and throw an exception on any error.
     *
     * @throws Exception If any piece of the dependency check fails.
     */
    public final void ping() throws Exception {
        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(httpConnectionTimeoutMillis)
                    .setConnectTimeout(httpConnectionTimeoutMillis)
                    .setSocketTimeout(httpSocketTimeoutMillis)
                    .build();

            final HttpGet get = new HttpGet(this.uri);
            get.setConfig(requestConfig);
            final CloseableHttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new Exception(this.uri + " did not return a 200 status code");
            }
            HttpEntity entity = response.getEntity();
            JsonParser parser = jsonFactory.createParser(new InputStreamReader(entity.getContent(), charset));
            DependencyStatus status = getDependencyStatus(parser);
            if (status.getCondition().isWorseThan(CheckStatus.MINOR)) {
                throw new Exception("dependency " + this.id + " is in status " + status.getCondition().name());
            }
        }
    }

    // dbw 2018-5-21 This is required to support improperly-encoded JSON (ISO-8859-1).  If we can drop support in the
    // future (i.e. if all teams at Indeed adopt unicode encoding), we should remove this method and use a Jackson
    // ObjectMapper, which would also let us drop the charset from the builder.
    private final DependencyStatus getDependencyStatus(JsonParser jsonParser) throws IOException {
        final DependencyStatus dependencyStatus = new DependencyStatus();
        while(jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String name = jsonParser.getCurrentName();

            // dbw 2018-5-21 I'm not sure why the json parser returns null so often, but when it does the switch throws
            // an NPE, so skip it -- its not gonna match any of the keys anyway.
            if (null == name) {
                continue;
            }

            switch (name) {
                case "appname":
                    jsonParser.nextToken();
                    dependencyStatus.setAppname(jsonParser.getText());
                    break;
                case "condition":
                    jsonParser.nextToken();
                    dependencyStatus.setCondition(CheckStatus.infer(jsonParser.getText()));
                    break;
                case "dcStatus":
                    jsonParser.nextToken();
                    dependencyStatus.setDcStatus(CheckStatus.infer(jsonParser.getText()));
                    break;
                case "duration":
                    jsonParser.nextToken();
                    dependencyStatus.setDuration(jsonParser.getLongValue());
                    break;
                case "hostname":
                    jsonParser.nextToken();
                    dependencyStatus.setHostname(jsonParser.getText());
                    break;
                case "leastRecentlyExecutedDate":
                    jsonParser.nextToken();
                    dependencyStatus.setLeastRecentlyExecutedDate(jsonParser.getText());
                    break;
                case "leastRecentlyExecutedTimestamp":
                    jsonParser.nextToken();
                    dependencyStatus.setLeastRecentlyExecutedTimestamp(jsonParser.getLongValue());
                    break;
                default:
                    // dbw 2018-5-21 Ignore other tokens on the response that are not on the model object.
                    jsonParser.nextToken();
                    break;
            }
        }

        return dependencyStatus;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Simple concrete extension of the pingable dependency builder to add
     * options specific to this {@link PingableDependency}.
     */
    public static class Builder extends PingableDependency.Builder<JsonPingableDependency, Builder> {
        @Nullable URI uri;
        private int httpSocketTimeoutMillis = 30000;
        private int httpConnectionTimeoutMillis = 30000;
        private Charset charset = Charset.forName("UTF-8");

        @Nullable
        public URI getUri() {
            return uri;
        }

        @Nonnull
        public Builder setUri(@Nonnull final URI uri) {
            this.uri = uri;
            return this;
        }

        @Nonnull
        public Builder setUri(@Nonnull final String uri) throws URISyntaxException {
            return setUri(new URI(uri));
        }

        public JsonPingableDependency build() {
            return new JsonPingableDependency(this);
        }

        public int getHttpSocketTimeoutMillis() {
            return httpSocketTimeoutMillis;
        }

        @Nonnull
        public Builder setHttpSocketTimeoutMillis(int httpSocketTimeoutMillis) {
            this.httpSocketTimeoutMillis = httpSocketTimeoutMillis;
            return this;
        }

        public int getHttpConnectionTimeoutMillis() {
            return httpConnectionTimeoutMillis;
        }

        @Nonnull
        public Builder setHttpConnectionTimeoutMillis(int httpConnectionTimeoutMillis) {
            this.httpConnectionTimeoutMillis = httpConnectionTimeoutMillis;
            return this;
        }

        /* dbw 2018-5-21 Indeed serves invalid JSON (JSON encoded with ISO-8859-1)
         * for its healthchecks, so we need a way to be able to set that invalid
         * encoding manually.  Note that this means that we can't rely on Jackson's
         * automatic encoding detecting, so users that have valid encodings other
         * than UTF-8, like UTF-16 or UTF-32 also have to set their encoding
         * manually.  The JSON specification allows us to assume UTF-8.
         */
        public Builder setCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder setCharset(String charset) {
            this.charset = Charset.forName(charset);
            return this;
        }

        public Charset getCharset() {
            return this.charset;
        }
    }
}
