package br.com.concretesolutions.requestmatcher;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.fail;

/**
 * Abstract rule for wrapping a {@link MockWebServer} with more features.
 * <br />
 * This sets up a mock web server and several helpers for enqueuing responses.
 * These can buffer assertions for the requests it receives.
 * <br />
 * It is an abstract type because unit tests and instrumented tests have different
 * paths to search for fixtures.
 *
 * @see MockWebServer
 * @see UnitTestRequestMatcherRule
 * @see InstrumentedTestRequestMatcherRule
 * @see RequestMatcher
 */
public abstract class RequestMatcherRule implements TestRule {

    private static final String ASSERT_HEADER = "REQUEST-ASSERT", ERROR_MESSAGE = "Failed assertion for %s";

    private final Map<String, RequestMatcher> requestAssertions = new HashMap<>();
    private final MockWebServer server;

    private RequestAssertionError assertionError;

    public RequestMatcherRule() {
        this(new MockWebServer());
    }

    public RequestMatcherRule(MockWebServer server) {
        this.server = server;
    }

    protected abstract InputStream open(String path) throws IOException;

    @Override
    public Statement apply(Statement base, Description description) {
        return requestAssertionStatement(base);
    }

    /**
     * Enqueue a {@link MockResponse} and create a {@link RequestMatcher} associated with it
     *
     * @param response The expected response
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueue(MockResponse response) {
        final RequestMatcher requestMatcher = new RequestMatcher();
        final String assertPath = response.hashCode() + "_::_" + requestMatcher.hashCode();
        server.enqueue(response.setHeader(ASSERT_HEADER, assertPath));
        // Only enqueue request if everything else passed. An exception thrown here would
        // make the request count be different.
        requestAssertions.put(assertPath, requestMatcher);
        return requestMatcher;
    }

    /**
     * Helper for enqueuing a disconnect response.
     *
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueueDisconnect() {
        return enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    }

    /**
     * Helper method to enqueue a mock response without body.
     *
     * @param statusCode status code of response
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueueNoBody(int statusCode) {
        return enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setBody(""));
    }

    /**
     * Helper method to enqueue a mock response.
     * Uses {@link IOReader#read(InputStream)} (String)} to read fixtures.
     *
     * @param statusCode status code of response
     * @param assetPath  Path inside the "fixtures" folder in androidTest/assets or test/resources
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueue(int statusCode, String assetPath) {
        return enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setBody(readFixture(assetPath)));
    }

    /**
     * Helper method to enqueue a mock response without body for a GET request without body.
     *
     * @param statusCode status code of response
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueueGET(int statusCode) {
        return enqueueNoBody(statusCode).assertMethodIs(RequestMatcher.GET);
    }

    /**
     * Helper method to enqueue a mock response for a GET request without body.
     * Uses {@link IOReader#read(InputStream)} (String)} to read fixtures.
     *
     * @param statusCode status code of response
     * @return A matcher for request assertions.
     */
    public RequestMatcher enqueueGET(int statusCode, String assetPath) {
        return enqueue(statusCode, assetPath).assertNoBody().assertMethodIs(RequestMatcher.GET);
    }

    public RequestMatcher enqueuePOST(int statusCode) {
        return enqueueNoBody(statusCode).assertMethodIs(RequestMatcher.POST);
    }

    public RequestMatcher enqueuePOST(int statusCode, String assetPath) {
        return enqueue(statusCode, assetPath).assertMethodIs(RequestMatcher.POST);
    }

    public RequestMatcher enqueuePUT(int statusCode) {
        return enqueueNoBody(statusCode).assertMethodIs(RequestMatcher.PUT);
    }

    public RequestMatcher enqueuePUT(int statusCode, String assetPath) {
        return enqueue(statusCode, assetPath).assertMethodIs(RequestMatcher.PUT);
    }

    public HttpUrl url(String path) {
        return server.url(path);
    }

    public MockWebServer getMockWebServer() {
        return server;
    }

    private void before() {
        this.server.setDispatcher(new QueueDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {

                final MockResponse response = super.dispatch(request);

                final RequestMatcher matcher = requestAssertions.get(response.getHeaders().get("REQUEST-ASSERT"));

                if (matcher != null)
                    try {
                        matcher.doAssert(request);
                    } catch (AssertionError e) {
                        final String message = String.format(ERROR_MESSAGE, request);
                        RequestMatcherRule.this.assertionError = new RequestAssertionError(message, e);
                        return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_END);
                    }

                return response;
            }
        });
    }

    private void after(boolean success) {

        if (!success)
            return;

        final int requestQueueSize = requestAssertions.size();
        final int requestCount = server.getRequestCount();

        try {
            if (assertionError != null)
                throw assertionError;

            if (requestQueueSize != requestCount) {
                try {
                    fail("Enqueued " + requestQueueSize + " requests but used " + requestCount + " requests.");
                } catch (AssertionError e) {
                    throw new RequestAssertionError("Failed assertion.", e);
                }
            }

        } finally {
            assertionError = null;
            requestAssertions.clear();
        }
    }

    private Statement requestAssertionStatement(final Statement base) {

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                before();
                boolean success = false;
                try {
                    base.evaluate();
                    success = true;
                } finally {
                    after(success);
                }
            }
        };
    }

    private String readFixture(final String assetPath) {
        try {
            return IOReader.read(open("fixtures/" + assetPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read asset with path " + assetPath, e);
        }
    }
}
