package br.com.concretesolutions.requestmatcher;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.concretesolutions.requestmatcher.assertion.BodyAssertion;
import br.com.concretesolutions.requestmatcher.assertion.RequestAssertion;
import br.com.concretesolutions.requestmatcher.model.Query;
import okhttp3.Headers;
import okhttp3.mockwebserver.RecordedRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * Main fluent interface object for gathering assertions to requests.
 *
 * @see RequestMatcherRule
 */
public final class RequestMatcher {

    public static final String GET = "GET", POST = "POST", DELETE = "DELETE", PUT = "PUT";

    private Set<Query> expectedQueries;
    private Map<String, String> expectedHeaders;
    private RequestAssertion requestAssert;
    private BodyAssertion bodyAssertion;
    private String expectedPath;
    private String expectedMethod;
    private boolean expectNoBody;

    /**
     * Add a {@link RequestAssertion} to this matcher.
     *
     * @param requestAssert The assertion to make on request arrived
     * @return "this"
     */
    public RequestMatcher assertRequest(RequestAssertion requestAssert) {
        this.requestAssert = requestAssert;
        return this;
    }

    /**
     * Add a path assertion to this matcher.
     *
     * @param expectedPath The assertion to make on request arrived
     * @return "this"
     */
    public RequestMatcher assertPathIs(String expectedPath) {
        this.expectedPath = expectedPath;
        return this;
    }

    /**
     * Add a {@link BodyAssertion} to this matcher.
     *
     * @param bodyAssertion The assertion to make on request arrived
     * @return "this"
     */
    public RequestMatcher assertBody(BodyAssertion bodyAssertion) {

        if (expectNoBody)
            throw new IllegalArgumentException("Cannot assertBody and assertNoBody together");

        this.bodyAssertion = bodyAssertion;
        return this;
    }

    /**
     * Add a "no body" assertion to this matcher.
     *
     * @return "this"
     */
    public RequestMatcher assertNoBody() {

        if (bodyAssertion != null)
            throw new IllegalArgumentException("Cannot assertBody and assertNoBody together");

        this.expectNoBody = true;
        return this;
    }

    /**
     * Add a query exists assertion
     *
     * @param key   The query key to be asserted
     * @param value The query value to be asserted
     * @return "this"
     */
    public RequestMatcher assertHasQuery(String key, String value) {

        if (expectedQueries == null)
            expectedQueries = new HashSet<>();

        expectedQueries.add(Query.of(key, value));
        return this;
    }

    /**
     * Add a header exists assertion
     *
     * @param key   The header key to be asserted
     * @param value The header value to be asserted
     * @return "this"
     */
    public RequestMatcher assertHasHeader(String key, String value) {

        if (expectedHeaders == null)
            expectedHeaders = new HashMap<>();

        expectedHeaders.put(key, value);
        return this;
    }

    /**
     * Add a request method assertion
     *
     * @param method The method expected
     * @return "this"
     */
    public RequestMatcher assertMethodIs(String method) {
        this.expectedMethod = method;
        return this;
    }

    /**
     * Call to action method. This fires all assertions buffered in to
     * the given request.
     *
     * @param request The assertions target
     */
    public void doAssert(RecordedRequest request) {

        if (requestAssert != null)
            requestAssert.doAssert(request);

        if (expectedMethod != null)
            assertThat(request.getMethod(), is(expectedMethod));

        final String path = RequestUtils.getPathOnly(request);

        if (expectedPath != null)
            assertThat(path, is(expectedPath));

        queryAssertions(request.getPath());
        headerAssertions(request.getHeaders());
        bodyAssertions(request);
    }

    private void headerAssertions(Headers headers) {

        if (expectedHeaders == null || expectedHeaders.isEmpty())
            return;

        if (headers.size() == 0)
            fail("Expected headers but found none");

        final Map<String, List<String>> headersMultiMap = headers.toMultimap();
        final Map<String, List<String>> expectedHeadersMultiMap = Headers.of(expectedHeaders).toMultimap();

        for (Map.Entry<String, List<String>> entry : expectedHeadersMultiMap.entrySet())
            assertThat(headersMultiMap, hasEntry(entry.getKey(), entry.getValue()));
    }

    private void queryAssertions(String path) {

        if (expectedQueries == null || expectedQueries.isEmpty())
            return;

        if (!RequestUtils.hasQuery(path))
            fail("Expected query strings but found none");


        final Set<Query> allQueries = RequestUtils.buildQueries(path);

        for (Query query : expectedQueries)
            assertThat(allQueries, hasItem(query));
    }

    private void bodyAssertions(RecordedRequest request) {
        final String body = RequestUtils.getBody(request);

        if (expectNoBody && !"".equals(body))
            fail("Expected no body but received one");

        if (bodyAssertion != null) {

            if ("".equals(body))
                fail("Expected body but found none");

            bodyAssertion.doAssert(body);
        }
    }

    // Ensure we call Object.hashCode for instance detection
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
